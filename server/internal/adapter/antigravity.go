package adapter

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

type AntigravityAdapter struct {
	cliRunner
	binaryPath func() string
}

func NewAntigravityAdapter() *AntigravityAdapter {
	return &AntigravityAdapter{binaryPath: func() string { return findCLI("agy") }}
}

func (a *AntigravityAdapter) AgentType() protocol.AgentType {
	return protocol.AgentTypeAntigravity
}

func (a *AntigravityAdapter) Initialize() error {
	return nil
}

type agyStreamLine struct {
	Event          string `json:"event"`
	ConversationID string `json:"conversation_id,omitempty"`
	StepUpdate     *struct {
		StepIndex       int     `json:"step_index"`
		StepType        string  `json:"step_type"`
		TextDelta       string  `json:"text_delta"`
		ThoughtDelta    string  `json:"thought_delta"`
		Thinking        string  `json:"thinking"`
		State           string  `json:"state"`
		DurationSeconds float64 `json:"duration_seconds"`
		ToolName        string  `json:"tool_name"`
		ToolInfo        *struct {
			Name       string         `json:"name"`
			Parameters map[string]any `json:"parameters"`
			Output     string         `json:"output"`
		} `json:"tool_info"`
	} `json:"step_update,omitempty"`
	Result *struct {
		Status         string `json:"status"`
		Error          string `json:"error"`
		Response       string `json:"response"`
		ConversationID string `json:"conversation_id"`
	} `json:"result,omitempty"`
}

func agyArgs(opts PromptOptions) []string {
	// --print=value keeps a prompt starting with "-" from being parsed as a flag.
	args := []string{"--print=" + opts.Prompt, "--output-format", "stream-json"}
	if opts.ConversationID != "" {
		args = append(args, "--conversation", opts.ConversationID)
	}
	if opts.Model != "" {
		args = append(args, "--model", opts.Model)
	}
	switch opts.PermissionMode {
	case "turbo":
		args = append(args, "--dangerously-skip-permissions")
	case "plan", "accept-edits", "auto":
		args = append(args, "--mode", opts.PermissionMode)
	}
	return args
}

func (a *AntigravityAdapter) HandlePrompt(opts PromptOptions, emit Emitter) {
	bin := a.binaryPath()
	if bin == "" {
		a.begin(emit, fmt.Sprintf("msg_%d", time.Now().UnixMilli())).fail("Không tìm thấy binary 'agy' trên máy Mac.")
		return
	}
	if err := validateOptions(opts); err != nil {
		a.begin(emit, fmt.Sprintf("msg_%d", time.Now().UnixMilli())).fail(err.Error())
		return
	}

	run := a.begin(emit, fmt.Sprintf("msg_%d", time.Now().UnixMilli()))
	run.setConversationID(opts.ConversationID)
	run.emit(protocol.AgentStateEvent(protocol.AgentStateThinking, "Đang xử lý prompt..."))

	activeCalls := make(map[int]string)
	run.execute("Antigravity", bin, agyArgs(opts), workingDir(opts.Workspace), func(line []byte) {
		handleAgyLine(run, line, activeCalls)
	})
}

func handleAgyLine(run *cliRun, line []byte, activeCalls map[int]string) {
	var stream agyStreamLine
	if err := json.Unmarshal(line, &stream); err != nil {
		trimmed := strings.TrimSpace(string(line))
		if strings.HasPrefix(trimmed, "error:") || strings.HasPrefix(trimmed, "AGY_ERROR:") {
			run.emit(protocol.ErrorEvent(trimmed))
		}
		return
	}
	run.setConversationID(stream.ConversationID)

	if su := stream.StepUpdate; su != nil {
		thought := su.ThoughtDelta
		if thought == "" {
			thought = su.Thinking
		}
		if thought != "" {
			run.emit(protocol.AgentStateEvent(protocol.AgentStateThinking, ""))
			run.emit(protocol.ThinkingChunk(thought))
		}

		if su.StepType == "tool" {
			if su.State == "ACTIVE" {
				callID := fmt.Sprintf("call_%d_%d", su.StepIndex, time.Now().UnixMilli())
				activeCalls[su.StepIndex] = callID
				var args map[string]any
				if su.ToolInfo != nil {
					args = su.ToolInfo.Parameters
				}
				run.emit(protocol.AgentStateEvent(protocol.AgentStateExecutingTool, su.ToolName))
				run.emit(protocol.ToolCall(callID, su.ToolName, args))
			} else if su.State == "DONE" {
				callID, ok := activeCalls[su.StepIndex]
				if !ok {
					callID = fmt.Sprintf("call_%d_%d", su.StepIndex, time.Now().UnixMilli())
				}
				out := ""
				if su.ToolInfo != nil {
					out = su.ToolInfo.Output
				}
				run.emit(protocol.ToolResult(callID, su.ToolName, out, false))
			}
		}

		if su.StepType == "agent_response" && su.TextDelta != "" {
			run.emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
			run.emit(protocol.TextChunk(su.TextDelta))
		}
	}

	if result := stream.Result; result != nil {
		run.setConversationID(result.ConversationID)
		if result.Status == "ERROR" {
			detail := result.Error
			if detail == "" {
				detail = "lượt chạy thất bại"
			}
			run.fail("Antigravity lỗi: " + detail)
			return
		}
		run.finish()
	}
}
