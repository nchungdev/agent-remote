package session

import (
	"bytes"
	"context"
	"os/exec"
	"strings"
	"time"
)

// GitWorkspaceStatus captures repository context for parent session handoffs and consults.
type GitWorkspaceStatus struct {
	IsGitRepo      bool     `json:"isGitRepo"`
	RepoRoot       string   `json:"repoRoot"`
	CurrentBranch  string   `json:"currentBranch"`
	HeadCommit     string   `json:"headCommit"`
	HasUncommitted bool     `json:"hasUncommitted"`
	ModifiedFiles  []string `json:"modifiedFiles"`
	UntrackedFiles []string `json:"untrackedFiles"`
	DiffStat       string   `json:"diffStat"`
}

// CaptureGitStatus inspects the git workspace safely and deterministically without locking.
func CaptureGitStatus(dir string) (*GitWorkspaceStatus, error) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	runGit := func(args ...string) ([]byte, error) {
		baseArgs := []string{"-c", "core.fsmonitor=false"}
		cmd := exec.CommandContext(ctx, "git", append(baseArgs, args...)...)
		cmd.Dir = dir
		cmd.Env = append(cmd.Environ(), "GIT_OPTIONAL_LOCKS=0")
		return cmd.Output()
	}

	// 1. Check if git repo and locate top-level root
	topLevel, err := runGit("rev-parse", "--show-toplevel")
	if err != nil {
		return &GitWorkspaceStatus{IsGitRepo: false}, nil
	}
	repoRoot := strings.TrimSpace(string(topLevel))

	// 2. Read HEAD commit and branch
	headOut, _ := runGit("rev-parse", "HEAD")
	headCommit := strings.TrimSpace(string(headOut))

	branchOut, _ := runGit("rev-parse", "--abbrev-ref", "HEAD")
	currentBranch := strings.TrimSpace(string(branchOut))

	// 3. Read status --porcelain -z for whitespace and rename resilience
	statusOut, err := runGit("status", "--porcelain", "-z", "--untracked-files=all")
	if err != nil {
		return &GitWorkspaceStatus{
			IsGitRepo:     true,
			RepoRoot:      repoRoot,
			CurrentBranch: currentBranch,
			HeadCommit:    headCommit,
		}, nil
	}

	var modifiedFiles []string
	var untrackedFiles []string

	entries := bytes.Split(statusOut, []byte{0})
	for _, entry := range entries {
		if len(entry) < 3 {
			continue
		}
		statusCodes := string(entry[0:2])
		filePath := string(entry[3:])
		if statusCodes == "??" {
			untrackedFiles = append(untrackedFiles, filePath)
		} else {
			modifiedFiles = append(modifiedFiles, filePath)
		}
	}

	// 4. Read diffstat compared to HEAD (including staged)
	diffStatOut, _ := runGit("diff", "HEAD", "--stat")

	return &GitWorkspaceStatus{
		IsGitRepo:      true,
		RepoRoot:       repoRoot,
		CurrentBranch:  currentBranch,
		HeadCommit:     headCommit,
		HasUncommitted: len(modifiedFiles) > 0 || len(untrackedFiles) > 0,
		ModifiedFiles:  modifiedFiles,
		UntrackedFiles: untrackedFiles,
		DiffStat:       strings.TrimSpace(string(diffStatOut)),
	}, nil
}
