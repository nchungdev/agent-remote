package session

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

func TestWorktreeLifecycleAndIsolation(t *testing.T) {
	tmpDir := t.TempDir()
	sourceRepo := filepath.Join(tmpDir, "source_repo")
	managedRoot := filepath.Join(tmpDir, "worktrees")

	if err := os.MkdirAll(sourceRepo, 0755); err != nil {
		t.Fatal(err)
	}

	run := func(args ...string) {
		cmd := exec.Command("git", args...)
		cmd.Dir = sourceRepo
		if out, err := cmd.CombinedOutput(); err != nil {
			t.Fatalf("git %v failed: %v (%s)", args, err, out)
		}
	}

	run("init")
	run("config", "user.name", "Test User")
	run("config", "user.email", "test@example.com")

	// Commit initial file
	fileA := filepath.Join(sourceRepo, "base.txt")
	if err := os.WriteFile(fileA, []byte("v1.0\n"), 0644); err != nil {
		t.Fatal(err)
	}
	run("add", "base.txt")
	run("commit", "-m", "first commit")

	// Make uncommitted changes in source repo (staged & untracked)
	untrackedFile := filepath.Join(sourceRepo, "dirty_scratch.tmp")
	_ = os.WriteFile(untrackedFile, []byte("scratch\n"), 0644)

	mgr := NewWorktreeManager(managedRoot)

	// 1. Create worktree
	wsRef, err := mgr.CreateWorktree(context.Background(), sourceRepo, "HEAD", "task-abc-123")
	if err != nil {
		t.Fatalf("failed create worktree: %v", err)
	}

	if wsRef.CheckoutID != "task-abc-123" {
		t.Errorf("checkout ID mismatch: %s", wsRef.CheckoutID)
	}

	// 2. Verify isolation: untrackedFile from source repo must NOT exist in the new worktree!
	worktreeScratch := filepath.Join(wsRef.RootPath, "dirty_scratch.tmp")
	if _, err := os.Stat(worktreeScratch); err == nil {
		t.Errorf("untracked file leaked into new worktree: %s", worktreeScratch)
	}

	// Verify base.txt exists in the new worktree
	worktreeBase := filepath.Join(wsRef.RootPath, "base.txt")
	if _, err := os.Stat(worktreeBase); err != nil {
		t.Errorf("base.txt missing from worktree: %v", err)
	}

	// 3. Retry with same taskID (must be idempotent)
	wsRef2, err := mgr.CreateWorktree(context.Background(), sourceRepo, "HEAD", "task-abc-123")
	if err != nil {
		t.Fatalf("retry create worktree failed: %v", err)
	}
	if wsRef2.RootPath != wsRef.RootPath {
		t.Errorf("idempotency broken, got different root: %s vs %s", wsRef2.RootPath, wsRef.RootPath)
	}

	// 4. Remove worktree
	if err := mgr.RemoveWorktree(context.Background(), sourceRepo, wsRef.RootPath); err != nil {
		t.Fatalf("failed to remove worktree: %v", err)
	}

	if _, err := os.Stat(wsRef.RootPath); err == nil {
		t.Errorf("worktree dir was not removed: %s", wsRef.RootPath)
	}
}
