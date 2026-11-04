package session

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

var (
	ErrInvalidWorktreeTarget = errors.New("invalid worktree destination: path must be inside managed root")
	ErrBaseCommitNotFound    = errors.New("base commit not found in repository")
)

// WorktreeManager provisions isolated checkouts from base commits (T3.6a, T3.6b).
type WorktreeManager struct {
	managedRoot string
}

func NewWorktreeManager(managedRoot string) *WorktreeManager {
	return &WorktreeManager{managedRoot: managedRoot}
}

// CreateWorktree creates an isolated git worktree for a session task.
func (w *WorktreeManager) CreateWorktree(
	ctx context.Context,
	repoPath string,
	baseCommit string,
	taskID string,
) (*WorkspaceRef, error) {
	runGit := func(args ...string) ([]byte, error) {
		cmdCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
		defer cancel()
		cmd := exec.CommandContext(cmdCtx, "git", args...)
		cmd.Dir = repoPath
		cmd.Env = append(cmd.Environ(), "GIT_OPTIONAL_LOCKS=0")
		return cmd.CombinedOutput()
	}

	// 1. Resolve and pin full SHA
	revOut, err := runGit("rev-parse", "--verify", baseCommit+"^{commit}")
	if err != nil {
		return nil, fmt.Errorf("%w: %s (%v)", ErrBaseCommitNotFound, baseCommit, string(revOut))
	}
	pinnedSHA := strings.TrimSpace(string(revOut))

	// 2. Define target directory inside managed root
	safeTaskID := strings.ReplaceAll(taskID, "/", "-")
	targetDir := filepath.Join(w.managedRoot, safeTaskID)
	branchName := fmt.Sprintf("remote/%s", safeTaskID)

	// Idempotency: if target directory already exists, check if it's the right worktree
	if _, err := os.Stat(targetDir); err == nil {
		checkOut, checkErr := runGit("worktree", "list", "--porcelain")
		if checkErr == nil && strings.Contains(string(checkOut), targetDir) {
			return &WorkspaceRef{
				RepositoryID: repoPath,
				CheckoutID:   taskID,
				RootPath:     targetDir,
			}, nil
		}
	}

	if err := os.MkdirAll(filepath.Dir(targetDir), 0755); err != nil {
		return nil, fmt.Errorf("failed to create parent dir: %w", err)
	}

	// 3. Execute git worktree add -b <branch> <path> <base-sha>
	addOut, err := runGit("worktree", "add", "-b", branchName, targetDir, pinnedSHA)
	if err != nil {
		// Retry without -b if branch already exists from earlier attempt
		if strings.Contains(string(addOut), "already exists") {
			addOut2, err2 := runGit("worktree", "add", targetDir, branchName)
			if err2 != nil {
				return nil, fmt.Errorf("git worktree add failed: %s", string(addOut2))
			}
		} else {
			return nil, fmt.Errorf("git worktree add failed: %s", string(addOut))
		}
	}

	// 4. Verify resulting root and HEAD
	st, err := CaptureGitStatus(targetDir)
	if err != nil || !st.IsGitRepo {
		_ = os.RemoveAll(targetDir)
		return nil, fmt.Errorf("created worktree verification failed at %s", targetDir)
	}

	return &WorkspaceRef{
		RepositoryID: repoPath,
		CheckoutID:   taskID,
		RootPath:     targetDir,
	}, nil
}

// RemoveWorktree prunes and cleans an isolated worktree after completion (T3.6b).
func (w *WorktreeManager) RemoveWorktree(ctx context.Context, repoPath string, targetDir string) error {
	cmdCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	cmd := exec.CommandContext(cmdCtx, "git", "worktree", "remove", "--force", targetDir)
	cmd.Dir = repoPath
	cmd.Env = append(cmd.Environ(), "GIT_OPTIONAL_LOCKS=0")
	if out, err := cmd.CombinedOutput(); err != nil {
		// Clean up residual directory if git prune failed
		_ = os.RemoveAll(targetDir)
		return fmt.Errorf("git worktree remove failed: %s (%w)", string(out), err)
	}
	return nil
}
