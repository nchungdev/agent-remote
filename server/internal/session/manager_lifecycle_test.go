package session

import (
	"path/filepath"
	"testing"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

func TestValidateNativeSessionID(t *testing.T) {
	validIDs := []string{
		"session-123",
		"codex_thread_abc_99",
		"thread:12345",
		"sess.v1",
	}
	for _, id := range validIDs {
		if err := ValidateNativeSessionID(id); err != nil {
			t.Errorf("expected valid ID %s, got: %v", id, err)
		}
	}

	invalidIDs := []string{
		"../escape",
		"session*wildcard",
		"--danger-flag",
		"test/path/traversal",
		"",
	}
	for _, id := range invalidIDs {
		if err := ValidateNativeSessionID(id); err == nil {
			t.Errorf("expected rejection for invalid ID %s, got nil", id)
		}
	}
}

func TestImportNativeSessionAndDriftDetection(t *testing.T) {
	tmpDir := t.TempDir()
	store, err := NewStore(filepath.Join(tmpDir, "lifecycle.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer store.Close()

	mgr := NewManager(DefaultRegistry(), store)

	// 1. Import session
	sess, err := mgr.ImportNativeSession(
		"ps-imported",
		"Imported Codex Session",
		protocol.AgentTypeCodex,
		"thread_01jxyz99",
		WorkspaceRef{RootPath: "/test/ws"},
	)
	if err != nil {
		t.Fatalf("failed to import session: %v", err)
	}

	if sess.WriterLease.State != LeaseAcquired {
		t.Errorf("expected lease acquired on import, got %s", sess.WriterLease.State)
	}

	agentID := sess.WriterLease.WriterAgentID
	binding := sess.Agents[agentID]
	if binding.NativeSessionID != "thread_01jxyz99" {
		t.Errorf("native session ID mismatch: %s", binding.NativeSessionID)
	}

	// 2. Check no drift with same or earlier timestamp
	drift, err := mgr.CheckNativeDrift(sess.ID, agentID, binding.NativeMTime)
	if err != nil || drift {
		t.Errorf("unexpected drift detected for unchanged timestamp")
	}

	// 3. Detect drift with future timestamp (external modification outside agent-remote)
	futureTime := binding.NativeMTime.Add(10 * time.Minute)
	drift, err = mgr.CheckNativeDrift(sess.ID, agentID, futureTime)
	if err != nil || !drift {
		t.Errorf("expected drift detected for modified native session")
	}

	// Verify lease marked for verification
	refreshed, _ := mgr.GetSession(sess.ID)
	if refreshed.WriterLease.State != LeaseVerificationNeeded {
		t.Errorf("expected lease state %s, got %s", LeaseVerificationNeeded, refreshed.WriterLease.State)
	}

	// Check event logged in store
	events, _ := store.GetEvents(sess.ID, 0)
	var foundDriftEvent bool
	for _, ev := range events {
		if ev.EventType == "external_drift_detected" {
			foundDriftEvent = true
			break
		}
	}
	if !foundDriftEvent {
		t.Errorf("expected external_drift_detected event in event log")
	}
}
