package adapter

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

type recorder struct {
	mu     sync.Mutex
	events []map[string]any
}

func (r *recorder) emit(raw []byte) {
	var event map[string]any
	_ = json.Unmarshal(raw, &event)
	r.mu.Lock()
	defer r.mu.Unlock()
	r.events = append(r.events, event)
}

func (r *recorder) all(eventType string) []map[string]any {
	r.mu.Lock()
	defer r.mu.Unlock()
	var out []map[string]any
	for _, e := range r.events {
		if e["type"] == eventType {
			out = append(out, e)
		}
	}
	return out
}

func (r *recorder) text() string {
	var b strings.Builder
	for _, e := range r.all("TEXT_CHUNK") {
		b.WriteString(e["text"].(string))
	}
	return b.String()
}

// fakeCLI writes a shell script and returns its path plus the file recording its argv.
func fakeCLI(t *testing.T, body string) (bin, argvFile string) {
	t.Helper()
	dir := t.TempDir()
	argvFile = filepath.Join(dir, "argv")
	bin = filepath.Join(dir, "cli")
	script := "#!/bin/sh\nfor a in \"$@\"; do printf '%s\\n' \"$a\" >> '" + argvFile + "'; done\n" + body + "\n"
	if err := os.WriteFile(bin, []byte(script), 0o755); err != nil {
		t.Fatal(err)
	}
	return bin, argvFile
}

func runWithin(t *testing.T, limit time.Duration, fn func()) {
	t.Helper()
	done := make(chan struct{})
	go func() { defer close(done); fn() }()
	select {
	case <-done:
	case <-time.After(limit):
		t.Fatalf("did not finish within %v", limit)
	}
}

func readArgv(t *testing.T, path string) string {
	t.Helper()
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return string(raw)
}

func codexWith(bin string) *CodexAdapter {
	return &CodexAdapter{binaryPath: func() string { return bin }}
}

const codexSuccess = `cat <<'EOF'
{"type":"thread.started","thread_id":"thread-1"}
{"type":"turn.started"}
{"type":"item.completed","item":{"id":"i0","type":"error","message":"model metadata warning"}}
{"type":"item.completed","item":{"id":"i1","type":"reasoning","text":"nghĩ"}}
{"type":"item.started","item":{"id":"i2","type":"command_execution","command":"echo hi","status":"in_progress"}}
{"type":"item.completed","item":{"id":"i2","type":"command_execution","command":"echo hi","aggregated_output":"hi\n","exit_code":0,"status":"completed"}}
{"type":"item.completed","item":{"id":"i3","type":"agent_message","text":"xin chào"}}
{"type":"turn.completed","usage":{}}
EOF`

func TestCodex_DoesNotHangWaitingForStdin(t *testing.T) {
	// Real `codex exec` reads stdin until EOF before starting the turn.
	bin, _ := fakeCLI(t, "cat >/dev/null\n"+codexSuccess)
	rec := &recorder{}
	runWithin(t, 5*time.Second, func() {
		codexWith(bin).HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit)
	})
	if rec.text() != "xin chào" {
		t.Fatalf("text = %q", rec.text())
	}
}

func TestCodex_MapsCurrentEventSchema(t *testing.T) {
	bin, _ := fakeCLI(t, codexSuccess)
	rec := &recorder{}
	codexWith(bin).HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit)

	if got := rec.all("THINKING_CHUNK"); len(got) != 1 || got[0]["thought"] != "nghĩ" {
		t.Fatalf("thinking = %v", got)
	}
	if got := rec.all("TOOL_CALL"); len(got) != 1 || got[0]["toolName"] != "shell" || got[0]["callId"] != "i2" {
		t.Fatalf("tool call = %v", got)
	}
	if got := rec.all("TOOL_RESULT"); len(got) != 1 || got[0]["output"] != "hi\n" || got[0]["isError"] != false {
		t.Fatalf("tool result = %v", got)
	}
	if got := rec.all("ERROR"); len(got) != 0 {
		t.Fatalf("warnings must not surface as errors: %v", got)
	}
	end := rec.all("MESSAGE_END")
	if len(end) != 1 || end[0]["conversationId"] != "thread-1" {
		t.Fatalf("message end = %v", end)
	}
}

func TestCodex_EndsMessageWithoutWaitingForProcessExit(t *testing.T) {
	bin, _ := fakeCLI(t, codexSuccess+"\nsleep 2")
	rec := &recorder{}
	done := make(chan struct{})
	go func() { defer close(done); codexWith(bin).HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit) }()

	deadline := time.Now().Add(1500 * time.Millisecond)
	for len(rec.all("MESSAGE_END")) == 0 {
		if time.Now().After(deadline) {
			t.Fatal("MESSAGE_END waited for the process to exit")
		}
		time.Sleep(10 * time.Millisecond)
	}
	<-done
	if n := len(rec.all("MESSAGE_END")); n != 1 {
		t.Fatalf("MESSAGE_END sent %d times", n)
	}
}

func TestCodex_SurfacesTurnFailureUnwrappingNestedJSON(t *testing.T) {
	bin, _ := fakeCLI(t, `cat <<'EOF'
{"type":"thread.started","thread_id":"t"}
{"type":"error","message":"retrying"}
{"type":"turn.failed","error":{"message":"{\"type\":\"error\",\"status\":400,\"error\":{\"message\":\"model not supported\"}}"}}
EOF
exit 1`)
	rec := &recorder{}
	codexWith(bin).HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit)

	errs := rec.all("ERROR")
	if len(errs) != 1 || errs[0]["message"] != "Codex lỗi: model not supported" {
		t.Fatalf("errors = %v", errs)
	}
	if len(rec.all("MESSAGE_END")) != 1 {
		t.Fatal("client spinner must stop after a failure")
	}
}

func TestCodex_SurfacesStderrOnStartupFailure(t *testing.T) {
	bin, _ := fakeCLI(t, `echo 'Reading additional input from stdin...' >&2
echo 'Not inside a trusted directory' >&2
exit 1`)
	rec := &recorder{}
	codexWith(bin).HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit)

	errs := rec.all("ERROR")
	if len(errs) != 1 || errs[0]["message"] != "Codex lỗi: Not inside a trusted directory" {
		t.Fatalf("errors = %v", errs)
	}
	if len(rec.all("MESSAGE_END")) != 1 {
		t.Fatal("missing MESSAGE_END")
	}
}

func TestPromptsAreNeverParsedAsFlags(t *testing.T) {
	const evil = "--dangerously-bypass-approvals-and-sandbox"
	cases := []struct {
		name string
		run  func(bin string, emit Emitter)
		want string
	}{
		{"codex", func(bin string, emit Emitter) {
			codexWith(bin).HandlePrompt(PromptOptions{Prompt: evil}, emit)
		}, "--\n" + evil + "\n"},
		{"claude", func(bin string, emit Emitter) {
			(&ClaudeCodeAdapter{binaryPath: func() string { return bin }}).HandlePrompt(PromptOptions{Prompt: evil}, emit)
		}, "--\n" + evil + "\n"},
		{"antigravity", func(bin string, emit Emitter) {
			(&AntigravityAdapter{binaryPath: func() string { return bin }}).HandlePrompt(PromptOptions{Prompt: evil}, emit)
		}, "--print=" + evil + "\n"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			bin, argv := fakeCLI(t, "exit 0")
			tc.run(bin, (&recorder{}).emit)
			if got := readArgv(t, argv); !strings.Contains(got, tc.want) {
				t.Fatalf("argv = %q, want it to contain %q", got, tc.want)
			}
		})
	}
}

func TestClientFlagValuesAreRejected(t *testing.T) {
	bin, argv := fakeCLI(t, "exit 0")
	for _, opts := range []PromptOptions{
		{Prompt: "hi", Model: "--dangerously-bypass-approvals-and-sandbox"},
		{Prompt: "hi", ConversationID: "--last"},
	} {
		rec := &recorder{}
		codexWith(bin).HandlePrompt(opts, rec.emit)
		if len(rec.all("ERROR")) != 1 || len(rec.all("MESSAGE_END")) != 1 {
			t.Fatalf("opts %+v: events = %v", opts, rec.events)
		}
	}
	if _, err := os.Stat(argv); err == nil {
		t.Fatal("CLI must not start with an unsafe flag value")
	}
}

func TestCodex_ResumeArgs(t *testing.T) {
	got := strings.Join(codexArgs(PromptOptions{Prompt: "p", Model: "m", ConversationID: "c"}), " ")
	if want := "exec resume --json --model m c -- p"; got != want {
		t.Fatalf("args = %q, want %q", got, want)
	}
}

func TestCancel_EndsMessageAndStopsChildProcesses(t *testing.T) {
	dir := t.TempDir()
	marker := filepath.Join(dir, "child-ran")
	bin, _ := fakeCLI(t, `(sleep 1; touch '`+marker+`') &
echo '{"type":"turn.started"}'
sleep 30`)
	adapter := codexWith(bin)
	rec := &recorder{}
	done := make(chan struct{})
	go func() { defer close(done); adapter.HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit) }()
	time.Sleep(200 * time.Millisecond)

	adapter.Cancel()
	runWithin(t, 5*time.Second, func() { <-done })
	if len(rec.all("MESSAGE_END")) != 1 || len(rec.all("ERROR")) != 0 {
		t.Fatalf("cancel events = %v", rec.events)
	}
	time.Sleep(1200 * time.Millisecond)
	if _, err := os.Stat(marker); err == nil {
		t.Fatal("child process survived cancel")
	}
}

func TestNewPrompt_SilencesThePreviousOne(t *testing.T) {
	slow, _ := fakeCLI(t, `echo '{"type":"turn.started"}'
sleep 30`)
	fast, _ := fakeCLI(t, codexSuccess)
	var bin atomic.Value
	bin.Store(slow)
	adapter := &CodexAdapter{binaryPath: func() string { return bin.Load().(string) }}

	oldRec, newRec := &recorder{}, &recorder{}
	done := make(chan struct{})
	go func() { defer close(done); adapter.HandlePrompt(PromptOptions{Prompt: "old"}, oldRec.emit) }()
	time.Sleep(200 * time.Millisecond)

	bin.Store(fast)
	adapter.HandlePrompt(PromptOptions{Prompt: "new"}, newRec.emit)
	runWithin(t, 5*time.Second, func() { <-done })

	if len(oldRec.all("MESSAGE_END")) != 0 {
		t.Fatal("superseded prompt must not end the new prompt's message")
	}
	if newRec.text() != "xin chào" || len(newRec.all("MESSAGE_END")) != 1 {
		t.Fatalf("new prompt events = %v", newRec.events)
	}
}

func TestLongOutputLineDoesNotHang(t *testing.T) {
	bin, _ := fakeCLI(t, `printf '{"type":"item.completed","item":{"id":"i","type":"agent_message","text":"'
head -c 3000000 /dev/zero | tr '\0' 'a'
printf '"}}\n{"type":"turn.completed"}\n'`)
	rec := &recorder{}
	runWithin(t, 10*time.Second, func() {
		codexWith(bin).HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit)
	})
	if len(rec.text()) != 3000000 {
		t.Fatalf("text length = %d", len(rec.text()))
	}
}

func TestClaude_StreamsDeltasAndReportsSession(t *testing.T) {
	bin, argv := fakeCLI(t, `cat <<'EOF'
{"type":"system","subtype":"init","session_id":"sess-1"}
{"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"nghĩ"}}}
{"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"xin "}}}
{"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"text_delta","text":"chào"}}}
{"type":"assistant","message":{"content":[{"type":"text","text":"xin chào"},{"type":"tool_use","id":"t1","name":"Bash","input":{"command":"ls"}}]}}
{"type":"user","message":{"content":[{"type":"tool_result","tool_use_id":"t1","content":[{"type":"text","text":"a.txt"}]}]}}
{"type":"result","subtype":"success","is_error":false,"result":"xin chào","session_id":"sess-1"}
EOF`)
	rec := &recorder{}
	adapter := &ClaudeCodeAdapter{binaryPath: func() string { return bin }, workspacePath: t.TempDir()}
	adapter.HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit)

	if rec.text() != "xin chào" {
		t.Fatalf("text must not be duplicated by the assistant event: %q", rec.text())
	}
	if len(rec.all("TEXT_CHUNK")) != 2 {
		t.Fatalf("expected streamed chunks, got %v", rec.all("TEXT_CHUNK"))
	}
	if got := rec.all("TOOL_RESULT"); len(got) != 1 || got[0]["output"] != "a.txt" {
		t.Fatalf("tool result = %v", got)
	}
	if end := rec.all("MESSAGE_END"); len(end) != 1 || end[0]["conversationId"] != "sess-1" {
		t.Fatalf("message end = %v", end)
	}
	if args := readArgv(t, argv); !strings.Contains(args, "stream-json") {
		t.Fatalf("argv = %q", args)
	}
}

func TestClaude_ReportsResultError(t *testing.T) {
	bin, _ := fakeCLI(t, `echo '{"type":"result","is_error":true,"result":"hết quota"}'; exit 1`)
	rec := &recorder{}
	(&ClaudeCodeAdapter{binaryPath: func() string { return bin }, workspacePath: t.TempDir()}).HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit)

	if errs := rec.all("ERROR"); len(errs) != 1 || errs[0]["message"] != "Claude Code lỗi: hết quota" {
		t.Fatalf("errors = %v", rec.all("ERROR"))
	}
}

func TestAntigravity_EndsOnResultAndReportsConversation(t *testing.T) {
	bin, _ := fakeCLI(t, `cat <<'EOF'
{"event":"init","conversation_id":"conv-1"}
{"event":"step_update","step_update":{"step_index":1,"step_type":"agent_response","text_delta":"ok"}}
{"event":"result","result":{"status":"SUCCESS","conversation_id":"conv-1"}}
EOF
sleep 2`)
	rec := &recorder{}
	done := make(chan struct{})
	go func() {
		defer close(done)
		(&AntigravityAdapter{binaryPath: func() string { return bin }}).HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit)
	}()
	deadline := time.Now().Add(1500 * time.Millisecond)
	for len(rec.all("MESSAGE_END")) == 0 {
		if time.Now().After(deadline) {
			t.Fatal("MESSAGE_END waited for process exit")
		}
		time.Sleep(10 * time.Millisecond)
	}
	<-done
	if end := rec.all("MESSAGE_END"); len(end) != 1 || end[0]["conversationId"] != "conv-1" {
		t.Fatalf("message end = %v", end)
	}
}

func TestMissingBinaryStopsTheClientSpinner(t *testing.T) {
	rec := &recorder{}
	codexWith("").HandlePrompt(PromptOptions{Prompt: "hi"}, rec.emit)
	if len(rec.all("ERROR")) != 1 || len(rec.all("MESSAGE_END")) != 1 {
		t.Fatalf("events = %v", rec.events)
	}
}
