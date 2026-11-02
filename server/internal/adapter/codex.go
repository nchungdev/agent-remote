package adapter

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

type CodexAdapter struct {
	mu         sync.Mutex
	binaryPath string
	ctx        context.Context
	cancelFunc context.CancelFunc
}

func NewCodexAdapter() *CodexAdapter {
	home, _ := os.UserHomeDir()
	bin, err := exec.LookPath("codex")
	if err != nil {
		candidate := filepath.Join(home, ".local", "bin", "codex")
		if _, statErr := os.Stat(candidate); statErr == nil {
			bin = candidate
		}
	}
	return &CodexAdapter{binaryPath: bin}
}

func (c *CodexAdapter) AgentType() protocol.AgentType {
	return protocol.AgentTypeCodex
}

func (c *CodexAdapter) Initialize() error {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.binaryPath == "" {
		home, _ := os.UserHomeDir()
		bin, err := exec.LookPath("codex")
		if err != nil {
			candidate := filepath.Join(home, ".local", "bin", "codex")
			if _, statErr := os.Stat(candidate); statErr == nil {
				bin = candidate
			}
		}
		c.binaryPath = bin
	}
	return nil
}

type codexJsonLine struct {
	Type    string `json:"type"`
	Payload struct {
		Text    string `json:"text,omitempty"`
		Role    string `json:"role,omitempty"`
		Content []struct {
			Text string `json:"text,omitempty"`
		} `json:"content,omitempty"`
	} `json:"payload,omitempty"`
}

func (c *CodexAdapter) HandlePrompt(opts PromptOptions, emit Emitter) {
	c.mu.Lock()
	bin := c.binaryPath
	if bin == "" {
		c.mu.Unlock()
		emit(protocol.ErrorEvent("Không tìm thấy lệnh 'codex' trên máy Mac."))
		return
	}

	c.ctx, c.cancelFunc = context.WithCancel(context.Background())
	ctx := c.ctx
	c.mu.Unlock()

	var args []string
	if opts.ConversationID != "" {
		args = []string{"exec", "resume", opts.ConversationID, opts.Prompt, "--json"}
	} else {
		args = []string{"exec", opts.Prompt, "--json"}
	}

	if opts.Model != "" {
		args = append(args, "--model", opts.Model)
	}

	cmd := exec.CommandContext(ctx, bin, args...)
	if opts.Workspace != "" {
		cmd.Dir = opts.Workspace
	}

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		emit(protocol.ErrorEvent(fmt.Sprintf("Không thể mở stdout pipe: %v", err)))
		return
	}
	cmd.Stderr = os.Stderr

	emit(protocol.AgentStateEvent(protocol.AgentStateThinking, "Codex đang phân tích..."))

	if err := cmd.Start(); err != nil {
		emit(protocol.ErrorEvent(fmt.Sprintf("Lỗi khởi động codex: %v", err)))
		return
	}

	scanner := bufio.NewScanner(stdout)
	scanner.Buffer(make([]byte, 256*1024), 2*1024*1024)
	msgID := fmt.Sprintf("codex_%d", time.Now().UnixMilli())

	for scanner.Scan() {
		raw := scanner.Bytes()
		var line codexJsonLine
		if err := json.Unmarshal(raw, &line); err != nil {
			// fallback raw text line
			emit(protocol.TextChunk(string(raw) + "\n"))
			continue
		}

		if line.Payload.Text != "" {
			emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
			emit(protocol.TextChunk(line.Payload.Text))
		} else if len(line.Payload.Content) > 0 {
			for _, item := range line.Payload.Content {
				if item.Text != "" {
					emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
					emit(protocol.TextChunk(item.Text))
				}
			}
		}
	}

	_ = cmd.Wait()
	emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
	emit(protocol.MessageEnd(msgID))
}

func (c *CodexAdapter) HandleApproval(callID string, approved bool) {}

func (c *CodexAdapter) Cancel() {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.cancelFunc != nil {
		c.cancelFunc()
	}
}

func (c *CodexAdapter) Close() {
	c.Cancel()
}
