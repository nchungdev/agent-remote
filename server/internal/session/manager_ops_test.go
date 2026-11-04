package session

import (
	"context"
	"path/filepath"
	"testing"

	"app.duinch.agentremote/server/internal/protocol"
)

func TestSwitchWriterHandoffAndSeenSeq(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := NewStore(filepath.Join(tmpDir, "test_mgr.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	reg := DefaultRegistry()
	fakeCodex := NewFakeEngine(protocol.AgentTypeCodex, EngineCapability{
		SupportsAppServer:    true,
		SupportsNativeResume: true,
		HasReadOnlySandbox:   true,
	})
	fakeClaude := NewFakeEngine(protocol.AgentTypeClaudeCode, EngineCapability{
		SupportsNativeResume: true,
		HasReadOnlySandbox:   true,
	})
	reg.Register(fakeCodex)
	reg.Register(fakeClaude)

	mgr := NewManager(reg, store)

	sess, err := mgr.CreateParentSession("ps-switch-test", "Switch Session", WorkspaceRef{RootPath: "/test"}, protocol.AgentTypeCodex, "gpt-4o")
	if err != nil {
		t.Fatalf("failed create parent session: %v", err)
	}

	initialWriterID := sess.WriterLease.WriterAgentID // "CODEX-agent-1"

	// 1. Initial writer executes Turn 1
	fakeCodex.QueueResponse("Turn 1 completed by Codex")
	_, err = mgr.ExecuteTurn(context.Background(), TurnRequest{
		ParentSessionID: sess.ID,
		TurnID:          "turn-1",
		TargetAgentID:   initialWriterID,
		Prompt:          "Initial prompt to codex",
	}, nil)
	if err != nil {
		t.Fatalf("turn 1 failed: %v", err)
	}

	// 2. Switch writer to Claude Code
	lease2, err := mgr.SwitchWriter(sess.ID, "claude-agent-1", protocol.AgentTypeClaudeCode, "claude-3-5-sonnet", 1)
	if err != nil {
		t.Fatalf("switch writer failed: %v", err)
	}
	if lease2.Revision != 2 || lease2.WriterAgentID != "claude-agent-1" {
		t.Fatalf("lease2 mismatch: %+v", lease2)
	}

	// 3. Claude executes Turn 2
	fakeClaude.QueueResponse("Turn 2 completed by Claude")
	_, err = mgr.ExecuteTurn(context.Background(), TurnRequest{
		ParentSessionID: sess.ID,
		TurnID:          "turn-2",
		TargetAgentID:   "claude-agent-1",
		Prompt:          "Prompt to claude",
	}, nil)
	if err != nil {
		t.Fatalf("turn 2 failed: %v", err)
	}

	// Check events unseen by Codex
	codexBinding := sess.Agents[initialWriterID]
	eventsAfterCodexSeen, err := store.GetEvents(sess.ID, codexBinding.SeenSeq)
	if err != nil {
		t.Fatalf("failed to query events: %v", err)
	}
	// Should see the writer transfer event and Claude's turn completion
	if len(eventsAfterCodexSeen) < 2 {
		t.Fatalf("expected at least 2 unseen events for Codex, got %d", len(eventsAfterCodexSeen))
	}

	// 4. Stale CAS switch must fail without corrupting active lease
	_, err = mgr.SwitchWriter(sess.ID, initialWriterID, protocol.AgentTypeCodex, "", 1) // Stale revision 1
	if err != ErrWriterConflict {
		t.Fatalf("expected ErrWriterConflict on stale revision, got: %v", err)
	}

	// Active lease remains Claude at revision 2
	refreshed, _ := mgr.GetSession(sess.ID)
	if refreshed.WriterLease.WriterAgentID != "claude-agent-1" || refreshed.WriterLease.Revision != 2 {
		t.Fatalf("active writer corrupted after conflict: %+v", refreshed.WriterLease)
	}
}

func TestConsultRejectsIncapableEngineAndProtectsWriter(t *testing.T) {
	reg := DefaultRegistry()
	// An engine without read-only sandbox or consult branch capability
	incapableEngine := NewFakeEngine(protocol.AgentType("UNSAFE_ENGINE"), EngineCapability{
		SupportsConsultBranch: false,
		HasReadOnlySandbox:    false,
	})
	safeClaude := NewFakeEngine(protocol.AgentTypeClaudeCode, EngineCapability{
		SupportsConsultBranch: true,
		HasReadOnlySandbox:    true,
	})
	reg.Register(incapableEngine)
	reg.Register(safeClaude)

	mgr := NewManager(reg)
	sess, _ := mgr.CreateParentSession("ps-consult-test", "Consult Test", WorkspaceRef{RootPath: "/test"}, protocol.AgentTypeCodex, "gpt-4o")

	origWriter := sess.WriterLease.WriterAgentID
	origRev := sess.WriterLease.Revision

	// Attempt consult against unsafe engine
	_, err := mgr.Consult(context.Background(), sess.ID, "UNSAFE_ENGINE", "", "What is this?", BuildContextOptions{}, nil)
	if err != ErrEngineCannotConsult {
		t.Fatalf("expected ErrEngineCannotConsult, got: %v", err)
	}

	// Consult safe Claude engine
	safeClaude.QueueResponse("This looks like a valid architecture.")
	branch, err := mgr.Consult(context.Background(), sess.ID, protocol.AgentTypeClaudeCode, "claude-3-5-sonnet", "Explain this design", BuildContextOptions{}, nil)
	if err != nil {
		t.Fatalf("safe consult failed: %v", err)
	}
	if branch.Response != "This looks like a valid architecture." {
		t.Errorf("unexpected branch response: %s", branch.Response)
	}

	// Invariant check: writer lease was untouched
	if sess.WriterLease.WriterAgentID != origWriter || sess.WriterLease.Revision != origRev {
		t.Fatalf("writer lease was altered by consult branch: %+v", sess.WriterLease)
	}
}
