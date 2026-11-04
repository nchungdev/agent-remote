package session

import (
	"bytes"
	"context"
	"testing"

	"app.duinch.agentremote/server/internal/protocol"
)

func TestParentSessionLifecycleAndWriterLease(t *testing.T) {
	reg := NewManager(DefaultRegistry()).registry
	fakeCodex := NewFakeEngine(protocol.AgentTypeCodex, EngineCapability{
		SupportsAppServer:    true,
		SupportsNativeResume: true,
		SupportsApprovalFlow: true,
	})
	fakeClaude := NewFakeEngine(protocol.AgentTypeClaudeCode, EngineCapability{
		SupportsNativeResume:  true,
		SupportsConsultBranch: true,
	})

	reg.Register(fakeCodex)
	reg.Register(fakeClaude)

	mgr := NewManager(reg)
	ws := WorkspaceRef{
		RepositoryID: "repo-1",
		CheckoutID:   "main",
		RootPath:     "/workspace/agent-remote",
	}

	// 1. Create parent session with initial writer (Codex)
	sess, err := mgr.CreateParentSession("parent-1", "Feature A", ws, protocol.AgentTypeCodex, "gpt-5")
	if err != nil {
		t.Fatalf("failed to create parent session: %v", err)
	}

	codexAgentID := sess.WriterLease.WriterAgentID
	if sess.WriterLease.Revision != 1 {
		t.Errorf("expected initial revision 1, got %d", sess.WriterLease.Revision)
	}

	// 2. Set script on Codex and execute primary turn
	fakeCodex.SetScript(FakeScript{
		ThinkingChunks: []string{"Analyzing codebase...\n"},
		TextChunks:     []string{"Implementation completed."},
	})

	var emitted [][]byte
	emit := func(evt []byte) {
		emitted = append(emitted, evt)
	}

	req := TurnRequest{
		ParentSessionID:  sess.ID,
		TurnID:           "turn-1",
		TargetAgentID:    codexAgentID,
		ExpectedRevision: 1,
		Prompt:           "Implement feature A",
		Workspace:        ws,
	}

	res, err := mgr.ExecuteTurn(context.Background(), req, emit)
	if err != nil {
		t.Fatalf("turn execution failed: %v", err)
	}
	if res.State != TurnCompleted || res.Output != "Implementation completed." {
		t.Errorf("unexpected turn result: %+v", res)
	}
	if len(emitted) == 0 {
		t.Errorf("expected emitted events, got 0")
	}

	// 3. Attempt turn with Claude before handoff (should fail: not writer)
	claudeAgentID := "claude-agent-1"
	reqClaude := TurnRequest{
		ParentSessionID:  sess.ID,
		TurnID:           "turn-2",
		TargetAgentID:    claudeAgentID,
		ExpectedRevision: 1,
		Prompt:           "Continue with tests",
		Workspace:        ws,
	}

	_, err = mgr.ExecuteTurn(context.Background(), reqClaude, emit)
	if err == nil {
		t.Fatalf("expected ErrAgentNotWriter, got nil")
	}

	// 4. Stale CAS revision on handoff should fail
	_, err = mgr.TransferWriter(sess.ID, claudeAgentID, protocol.AgentTypeClaudeCode, "claude-3-7-sonnet", 999)
	if err == nil {
		t.Fatalf("expected ErrWriterConflict for stale revision, got nil")
	}

	// 5. Successful CAS handoff from Codex to Claude
	lease, err := mgr.TransferWriter(sess.ID, claudeAgentID, protocol.AgentTypeClaudeCode, "claude-3-7-sonnet", 1)
	if err != nil {
		t.Fatalf("transfer writer failed: %v", err)
	}
	if lease.Revision != 2 || lease.WriterAgentID != claudeAgentID {
		t.Errorf("unexpected lease after transfer: %+v", lease)
	}

	// 6. Now Claude is writer -> turn succeeds
	fakeClaude.SetScript(FakeScript{
		TextChunks: []string{"Tests added and passing."},
	})

	reqClaude.ExpectedRevision = 2
	resClaude, err := mgr.ExecuteTurn(context.Background(), reqClaude, emit)
	if err != nil {
		t.Fatalf("claude turn failed: %v", err)
	}
	if resClaude.Output != "Tests added and passing." {
		t.Errorf("unexpected claude output: %s", resClaude.Output)
	}
}

func TestConsultBranchExecution(t *testing.T) {
	reg := NewManager(DefaultRegistry()).registry
	fakeClaude := NewFakeEngine(protocol.AgentTypeClaudeCode, EngineCapability{
		SupportsConsultBranch: true,
		HasReadOnlySandbox:    true,
	})
	reg.Register(fakeClaude)

	mgr := NewManager(reg)
	ws := WorkspaceRef{RootPath: "/workspace/agent-remote"}
	sess, _ := mgr.CreateParentSession("parent-2", "Feature B", ws, protocol.AgentTypeClaudeCode, "sonnet")

	fakeClaude.SetScript(FakeScript{
		TextChunks: []string{"Diff looks safe and follows clean architecture."},
	})

	// Consult branch turn (@claude:btw)
	req := TurnRequest{
		ParentSessionID: sess.ID,
		TurnID:          "branch-turn-1",
		TargetAgentID:   sess.WriterLease.WriterAgentID,
		Prompt:          "@claude:btw review diff",
		Workspace:       ws,
		IsConsult:       true,
	}

	res, err := mgr.ExecuteTurn(context.Background(), req, func(_ []byte) {})
	if err != nil {
		t.Fatalf("consult turn failed: %v", err)
	}

	if res.Output != "Diff looks safe and follows clean architecture." {
		t.Errorf("unexpected consult output: %s", res.Output)
	}

	// Verify recorded branch
	branch, exists := sess.Branches["branch-turn-1"]
	if !exists || branch.Response != res.Output {
		t.Errorf("branch was not recorded properly: %+v", branch)
	}
}

func TestApprovalFlowInFakeEngine(t *testing.T) {
	engine := NewFakeEngine(protocol.AgentTypeCodex, EngineCapability{SupportsApprovalFlow: true})
	engine.SetScript(FakeScript{
		RequireApproval: true,
		ToolCall: &FakeToolCall{
			ID:        "call-123",
			ToolName:  "delete_file",
			Summary:   "Delete legacy file",
			Dangerous: true,
		},
		TextChunks: []string{"File deleted."},
	})

	approvalChan := make(chan struct{}, 1)
	emit := func(evt []byte) {
		if bytes.Contains(evt, []byte("APPROVAL_REQUIRED")) {
			select {
			case approvalChan <- struct{}{}:
			default:
			}
		}
	}

	req := TurnRequest{TurnID: "turn-approval"}

	go func() {
		<-approvalChan
		_ = engine.HandleApproval("turn-approval", "call-123", true)
	}()

	res, err := engine.ExecuteTurn(context.Background(), req, emit)
	if err != nil {
		t.Fatalf("approval turn execution failed: %v", err)
	}
	if res.State != TurnCompleted {
		t.Errorf("expected approval completion, got: %+v", res)
	}
}
