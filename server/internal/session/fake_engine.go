package session

import (
	"context"
	"fmt"
	"sync"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

type FakeToolCall struct {
	ID        string
	ToolName  string
	Summary   string
	Dangerous bool
}

// FakeScript defines how FakeEngine executes a turn.
type FakeScript struct {
	ThinkingChunks   []string
	TextChunks       []string
	ToolCall         *FakeToolCall
	RequireApproval  bool
	Delay            time.Duration
	ShouldFail       bool
	FailError        string
}

// FakeEngine provides a programmable, deterministic test harness for multi-agent workflows.
type FakeEngine struct {
	mu            sync.Mutex
	engineType    protocol.AgentType
	capabilities  EngineCapability
	script        FakeScript
	activeTurns   map[string]chan bool // turnID -> approval channel
	interrupted   map[string]bool
	executedTurns []TurnRequest
}

func NewFakeEngine(engineType protocol.AgentType, caps EngineCapability) *FakeEngine {
	return &FakeEngine{
		engineType:    engineType,
		capabilities:  caps,
		activeTurns:   make(map[string]chan bool),
		interrupted:   make(map[string]bool),
		executedTurns: make([]TurnRequest, 0),
	}
}

func (f *FakeEngine) Engine() protocol.AgentType {
	return f.engineType
}

func (f *FakeEngine) Capabilities() EngineCapability {
	return f.capabilities
}

func (f *FakeEngine) SetScript(script FakeScript) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.script = script
}

// QueueResponse is a test helper configuring a simple text response.
func (f *FakeEngine) QueueResponse(text string) {
	f.SetScript(FakeScript{TextChunks: []string{text}})
}

func (f *FakeEngine) GetExecutedTurns() []TurnRequest {
	f.mu.Lock()
	defer f.mu.Unlock()
	copied := make([]TurnRequest, len(f.executedTurns))
	copy(copied, f.executedTurns)
	return copied
}

func (f *FakeEngine) ExecuteTurn(
	ctx context.Context,
	req TurnRequest,
	emit EventEmitter,
) (*TurnResult, error) {
	f.mu.Lock()
	f.executedTurns = append(f.executedTurns, req)
	script := f.script
	approvalCh := make(chan bool, 1)
	f.activeTurns[req.TurnID] = approvalCh
	f.interrupted[req.TurnID] = false
	f.mu.Unlock()

	defer func() {
		f.mu.Lock()
		delete(f.activeTurns, req.TurnID)
		f.mu.Unlock()
	}()

	safeEmit := func(evt []byte) {
		if emit != nil {
			emit(evt)
		}
	}

	// 1. Initial agent state
	safeEmit(protocol.AgentStateEvent(protocol.AgentStateThinking, ""))

	if script.Delay > 0 {
		select {
		case <-ctx.Done():
			return &TurnResult{TurnID: req.TurnID, State: TurnCancelled}, nil
		case <-time.After(script.Delay):
		}
	}

	// 2. Stream thinking
	for _, thought := range script.ThinkingChunks {
		if f.isInterrupted(req.TurnID) || ctx.Err() != nil {
			return &TurnResult{TurnID: req.TurnID, State: TurnCancelled}, nil
		}
		safeEmit(protocol.ThinkingChunk(thought))
	}

	// 3. Approval request simulation
	if script.RequireApproval && script.ToolCall != nil {
		safeEmit(protocol.ApprovalRequired(
			script.ToolCall.ID,
			script.ToolCall.ToolName,
			script.ToolCall.Summary,
			script.ToolCall.Dangerous,
		))

		select {
		case <-ctx.Done():
			return &TurnResult{TurnID: req.TurnID, State: TurnCancelled}, nil
		case approved := <-approvalCh:
			if !approved {
				return &TurnResult{
					TurnID: req.TurnID,
					State:  TurnFailed,
					Error:  "Tool execution was rejected by user",
				}, nil
			}
		}
	}

	// 4. Stream response text
	var finalOutput string
	for _, text := range script.TextChunks {
		if f.isInterrupted(req.TurnID) || ctx.Err() != nil {
			return &TurnResult{TurnID: req.TurnID, State: TurnCancelled}, nil
		}
		finalOutput += text
		safeEmit(protocol.TextChunk(text))
	}

	if script.ShouldFail {
		return &TurnResult{
			TurnID: req.TurnID,
			State:  TurnFailed,
			Error:  script.FailError,
		}, fmt.Errorf("fake engine turn failure: %s", script.FailError)
	}

	safeEmit(protocol.MessageEnd(req.TurnID, req.ParentSessionID))

	return &TurnResult{
		TurnID:       req.TurnID,
		State:        TurnCompleted,
		Output:       finalOutput,
		InputTokens:  120,
		OutputTokens: 85,
	}, nil
}

func (f *FakeEngine) HandleApproval(turnID string, callID string, approved bool) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	ch, exists := f.activeTurns[turnID]
	if !exists {
		return fmt.Errorf("no active approval pending for turn %s", turnID)
	}
	ch <- approved
	return nil
}

func (f *FakeEngine) Interrupt(turnID string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.interrupted[turnID] = true
	return nil
}

func (f *FakeEngine) isInterrupted(turnID string) bool {
	f.mu.Lock()
	defer f.mu.Unlock()
	return f.interrupted[turnID]
}

func (f *FakeEngine) Close() error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.activeTurns = make(map[string]chan bool)
	return nil
}
