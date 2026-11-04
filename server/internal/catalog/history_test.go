package catalog

import (
	"context"
	"os"
	"path/filepath"
	"testing"
)

func TestLoadAntigravityHistory(t *testing.T) {
	tmp := t.TempDir()
	convID := "test-conv-123"
	logsDir := filepath.Join(tmp, ".gemini", "antigravity", "brain", convID, ".system_generated", "logs")
	if err := os.MkdirAll(logsDir, 0755); err != nil {
		t.Fatal(err)
	}

	sampleLines := `{"step_index":0,"source":"USER_EXPLICIT","type":"USER_INPUT","status":"DONE","created_at":"2026-10-31T11:56:21Z","content":"<USER_REQUEST>\nđọc trạng thái project\n</USER_REQUEST>"}
{"step_index":1,"source":"MODEL","type":"PLANNER_RESPONSE","status":"DONE","created_at":"2026-10-31T11:56:22Z","content":"Dạ đây là trạng thái hiện tại của dự án."}
{"step_index":2,"source":"USER_EXPLICIT","type":"USER_INPUT","status":"DONE","created_at":"2026-10-31T11:57:00Z","content":"tiếp tục đi"}
{"step_index":3,"source":"MODEL","type":"PLANNER_RESPONSE","status":"DONE","created_at":"2026-10-31T11:57:05Z","content":"Đã hoàn thành các bước."}
`
	if err := os.WriteFile(filepath.Join(logsDir, "transcript.jsonl"), []byte(sampleLines), 0644); err != nil {
		t.Fatal(err)
	}

	svc := NewService(tmp, NewAntigravity(tmp))
	msgs, err := svc.LoadHistory(context.Background(), EngineAntigravity, convID, 10)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(msgs) != 4 {
		t.Fatalf("expected 4 messages, got %d", len(msgs))
	}
	if msgs[0].Role != "user" || msgs[0].Content != "đọc trạng thái project" {
		t.Errorf("msg[0] mismatch: %+v", msgs[0])
	}
	if msgs[1].Role != "assistant" || msgs[1].Content != "Dạ đây là trạng thái hiện tại của dự án." {
		t.Errorf("msg[1] mismatch: %+v", msgs[1])
	}
	if msgs[2].Role != "user" || msgs[2].Content != "tiếp tục đi" {
		t.Errorf("msg[2] mismatch: %+v", msgs[2])
	}
	if msgs[3].Role != "assistant" || msgs[3].Content != "Đã hoàn thành các bước." {
		t.Errorf("msg[3] mismatch: %+v", msgs[3])
	}
}

func TestLoadAntigravityHistory_WithToolCalls(t *testing.T) {
	tmp := t.TempDir()
	convID := "test-conv-tools"
	logsDir := filepath.Join(tmp, ".gemini", "antigravity", "brain", convID, ".system_generated", "logs")
	if err := os.MkdirAll(logsDir, 0755); err != nil {
		t.Fatal(err)
	}

	sampleLines := `{"step_index":0,"type":"USER_INPUT","content":"<USER_REQUEST>run something</USER_REQUEST>"}
{"step_index":1,"type":"PLANNER_RESPONSE","content":null,"tool_calls":[{"name":"run_command","args":{"CommandLine":"ls -la"}}]}
{"step_index":2,"type":"PLANNER_RESPONSE","content":null,"tool_calls":[{"name":"read_file","args":{"Path":"/tmp/foo.txt"}}]}
`
	if err := os.WriteFile(filepath.Join(logsDir, "transcript.jsonl"), []byte(sampleLines), 0644); err != nil {
		t.Fatal(err)
	}
	svc := NewService(tmp, NewAntigravity(tmp))
	msgs, err := svc.LoadHistory(context.Background(), EngineAntigravity, convID, 10)
	if err != nil {
		t.Fatal(err)
	}
	if len(msgs) != 2 {
		t.Fatalf("expected 2 messages (1 user, 1 merged assistant tool execution), got %d", len(msgs))
	}
	if msgs[1].Role != "assistant" {
		t.Errorf("expected role assistant, got: %s", msgs[1].Role)
	}
}
