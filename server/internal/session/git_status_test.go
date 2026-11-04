package session

import (
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

func TestCaptureGitStatusNonRepo(t *testing.T) {
	tmpDir := t.TempDir()
	st, err := CaptureGitStatus(tmpDir)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if st.IsGitRepo {
		t.Errorf("expected IsGitRepo to be false for non-git dir")
	}
}

func TestCaptureGitStatusRealRepo(t *testing.T) {
	tmpDir := t.TempDir()

	run := func(args ...string) {
		cmd := exec.Command("git", args...)
		cmd.Dir = tmpDir
		if out, err := cmd.CombinedOutput(); err != nil {
			t.Fatalf("git %v failed: %v (%s)", args, err, out)
		}
	}

	run("init")
	run("config", "user.name", "Test User")
	run("config", "user.email", "test@example.com")

	// Commit initial file
	fileA := filepath.Join(tmpDir, "file a.txt")
	if err := os.WriteFile(fileA, []byte("line1\n"), 0644); err != nil {
		t.Fatal(err)
	}
	run("add", "file a.txt")
	run("commit", "-m", "initial commit")

	// Test clean repo
	st, err := CaptureGitStatus(tmpDir)
	if err != nil || !st.IsGitRepo {
		t.Fatalf("failed capture clean repo: %v", err)
	}
	if st.HasUncommitted {
		t.Errorf("expected no uncommitted changes on clean repo")
	}

	// Create subdirectory and test from inside subfolder
	subDir := filepath.Join(tmpDir, "src", "nested")
	if err := os.MkdirAll(subDir, 0755); err != nil {
		t.Fatal(err)
	}

	// Modify file with space in path
	if err := os.WriteFile(fileA, []byte("line1\nmodified line2\n"), 0644); err != nil {
		t.Fatal(err)
	}

	// Add untracked file
	fileB := filepath.Join(subDir, "new file.go")
	if err := os.WriteFile(fileB, []byte("package nested\n"), 0644); err != nil {
		t.Fatal(err)
	}

	// Run status from subDir
	stSub, err := CaptureGitStatus(subDir)
	if err != nil || !stSub.IsGitRepo {
		t.Fatalf("failed capture from subDir: %v", err)
	}
	if !stSub.HasUncommitted {
		t.Errorf("expected uncommitted changes")
	}
	if len(stSub.ModifiedFiles) == 0 {
		t.Errorf("expected modified file 'file a.txt'")
	}
	if len(stSub.UntrackedFiles) == 0 {
		t.Errorf("expected untracked file in nested dir")
	}
}
