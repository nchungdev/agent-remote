package adapter

import (
	"bufio"
	"bytes"
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func newTestRun(rec *recorder) *cliRun {
	ctx, cancel := context.WithCancel(context.Background())
	return &cliRun{
		ctx:    ctx,
		cancel: cancel,
		send:   rec.emit,
		msgID:  "msg-test",
	}
}

func readFixtureLines(t *testing.T, relPath string) [][]byte {
	t.Helper()
	content, err := os.ReadFile(filepath.Join("..", "..", "testdata", "cli_fixtures", relPath))
	if err != nil {
		t.Fatalf("failed to read fixture %s: %v", relPath, err)
	}
	var lines [][]byte
	scanner := bufio.NewScanner(bytes.NewReader(content))
	for scanner.Scan() {
		line := bytes.TrimSpace(scanner.Bytes())
		if len(line) > 0 {
			buf := make([]byte, len(line))
			copy(buf, line)
			lines = append(lines, buf)
		}
	}
	return lines
}

func TestCodexFixtures(t *testing.T) {
	t.Run("turn_success", func(t *testing.T) {
		lines := readFixtureLines(t, "codex/turn_success.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		for _, l := range lines {
			handleCodexLine(run, l)
		}
		if !run.isFinished() {
			t.Fatalf("expected run to be finished")
		}
		chunks := rec.all("TEXT_CHUNK")
		if len(chunks) == 0 {
			t.Fatalf("expected TEXT_CHUNK event")
		}
		if text, ok := chunks[0]["text"].(string); !ok || !strings.Contains(text, "Xin chào") {
			t.Errorf("unexpected text chunk: %v", chunks[0])
		}
	})

	t.Run("tool_call", func(t *testing.T) {
		lines := readFixtureLines(t, "codex/tool_call.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		for _, l := range lines {
			handleCodexLine(run, l)
		}
		if len(rec.all("TOOL_CALL")) == 0 {
			t.Fatalf("expected TOOL_CALL event")
		}
		results := rec.all("TOOL_RESULT")
		if len(results) == 0 {
			t.Fatalf("expected TOOL_RESULT event")
		}
		if !strings.Contains(results[0]["output"].(string), "main.go") {
			t.Errorf("expected main.go in tool result, got: %v", results[0])
		}
	})

	t.Run("quota_exceeded", func(t *testing.T) {
		lines := readFixtureLines(t, "codex/quota_exceeded.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		for _, l := range lines {
			handleCodexLine(run, l)
		}
		errEvents := rec.all("ERROR")
		if len(errEvents) == 0 {
			t.Fatalf("expected ERROR event")
		}
		msg := errEvents[0]["message"].(string)
		if !strings.Contains(msg, "quota") {
			t.Errorf("expected quota in error message, got: %s", msg)
		}
	})

	t.Run("cancel", func(t *testing.T) {
		lines := readFixtureLines(t, "codex/cancel.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		for _, l := range lines {
			handleCodexLine(run, l)
		}
		errEvents := rec.all("ERROR")
		if len(errEvents) == 0 {
			t.Fatalf("expected ERROR event for cancel")
		}
	})
}

func TestAntigravityFixtures(t *testing.T) {
	t.Run("turn_success", func(t *testing.T) {
		lines := readFixtureLines(t, "antigravity/turn_success.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		activeCalls := make(map[int]string)
		var streamedText bool
		for _, l := range lines {
			handleAgyLine(run, l, activeCalls, &streamedText)
		}
		if len(rec.all("THINKING_CHUNK")) == 0 {
			t.Fatalf("expected THINKING_CHUNK")
		}
		if len(rec.all("TEXT_CHUNK")) == 0 {
			t.Fatalf("expected TEXT_CHUNK")
		}
	})

	t.Run("tool_call", func(t *testing.T) {
		lines := readFixtureLines(t, "antigravity/tool_call.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		activeCalls := make(map[int]string)
		var streamedText bool
		for _, l := range lines {
			handleAgyLine(run, l, activeCalls, &streamedText)
		}
		if len(rec.all("TOOL_CALL")) == 0 {
			t.Fatalf("expected TOOL_CALL")
		}
		if len(rec.all("TOOL_RESULT")) == 0 {
			t.Fatalf("expected TOOL_RESULT")
		}
	})

	t.Run("quota_exceeded", func(t *testing.T) {
		lines := readFixtureLines(t, "antigravity/quota_exceeded.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		activeCalls := make(map[int]string)
		var streamedText bool
		for _, l := range lines {
			handleAgyLine(run, l, activeCalls, &streamedText)
		}
		errEvents := rec.all("ERROR")
		if len(errEvents) == 0 {
			t.Fatalf("expected ERROR event")
		}
		msg := errEvents[0]["message"].(string)
		if !strings.Contains(msg, "Quota exceeded") {
			t.Errorf("expected Quota exceeded, got %s", msg)
		}
	})

	t.Run("cancel", func(t *testing.T) {
		lines := readFixtureLines(t, "antigravity/cancel.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		activeCalls := make(map[int]string)
		var streamedText bool
		for _, l := range lines {
			handleAgyLine(run, l, activeCalls, &streamedText)
		}
		errEvents := rec.all("ERROR")
		if len(errEvents) == 0 {
			t.Fatalf("expected ERROR event for cancel")
		}
	})
}

func TestClaudeFixtures(t *testing.T) {
	t.Run("turn_success", func(t *testing.T) {
		lines := readFixtureLines(t, "claude/turn_success.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		var streamedText bool
		for _, l := range lines {
			handleClaudeLine(run, l, &streamedText)
		}
		if len(rec.all("THINKING_CHUNK")) == 0 {
			t.Fatalf("expected THINKING_CHUNK")
		}
		if len(rec.all("TEXT_CHUNK")) == 0 {
			t.Fatalf("expected TEXT_CHUNK")
		}
	})

	t.Run("tool_call", func(t *testing.T) {
		lines := readFixtureLines(t, "claude/tool_call.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		var streamedText bool
		for _, l := range lines {
			handleClaudeLine(run, l, &streamedText)
		}
		if len(rec.all("TOOL_CALL")) == 0 {
			t.Fatalf("expected TOOL_CALL")
		}
		if len(rec.all("TOOL_RESULT")) == 0 {
			t.Fatalf("expected TOOL_RESULT")
		}
	})

	t.Run("quota_exceeded", func(t *testing.T) {
		lines := readFixtureLines(t, "claude/quota_exceeded.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		var streamedText bool
		for _, l := range lines {
			handleClaudeLine(run, l, &streamedText)
		}
		errEvents := rec.all("ERROR")
		if len(errEvents) == 0 {
			t.Fatalf("expected ERROR event")
		}
		msg := errEvents[0]["message"].(string)
		if !strings.Contains(msg, "Rate limit") {
			t.Errorf("expected Rate limit error, got %s", msg)
		}
	})

	t.Run("cancel", func(t *testing.T) {
		lines := readFixtureLines(t, "claude/cancel.jsonl")
		rec := &recorder{}
		run := newTestRun(rec)
		var streamedText bool
		for _, l := range lines {
			handleClaudeLine(run, l, &streamedText)
		}
		errEvents := rec.all("ERROR")
		if len(errEvents) == 0 {
			t.Fatalf("expected ERROR event for cancel")
		}
	})
}
