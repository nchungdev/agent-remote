package adapter

import (
	"bufio"
	"context"
	"fmt"
	"os"
	"os/exec"
	"sync"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

type ClaudeCodeAdapter struct {
	mu            sync.Mutex
	claudeBin     string
	workspacePath string
	ctx           context.Context
	cancelFunc    context.CancelFunc
}

func NewClaudeCodeAdapter(workspacePath string) *ClaudeCodeAdapter {
	if workspacePath == "" {
		workspacePath, _ = os.Getwd()
	}
	bin, _ := exec.LookPath("claude")
	return &ClaudeCodeAdapter{
		claudeBin:     bin,
		workspacePath: workspacePath,
	}
}

func (c *ClaudeCodeAdapter) AgentType() protocol.AgentType {
	return protocol.AgentTypeClaudeCode
}

func (c *ClaudeCodeAdapter) Initialize() error {
	bin, err := exec.LookPath("claude")
	c.mu.Lock()
	c.claudeBin = bin
	c.mu.Unlock()
	return err
}

func (c *ClaudeCodeAdapter) HandlePrompt(opts PromptOptions, emit Emitter) {
	c.mu.Lock()
	if c.claudeBin == "" {
		c.mu.Unlock()
		emit(protocol.ErrorEvent("Không tìm thấy lệnh 'claude' CLI trên máy Mac. Cài đặt: npm i -g @anthropic-ai/claude-code"))
		return
	}
	c.ctx, c.cancelFunc = context.WithCancel(context.Background())
	ctx := c.ctx
	claudePath := c.claudeBin
	workDir := c.workspacePath
	if opts.Workspace != "" {
		workDir = opts.Workspace
	}
	c.mu.Unlock()

	msgID := fmt.Sprintf("claude_%d", time.Now().Unix())
	emit(protocol.AgentStateEvent(protocol.AgentStateThinking, ""))
	emit(protocol.ThinkingChunk("Khởi động phiên Claude Code...\n"))

	args := []string{"-p", opts.Prompt}
	if opts.ConversationID != "" {
		args = append(args, "--resume", opts.ConversationID)
	}
	if opts.Model != "" {
		args = append(args, "--model", opts.Model)
	}

	cmd := exec.CommandContext(ctx, claudePath, args...)
	cmd.Dir = workDir

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		emit(protocol.ErrorEvent(fmt.Sprintf("Không thể mở stdout pipe: %v", err)))
		return
	}
	cmd.Stderr = cmd.Stdout

	if err := cmd.Start(); err != nil {
		emit(protocol.ErrorEvent(fmt.Sprintf("Lỗi khởi động tiến trình claude: %v", err)))
		return
	}

	emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
	scanner := bufio.NewScanner(stdout)
	for scanner.Scan() {
		line := scanner.Text() + "\n"
		emit(protocol.TextChunk(line))
	}

	_ = cmd.Wait()
	emit(protocol.MessageEnd(msgID))
}

func (c *ClaudeCodeAdapter) HandleApproval(callID string, approved bool) {}

func (c *ClaudeCodeAdapter) Cancel() {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.cancelFunc != nil {
		c.cancelFunc()
	}
}

func (c *ClaudeCodeAdapter) Close() {
	c.Cancel()
}
