package catalog

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"sync"
	"time"
)

const (
	loadTimeout  = 20 * time.Second
	DefaultLimit = 60
)

type Service struct {
	home      string
	providers []Provider
}

func NewService(home string, providers ...Provider) *Service {
	return &Service{home: home, providers: providers}
}

// NewDefaultService wires the four supported engines against the current user's home directory.
func NewDefaultService() *Service {
	home, _ := os.UserHomeDir()
	return NewService(home, NewAntigravity(home), NewClaude(home), NewCodex(home), NewMockProvider())
}

// Stream loads every engine in parallel and hands each result to emit as soon as it is ready,
// so a slow engine (network model fetch) never delays the others. emit may be called concurrently.
func (s *Service) Stream(ctx context.Context, limit int, emit func(EngineCatalog)) {
	if limit <= 0 {
		limit = DefaultLimit
	}
	var wg sync.WaitGroup
	for _, p := range s.providers {
		wg.Add(1)
		go func(p Provider) {
			defer wg.Done()
			cctx, cancel := context.WithTimeout(ctx, loadTimeout)
			defer cancel()
			emit(capConversations(p.Load(cctx), limit))
		}(p)
	}
	wg.Wait()
}

// capConversations keeps pinned and running conversations plus the newest others, so one catalog
// message stays small enough for a WebRTC DataChannel frame. Total keeps the real count.
func capConversations(c EngineCatalog, limit int) EngineCatalog {
	c.Total = len(c.Conversations)
	sort.SliceStable(c.Conversations, func(i, j int) bool {
		a, b := c.Conversations[i], c.Conversations[j]
		if a.Pinned != b.Pinned {
			return a.Pinned
		}
		return a.UpdatedAtMs > b.UpdatedAtMs
	})
	kept := c.Conversations[:0:0]
	for _, conv := range c.Conversations {
		if len(kept) < limit || conv.Pinned || conv.Running {
			kept = append(kept, conv)
		}
	}
	c.Conversations = kept
	return c
}

// FindBinary locates an agent CLI the same way the catalog does.
func FindBinary(home, name string) string {
	return findBinary(home, name)
}

// findBinary returns the first existing executable: PATH first, then well-known install dirs
// (a menu-bar app does not inherit the shell PATH).
func findBinary(home, name string, extraDirs ...string) string {
	if name == "codex" {
		appCodex := "/Applications/ChatGPT.app/Contents/Resources/codex-cli/CodexCLI.app/Contents/MacOS/codex"
		if st, err := os.Stat(appCodex); err == nil && !st.IsDir() && st.Mode()&0o111 != 0 {
			return appCodex
		}
	}
	if name == "claude" {
		claudeGlob := filepath.Join(home, "Library", "Application Support", "Claude", "claude-code", "*", "*", "claude.app", "Contents", "MacOS", "claude")
		if matches, _ := filepath.Glob(claudeGlob); len(matches) > 0 {
			sort.Sort(sort.Reverse(sort.StringSlice(matches)))
			return matches[0]
		}
	}

	if p, err := exec.LookPath(name); err == nil {
		return p
	}
	dirs := append([]string{
		filepath.Join(home, ".local", "bin"),
		filepath.Join(home, ".claude", "local"),
		"/opt/homebrew/bin",
		"/usr/local/bin",
	}, extraDirs...)
	for _, d := range dirs {
		p := filepath.Join(d, name)
		if st, err := os.Stat(p); err == nil && !st.IsDir() && st.Mode()&0o111 != 0 {
			return p
		}
	}
	return ""
}
