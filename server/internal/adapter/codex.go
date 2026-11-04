package adapter

import (
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

type CodexAdapter struct {
	cliRunner
	binaryPath func() string
}

func NewCodexAdapter() *CodexAdapter {
	return &CodexAdapter{binaryPath: func() string { return findCLI("codex") }}
}

func (c *CodexAdapter) AgentType() protocol.AgentType {
	return protocol.AgentTypeCodex
}

func (c *CodexAdapter) Initialize() error {
	return nil
}

// codexEvent is one `codex exec --json` line.
type codexEvent struct {
	Type     string `json:"type"`
	ThreadID string `json:"thread_id"`
	Message  string `json:"message"`
	Error    *struct {
		Message string `json:"message"`
	} `json:"error"`
	Item *codexItem `json:"item"`
}

type codexItem struct {
	ID               string `json:"id"`
	Type             string `json:"type"`
	Text             string `json:"text"`
	Message          string `json:"message"`
	Command          string `json:"command"`
	AggregatedOutput string `json:"aggregated_output"`
	ExitCode         *int   `json:"exit_code"`
	Status           string `json:"status"`
	Server           string `json:"server"`
	Tool             string `json:"tool"`
	Query            string `json:"query"`
	Changes          []struct {
		Path string `json:"path"`
		Kind string `json:"kind"`
	} `json:"changes"`
}

func codexArgs(opts PromptOptions) []string {
	args := []string{"exec"}
	switch opts.PermissionMode {
	case "read-only":
		args = append(args, "-s", "read-only")
	case "workspace-write":
		args = append(args, "-s", "workspace-write")
	case "approve-for-me":
		args = append(args, "--approve-for-me")
	case "full-access":
		args = append(args, "--dangerously-bypass-approvals-and-sandbox")
	}
	if opts.ConversationID != "" {
		args = append(args, "resume", opts.ConversationID)
	}
	args = append(args, "--json")
	if opts.Model != "" {
		args = append(args, "--model", opts.Model)
	}
	return append(args, "--", opts.Prompt)
}

func (c *CodexAdapter) HandlePrompt(opts PromptOptions, emit Emitter) {
	bin := c.binaryPath()
	if bin == "" {
		c.begin(emit, fmt.Sprintf("codex_%d", time.Now().UnixMilli())).fail("Không tìm thấy lệnh 'codex' trên máy Mac.")
		return
	}
	if err := validateOptions(opts); err != nil {
		c.begin(emit, fmt.Sprintf("codex_%d", time.Now().UnixMilli())).fail(err.Error())
		return
	}

	run := c.begin(emit, fmt.Sprintf("codex_%d", time.Now().UnixMilli()))
	run.setConversationID(opts.ConversationID)
	run.emit(protocol.AgentStateEvent(protocol.AgentStateThinking, "Codex đang phân tích..."))
	run.execute("Codex", bin, codexArgs(opts), workingDir(opts.Workspace), func(line []byte) {
		handleCodexLine(run, line)
	})
}

func handleCodexLine(run *cliRun, line []byte) {
	var event codexEvent
	if err := json.Unmarshal(line, &event); err != nil {
		log.Printf("[Codex] Bỏ qua dòng không phải JSON: %s", line)
		return
	}
	switch event.Type {
	case "thread.started":
		run.setConversationID(event.ThreadID)
	case "item.started":
		if event.Item != nil {
			emitCodexToolStart(run, event.Item)
		}
	case "item.completed":
		if event.Item != nil {
			emitCodexItem(run, event.Item)
		}
	case "turn.completed":
		// The process lingers for seconds after this; the client should not wait on it.
		run.finish()
	case "turn.failed":
		run.fail(codexErrorText(event))
	}
}

func emitCodexItem(run *cliRun, item *codexItem) {
	switch item.Type {
	case "agent_message":
		run.emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
		run.emit(protocol.TextChunk(item.Text))
	case "reasoning":
		run.emit(protocol.ThinkingChunk(item.Text))
	case "error":
		log.Printf("[Codex] Cảnh báo: %s", item.Message) // Non-fatal: real failures arrive as turn.failed.
	case "command_execution", "file_change", "mcp_tool_call", "web_search":
		isError := item.Status == "failed" || (item.ExitCode != nil && *item.ExitCode != 0)
		run.emit(protocol.ToolResult(item.ID, codexToolName(item), item.AggregatedOutput, isError))
	}
}

func emitCodexToolStart(run *cliRun, item *codexItem) {
	var args map[string]any
	switch item.Type {
	case "command_execution":
		args = map[string]any{"command": item.Command}
	case "web_search":
		args = map[string]any{"query": item.Query}
	case "file_change":
		paths := make([]string, 0, len(item.Changes))
		for _, change := range item.Changes {
			paths = append(paths, change.Path)
		}
		args = map[string]any{"paths": paths}
	case "mcp_tool_call":
		args = map[string]any{}
	default:
		return
	}
	run.emit(protocol.AgentStateEvent(protocol.AgentStateExecutingTool, codexToolName(item)))
	run.emit(protocol.ToolCall(item.ID, codexToolName(item), args))
}

func codexToolName(item *codexItem) string {
	switch item.Type {
	case "command_execution":
		return "shell"
	case "mcp_tool_call":
		return item.Server + "." + item.Tool
	default:
		return item.Type
	}
}

// codexErrorText unwraps API failures, which nest a JSON document inside the message.
func codexErrorText(event codexEvent) string {
	text := event.Message
	if event.Error != nil && event.Error.Message != "" {
		text = event.Error.Message
	}
	var nested struct {
		Error struct {
			Message string `json:"message"`
		} `json:"error"`
	}
	if strings.HasPrefix(strings.TrimSpace(text), "{") && json.Unmarshal([]byte(text), &nested) == nil && nested.Error.Message != "" {
		text = nested.Error.Message
	}
	if text == "" {
		text = "lượt chạy thất bại"
	}
	return "Codex lỗi: " + text
}
