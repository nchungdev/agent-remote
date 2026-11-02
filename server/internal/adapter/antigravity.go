package adapter

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

type AntigravityAdapter struct {
	mu         sync.Mutex
	binaryPath string
	ctx        context.Context
	cancelFunc context.CancelFunc
	stdinPipe  io.WriteCloser
}

func NewAntigravityAdapter() *AntigravityAdapter {
	home, _ := os.UserHomeDir()
	bin, err := exec.LookPath("agy")
	if err != nil {
		candidate := filepath.Join(home, ".local", "bin", "agy")
		if _, statErr := os.Stat(candidate); statErr == nil {
			bin = candidate
		}
	}
	return &AntigravityAdapter{binaryPath: bin}
}

func (a *AntigravityAdapter) AgentType() protocol.AgentType {
	return protocol.AgentTypeAntigravity
}

func (a *AntigravityAdapter) Initialize() error {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.binaryPath == "" {
		home, _ := os.UserHomeDir()
		bin, err := exec.LookPath("agy")
		if err != nil {
			candidate := filepath.Join(home, ".local", "bin", "agy")
			if _, statErr := os.Stat(candidate); statErr == nil {
				bin = candidate
			}
		}
		a.binaryPath = bin
	}
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

func (a *AntigravityAdapter) HandlePrompt(opts PromptOptions, emit Emitter) {
	a.mu.Lock()
	bin := a.binaryPath
	if bin == "" {
		a.mu.Unlock()
		emit(protocol.ErrorEvent("Không tìm thấy binary 'agy' trên máy Mac."))
		return
	}

	if a.cancelFunc != nil {
		a.cancelFunc()
	}
	a.ctx, a.cancelFunc = context.WithCancel(context.Background())
	ctx := a.ctx
	a.mu.Unlock()

	args := []string{
		"--print", opts.Prompt,
		"--output-format", "stream-json",
	}

	if opts.ConversationID != "" {
		args = append(args, "--conversation", opts.ConversationID)
	}
	if opts.Model != "" {
		args = append(args, "--model", opts.Model)
	}

	cmd := exec.CommandContext(ctx, bin, args...)
	if opts.Workspace != "" {
		cmd.Dir = opts.Workspace
	}

	stdin, _ := cmd.StdinPipe()
	a.mu.Lock()
	a.stdinPipe = stdin
	a.mu.Unlock()

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		emit(protocol.ErrorEvent(fmt.Sprintf("Không thể mở stdout pipe: %v", err)))
		return
	}
	cmd.Stderr = os.Stderr

	emit(protocol.AgentStateEvent(protocol.AgentStateThinking, "Đang xử lý prompt..."))

	if err := cmd.Start(); err != nil {
		emit(protocol.ErrorEvent(fmt.Sprintf("Lỗi khởi động tiến trình agy: %v", err)))
		return
	}

	scanner := bufio.NewScanner(stdout)
	scanner.Buffer(make([]byte, 256*1024), 2*1024*1024)

	activeConvID := opts.ConversationID
	msgID := fmt.Sprintf("msg_%d", time.Now().UnixMilli())
	activeCalls := make(map[int]string)

	for scanner.Scan() {
		line := scanner.Bytes()
		var stream agyStreamLine
		if err := json.Unmarshal(line, &stream); err != nil {
			trimmed := strings.TrimSpace(string(line))
			if strings.HasPrefix(trimmed, "error:") || strings.HasPrefix(trimmed, "AGY_ERROR:") {
				emit(protocol.ErrorEvent(trimmed))
			}
			continue
		}

		if stream.Event == "init" && stream.ConversationID != "" {
			activeConvID = stream.ConversationID
		}

		if stream.StepUpdate != nil {
			su := stream.StepUpdate

			// 1. Thinking / Thought updates
			thought := su.ThoughtDelta
			if thought == "" {
				thought = su.Thinking
			}
			if thought != "" {
				emit(protocol.AgentStateEvent(protocol.AgentStateThinking, ""))
				emit(protocol.ThinkingChunk(thought))
			}

			// 2. Tool calls (ACTIVE and DONE states)
			if su.StepType == "tool" {
				if su.State == "ACTIVE" {
					callID := fmt.Sprintf("call_%d_%d", su.StepIndex, time.Now().UnixMilli())
					activeCalls[su.StepIndex] = callID
					var args map[string]any
					if su.ToolInfo != nil {
						args = su.ToolInfo.Parameters
					}
					emit(protocol.AgentStateEvent(protocol.AgentStateExecutingTool, su.ToolName))
					emit(protocol.ToolCall(callID, su.ToolName, args))
				} else if su.State == "DONE" {
					callID, ok := activeCalls[su.StepIndex]
					if !ok {
						callID = fmt.Sprintf("call_%d_%d", su.StepIndex, time.Now().UnixMilli())
					}
					out := ""
					if su.ToolInfo != nil {
						out = su.ToolInfo.Output
					}
					emit(protocol.ToolResult(callID, su.ToolName, out, false))
				}
			}

			// 3. Text output from assistant
			if su.StepType == "agent_response" && su.TextDelta != "" {
				emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
				emit(protocol.TextChunk(su.TextDelta))
			}
		}

		if stream.Result != nil {
			if stream.Result.Status == "ERROR" && stream.Result.Error != "" {
				emit(protocol.ErrorEvent(stream.Result.Error))
			}
		}
	}

	_ = cmd.Wait()
	emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
	emit(protocol.MessageEnd(activeConvID + "_" + msgID))
}

func (a *AntigravityAdapter) HandleApproval(callID string, approved bool) {
	a.mu.Lock()
	pipe := a.stdinPipe
	cancel := a.cancelFunc
	a.mu.Unlock()

	if !approved {
		if pipe != nil {
			_, _ = pipe.Write([]byte("n\n"))
		}
		if cancel != nil {
			cancel()
		}
		return
	}
	if pipe != nil {
		_, _ = pipe.Write([]byte("y\n"))
	}
}

func (a *AntigravityAdapter) Cancel() {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.cancelFunc != nil {
		a.cancelFunc()
	}
}

func (a *AntigravityAdapter) Close() {
	a.Cancel()
}
