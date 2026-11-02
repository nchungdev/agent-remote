package adapter

import (
	"context"
	"fmt"
	"sync"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

type MockAdapter struct {
	mu          sync.Mutex
	ctx         context.Context
	cancelFunc  context.CancelFunc
	approvalCh  chan bool
	pendingCall string
}

func NewMockAdapter() *MockAdapter {
	return &MockAdapter{}
}

func (m *MockAdapter) AgentType() protocol.AgentType {
	return protocol.AgentTypeMock
}

func (m *MockAdapter) Initialize() error {
	return nil
}

func (m *MockAdapter) HandlePrompt(opts PromptOptions, emit Emitter) {
	prompt := opts.Prompt
	m.mu.Lock()
	m.ctx, m.cancelFunc = context.WithCancel(context.Background())
	m.approvalCh = make(chan bool, 1)
	ctx := m.ctx
	m.mu.Unlock()

	msgID := fmt.Sprintf("mock_%d", time.Now().Unix())

	// 1. Thinking state
	emit(protocol.AgentStateEvent(protocol.AgentStateThinking, ""))
	thoughts := []string{
		fmt.Sprintf("Nhận được yêu cầu: '%s'.\n", prompt),
		"Đang kiểm tra workspace và git status...\n",
		"Cần chạy lệnh kiểm tra trước khi thực hiện.\n",
	}

	for _, t := range thoughts {
		select {
		case <-ctx.Done():
			emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
			return
		case <-time.After(300 * time.Millisecond):
			emit(protocol.ThinkingChunk(t))
		}
	}

	// 2. Tool call requiring approval
	callID := fmt.Sprintf("tc_%d", time.Now().Unix())
	m.mu.Lock()
	m.pendingCall = callID
	m.mu.Unlock()

	emit(protocol.AgentStateEvent(protocol.AgentStateWaitingApproval, ""))
	emit(protocol.ToolCall(callID, "run_command", map[string]any{"CommandLine": "git status"}))
	emit(protocol.ApprovalRequired(callID, "run_command", "Kiểm tra trạng thái Git repository (git status)", false))

	// Wait for approval
	var approved bool
	select {
	case <-ctx.Done():
		emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
		return
	case approved = <-m.approvalCh:
	}

	if !approved {
		emit(protocol.AgentStateEvent(protocol.AgentStateExecutingTool, ""))
		emit(protocol.ToolResult(callID, "run_command", "Lệnh bị người dùng từ chối.", true))
		emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
		emit(protocol.TextChunk("Bạn đã hủy thực thi lệnh."))
		emit(protocol.MessageEnd(msgID))
		return
	}

	// Tool approved & executed
	emit(protocol.AgentStateEvent(protocol.AgentStateExecutingTool, ""))
	time.Sleep(300 * time.Millisecond)
	mockOutput := "On branch main\nYour branch is up to date with 'origin/main'.\nnothing to commit, working tree clean."
	emit(protocol.ToolResult(callID, "run_command", mockOutput, false))

	// 3. Stream text answer
	emit(protocol.AgentStateEvent(protocol.AgentStateIdle, ""))
	chunks := []string{
		"Đã kiểm tra thành công. ",
		"Workspace của bạn hoàn toàn sạch sẽ, ",
		fmt.Sprintf("không có xung đột nào cho prompt: '%s'.", prompt),
	}

	for _, c := range chunks {
		select {
		case <-ctx.Done():
			return
		case <-time.After(150 * time.Millisecond):
			emit(protocol.TextChunk(c))
		}
	}

	emit(protocol.MessageEnd(msgID))
}

func (m *MockAdapter) HandleApproval(callID string, approved bool) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.approvalCh != nil {
		select {
		case m.approvalCh <- approved:
		default:
		}
	}
}

func (m *MockAdapter) Cancel() {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.cancelFunc != nil {
		m.cancelFunc()
	}
}

func (m *MockAdapter) Close() {
	m.Cancel()
}
