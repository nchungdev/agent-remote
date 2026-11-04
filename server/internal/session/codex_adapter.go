package session

import (
	"context"
	"sync"

	"app.duinch.agentremote/server/internal/adapter"
	"app.duinch.agentremote/server/internal/protocol"
)

// CodexSessionAdapter bridges the Codex CLI / App Server into the Parent Session EngineSessionAdapter.
type CodexSessionAdapter struct {
	mu           sync.Mutex
	innerAdapter *adapter.CodexAdapter
	runningTurns map[string]context.CancelFunc
}

func NewCodexSessionAdapter() *CodexSessionAdapter {
	return &CodexSessionAdapter{
		innerAdapter: adapter.NewCodexAdapter(),
		runningTurns: make(map[string]context.CancelFunc),
	}
}

func (a *CodexSessionAdapter) Engine() protocol.AgentType {
	return protocol.AgentTypeCodex
}

func (a *CodexSessionAdapter) Capabilities() EngineCapability {
	return EngineCapability{
		SupportsAppServer:     true,
		SupportsNativeResume:  true,
		SupportsConsultBranch: true,
		HasReadOnlySandbox:    true,
		SupportsApprovalFlow:  true,
	}
}

// ExecuteTurn runs a prompt turn using Codex CLI / app server under the session rules.
func (a *CodexSessionAdapter) ExecuteTurn(
	ctx context.Context,
	req TurnRequest,
	emit EventEmitter,
) (*TurnResult, error) {
	turnCtx, cancel := context.WithCancel(ctx)
	a.mu.Lock()
	a.runningTurns[req.TurnID] = cancel
	a.mu.Unlock()

	defer func() {
		a.mu.Lock()
		delete(a.runningTurns, req.TurnID)
		a.mu.Unlock()
		cancel()
	}()

	permissionMode := req.PermissionMode
	if req.IsConsult {
		// Consult branches must always execute in strict read-only sandbox
		permissionMode = "read-only"
	} else if permissionMode == "" {
		permissionMode = "workspace-write"
	}

	opts := adapter.PromptOptions{
		Prompt:         req.Prompt,
		Model:          req.Model,
		Workspace:      req.Workspace.RootPath,
		PermissionMode: permissionMode,
	}

	resultChan := make(chan *TurnResult, 1)
	var outputBuffer string
	var errBuffer string

	wrappedEmit := func(event []byte) {
		if emit != nil {
			emit(event)
		}
	}

	go func() {
		a.innerAdapter.HandlePrompt(opts, wrappedEmit)
		resultChan <- &TurnResult{
			TurnID: req.TurnID,
			State:  TurnCompleted,
			Output: outputBuffer,
			Error:  errBuffer,
		}
	}()

	select {
	case <-turnCtx.Done():
		a.innerAdapter.Cancel()
		return &TurnResult{
			TurnID: req.TurnID,
			State:  TurnCancelled,
			Error:  "Turn cancelled by context",
		}, turnCtx.Err()
	case res := <-resultChan:
		return res, nil
	}
}

func (a *CodexSessionAdapter) HandleApproval(turnID string, callID string, approved bool) error {
	a.innerAdapter.HandleApproval(callID, approved)
	return nil
}

func (a *CodexSessionAdapter) Interrupt(turnID string) error {
	a.mu.Lock()
	cancel, exists := a.runningTurns[turnID]
	a.mu.Unlock()

	if exists {
		cancel()
	}
	a.innerAdapter.Cancel()
	return nil
}

func (a *CodexSessionAdapter) Close() error {
	a.innerAdapter.Close()
	return nil
}
