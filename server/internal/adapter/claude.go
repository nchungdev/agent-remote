package adapter

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

type ClaudeCodeAdapter struct {
	cliRunner
	workspacePath string
	binaryPath    func() string
}

func NewClaudeCodeAdapter(workspacePath string) *ClaudeCodeAdapter {
	if workspacePath == "" {
		workspacePath, _ = os.Getwd()
	}
	return &ClaudeCodeAdapter{
		workspacePath: workspacePath,
		binaryPath:    func() string { return findCLI("claude") },
	}
}

func (c *ClaudeCodeAdapter) AgentType() protocol.AgentType {
	return protocol.AgentTypeClaudeCode
}

func (c *ClaudeCodeAdapter) Initialize() error {
	if c.binaryPath() == "" {
		return errors.New("không tìm thấy lệnh 'claude'")
	}
	return nil
}

// claudeEvent is one `claude -p --output-format stream-json` line.
type claudeEvent struct {
	Type      string `json:"type"`
	SessionID string `json:"session_id"`
	IsError   bool   `json:"is_error"`
	Result    string `json:"result"`
	Event     *struct {
		Type  string `json:"type"`
		Delta struct {
			Type     string `json:"type"`
			Text     string `json:"text"`
			Thinking string `json:"thinking"`
		} `json:"delta"`
	} `json:"event"`
	Message *struct {
		Content []claudeContent `json:"content"`
	} `json:"message"`
}

type claudeContent struct {
	Type      string          `json:"type"`
	Text      string          `json:"text"`
	ID        string          `json:"id"`
	Name      string          `json:"name"`
	Input     map[string]any  `json:"input"`
	ToolUseID string          `json:"tool_use_id"`
	Content   json.RawMessage `json:"content"`
	IsError   bool            `json:"is_error"`
}

func claudeArgs(opts PromptOptions) []string {
	args := []string{"-p", "--output-format", "stream-json", "--verbose", "--include-partial-messages"}
	if opts.ConversationID != "" {
		args = append(args, "--resume", opts.ConversationID)
	}
	if opts.Model != "" {
		args = append(args, "--model", opts.Model)
	}
	switch opts.PermissionMode {
	case "bypassPermissions":
		args = append(args, "--dangerously-skip-permissions")
	case "plan", "default", "acceptEdits":
		args = append(args, "--permission-mode", opts.PermissionMode)
	}
	return append(args, "--", opts.Prompt)
}

func (c *ClaudeCodeAdapter) HandlePrompt(opts PromptOptions, emit Emitter) {
	bin := c.binaryPath()
	if bin == "" {
		c.begin(emit, fmt.Sprintf("claude_%d", time.Now().UnixMilli())).fail("Không tìm thấy lệnh 'claude' CLI trên máy Mac. Cài đặt: npm i -g @anthropic-ai/claude-code")
		return
	}
	if err := validateOptions(opts); err != nil {
		c.begin(emit, fmt.Sprintf("claude_%d", time.Now().UnixMilli())).fail(err.Error())
		return
	}
	workspace := opts.Workspace
	if workspace == "" {
		workspace = c.workspacePath
	}

	run := c.begin(emit, fmt.Sprintf("claude_%d", time.Now().UnixMilli()))
	run.setConversationID(opts.ConversationID)
	run.emit(protocol.AgentStateEvent(protocol.AgentStateThinking, ""))

	streamedText := false
	run.execute("Claude Code", bin, claudeArgs(opts), workingDir(workspace), func(line []byte) {
		handleClaudeLine(run, line, &streamedText)
	})
}

func handleClaudeLine(run *cliRun, line []byte, streamedText *bool) {
	var event claudeEvent
	if err := json.Unmarshal(line, &event); err != nil {
		run.emit(protocol.TextChunk(string(line) + "\n"))
		return
	}
	run.setConversationID(event.SessionID)

	switch event.Type {
	case "stream_event":
		if event.Event == nil || event.Event.Type != "content_block_delta" {
			return
		}
		switch event.Event.Delta.Type {
		case "text_delta":
			*streamedText = true
			run.emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
			run.emit(protocol.TextChunk(event.Event.Delta.Text))
		case "thinking_delta":
			run.emit(protocol.ThinkingChunk(event.Event.Delta.Thinking))
		}
	case "assistant":
		emitClaudeAssistant(run, event, *streamedText)
		*streamedText = false
	case "user":
		if event.Message == nil {
			return
		}
		for _, block := range event.Message.Content {
			if block.Type == "tool_result" {
				run.emit(protocol.ToolResult(block.ToolUseID, "", claudeResultText(block.Content), block.IsError))
			}
		}
	case "result":
		if event.IsError {
			run.fail("Claude Code lỗi: " + event.Result)
			return
		}
		run.finish()
	}
}

func emitClaudeAssistant(run *cliRun, event claudeEvent, alreadyStreamed bool) {
	if event.Message == nil {
		return
	}
	for _, block := range event.Message.Content {
		switch block.Type {
		case "text":
			if !alreadyStreamed { // Partial messages are unavailable on older CLIs.
				run.emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
				run.emit(protocol.TextChunk(block.Text))
			}
		case "tool_use":
			run.emit(protocol.AgentStateEvent(protocol.AgentStateExecutingTool, block.Name))
			run.emit(protocol.ToolCall(block.ID, block.Name, block.Input))
		}
	}
}

// claudeResultText handles tool output given as a string or as a list of text blocks.
func claudeResultText(raw json.RawMessage) string {
	var text string
	if json.Unmarshal(raw, &text) == nil {
		return text
	}
	var blocks []struct {
		Text string `json:"text"`
	}
	if json.Unmarshal(raw, &blocks) != nil {
		return string(raw)
	}
	for _, block := range blocks {
		text += block.Text
	}
	return text
}
