package session

import (
	"path/filepath"
	"sync"
	"testing"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

func TestStoreMigrationAndLifecycle(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_session.db")

	store, err := NewStore(dbPath)
	if err != nil {
		t.Fatalf("failed to init store: %v", err)
	}
	defer store.Close()

	sess := &ParentSession{
		ID:    "ps-test-1",
		Title: "Test Parent Session",
		Workspace: WorkspaceRef{
			RepositoryID: "repo-1",
			CheckoutID:   "checkout-main",
			RootPath:     "/workspace/test",
		},
		WriterLease: WriterLease{
			WriterAgentID: "codex-1",
			Revision:      1,
			State:         LeaseAcquired,
			AcquiredAt:    time.Now(),
		},
		Agents: map[string]*AgentBinding{
			"codex-1": {
				AgentID:        "codex-1",
				Engine:         protocol.AgentTypeCodex,
				Alias:          "codex",
				Model:          "gpt-4o",
				IsActiveWriter: true,
			},
		},
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}

	if err := store.SaveParentSession(sess); err != nil {
		t.Fatalf("failed to save session: %v", err)
	}

	loaded, err := store.GetParentSession("ps-test-1")
	if err != nil {
		t.Fatalf("failed to load session: %v", err)
	}

	if loaded.Title != sess.Title || loaded.WriterLease.WriterAgentID != "codex-1" {
		t.Errorf("loaded session mismatch: %+v", loaded)
	}
	if len(loaded.Agents) != 1 || !loaded.Agents["codex-1"].IsActiveWriter {
		t.Errorf("agent binding mismatch: %+v", loaded.Agents)
	}

	// Test Event Append & Query
	ev1 := &SessionEvent{
		ParentSessionID: "ps-test-1",
		TurnID:          "turn-1",
		OriginEngine:    protocol.AgentTypeCodex,
		RequestedBy:     "user",
		Purpose:         PurposeWork,
		EventType:       "user_message",
		Payload:         "Hello world",
	}
	if err := store.AppendEvent(ev1); err != nil {
		t.Fatalf("failed to append event 1: %v", err)
	}
	if ev1.Seq != 1 {
		t.Errorf("expected seq 1, got %d", ev1.Seq)
	}

	ev2 := &SessionEvent{
		ParentSessionID: "ps-test-1",
		TurnID:          "turn-2",
		OriginEngine:    protocol.AgentTypeCodex,
		RequestedBy:     "codex-1",
		Purpose:         PurposeWork,
		EventType:       "agent_message",
		Payload:         "I can help with that",
	}
	if err := store.AppendEvent(ev2); err != nil {
		t.Fatalf("failed to append event 2: %v", err)
	}
	if ev2.Seq != 2 {
		t.Errorf("expected seq 2, got %d", ev2.Seq)
	}

	eventsAfter0, err := store.GetEvents("ps-test-1", 0)
	if err != nil || len(eventsAfter0) != 2 {
		t.Fatalf("expected 2 events after 0, got %d (err: %v)", len(eventsAfter0), err)
	}

	eventsAfter1, err := store.GetEvents("ps-test-1", 1)
	if err != nil || len(eventsAfter1) != 1 || eventsAfter1[0].TurnID != "turn-2" {
		t.Fatalf("expected 1 event after 1, got %d (err: %v)", len(eventsAfter1), err)
	}
}

func TestStoreWriterLeaseCASConcurrency(t *testing.T) {
	store, err := NewStore(":memory:")
	if err != nil {
		t.Fatalf("failed to open memory store: %v", err)
	}
	defer store.Close()

	sess := &ParentSession{
		ID: "ps-concur",
		Workspace: WorkspaceRef{
			RootPath: "/test",
		},
		WriterLease: WriterLease{
			WriterAgentID: "agent-initial",
			Revision:      1,
			State:         LeaseAcquired,
			AcquiredAt:    time.Now(),
		},
		Agents: map[string]*AgentBinding{
			"agent-initial": {AgentID: "agent-initial", Engine: protocol.AgentTypeCodex, IsActiveWriter: true},
			"agent-a":       {AgentID: "agent-a", Engine: protocol.AgentTypeAntigravity},
			"agent-b":       {AgentID: "agent-b", Engine: protocol.AgentTypeClaudeCode},
		},
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
	if err := store.SaveParentSession(sess); err != nil {
		t.Fatalf("failed to save parent session: %v", err)
	}

	// Two agents concurrently try to acquire writer lease with expected revision 1
	var wg sync.WaitGroup
	var successCount int
	var conflictCount int
	var mu sync.Mutex

	for _, cand := range []string{"agent-a", "agent-b"} {
		wg.Add(1)
		candAgent := cand
		go func() {
			defer wg.Done()
			_, err := store.UpdateWriterLeaseCAS("ps-concur", 1, candAgent, LeaseAcquired)
			mu.Lock()
			defer mu.Unlock()
			if err == nil {
				successCount++
			} else if err == ErrWriterConflict {
				conflictCount++
			}
		}()
	}

	wg.Wait()

	if successCount != 1 || conflictCount != 1 {
		t.Fatalf("CAS race invariant broken: success=%d, conflict=%d (expected exactly 1 winner)",
			successCount, conflictCount)
	}

	// Verify resulting state
	updated, err := store.GetParentSession("ps-concur")
	if err != nil {
		t.Fatalf("failed to load session: %v", err)
	}
	if updated.WriterLease.Revision != 2 {
		t.Errorf("expected revision 2, got %d", updated.WriterLease.Revision)
	}
}
