package catalog

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"
)

const (
	claudeHeadValues = 80
	claudeHeadBytes  = 8 << 20
	claudeTailBytes  = 256 << 10
)

// Claude Code has no index database: every session is a transcript file under
// ~/.claude/projects/<slug>/<session-id>.jsonl, and a project is the working directory (`cwd`).
type Claude struct {
	home  string
	mu    sync.Mutex
	cache map[string]cachedSession // keyed by file path; valid while size+mtime are unchanged
}

type cachedSession struct {
	size  int64
	mtime time.Time
	conv  Conversation
	ok    bool
}

func NewClaude(home string) *Claude {
	return &Claude{home: home, cache: map[string]cachedSession{}}
}

func (c *Claude) Engine() Engine { return EngineClaudeCode }

func (c *Claude) Load(ctx context.Context) EngineCatalog {
	bin := findBinary(c.home, "claude")
	convs := c.conversations(ctx)
	return EngineCatalog{
		Engine:        EngineClaudeCode,
		Installed:     bin != "",
		Binary:        bin,
		Models:        c.models(),
		Projects:      claudeProjects(convs),
		Conversations: convs,
	}
}

// models returns Claude Code's own aliases, which always resolve to the newest model of that family
// (so they never go stale), plus any extra models the CLI cached for this account.
func (c *Claude) models() []Model {
	out := []Model{
		{ID: "claude-3-7-sonnet-thinking", Label: "Claude 3.7 Sonnet Thinking", Description: "Thinking"},
		{ID: "claude-3-7-sonnet", Label: "Claude 3.7 Sonnet", Description: "Mặc định (Sonnet)"},
		{ID: "claude-3-5-sonnet", Label: "Claude 3.5 Sonnet", Description: "Ổn định"},
		{ID: "claude-3-5-haiku", Label: "Claude 3.5 Haiku", Description: "Nhanh (Haiku)"},
		{ID: "claude-3-opus", Label: "Claude 3 Opus", Description: "Opus"},
	}
	raw, err := os.ReadFile(filepath.Join(c.home, ".claude.json"))
	if err != nil {
		return out
	}
	var cfg struct {
		Extra []struct {
			Value       string `json:"value"`
			Label       string `json:"label"`
			Description string `json:"description"`
		} `json:"additionalModelOptionsCache"`
	}
	if json.Unmarshal(raw, &cfg) != nil {
		return out
	}
	for _, m := range cfg.Extra {
		out = append(out, Model{ID: m.Value, Label: m.Label, Description: m.Description})
	}
	return out
}

func (c *Claude) conversations(ctx context.Context) []Conversation {
	files, _ := filepath.Glob(filepath.Join(c.home, ".claude", "projects", "*", "*.jsonl"))
	var out []Conversation
	for _, f := range files {
		if ctx.Err() != nil {
			break
		}
		if conv, ok := c.session(f); ok {
			out = append(out, conv)
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].UpdatedAtMs > out[j].UpdatedAtMs })
	return out
}

func (c *Claude) session(path string) (Conversation, bool) {
	st, err := os.Stat(path)
	if err != nil {
		return Conversation{}, false
	}
	c.mu.Lock()
	hit, found := c.cache[path]
	c.mu.Unlock()
	if found && hit.size == st.Size() && hit.mtime.Equal(st.ModTime()) {
		return hit.conv, hit.ok
	}

	conv, ok := readClaudeSession(path, st.Size())
	conv.UpdatedAtMs = st.ModTime().UnixMilli()
	c.mu.Lock()
	c.cache[path] = cachedSession{size: st.Size(), mtime: st.ModTime(), conv: conv, ok: ok}
	c.mu.Unlock()
	return conv, ok
}

type claudeLine struct {
	Type    string          `json:"type"`
	Cwd     string          `json:"cwd"`
	IsMeta  bool            `json:"isMeta"`
	Message json.RawMessage `json:"message"`
}

// readClaudeSession streams JSON values from the head of the transcript (lines can be megabytes
// because of inlined images, so a line scanner is unsafe) and takes a rename from the tail.
func readClaudeSession(path string, size int64) (Conversation, bool) {
	f, err := os.Open(path)
	if err != nil {
		return Conversation{}, false
	}
	defer f.Close()

	var title, cwd string
	dec := json.NewDecoder(io.LimitReader(f, claudeHeadBytes))
	for i := 0; i < claudeHeadValues && (title == "" || cwd == ""); i++ {
		var line claudeLine
		if err := dec.Decode(&line); err != nil {
			break
		}
		if cwd == "" {
			cwd = line.Cwd
		}
		if title == "" && line.Type == "user" && !line.IsMeta {
			title = firstUserText(line.Message)
		}
	}
	if renamed := lastCustomTitle(f, size); renamed != "" {
		title = renamed
	}
	if title == "" {
		return Conversation{}, false
	}
	return Conversation{
		ID:        strings.TrimSuffix(filepath.Base(path), ".jsonl"),
		Title:     clip(title, 120),
		ProjectID: cwd,
		Workspace: cwd,
	}, true
}

// firstUserText returns the human-typed text of a user message, ignoring slash-command wrappers and
// tool results.
func firstUserText(raw json.RawMessage) string {
	var msg struct {
		Content json.RawMessage `json:"content"`
	}
	if json.Unmarshal(raw, &msg) != nil {
		return ""
	}
	var text string
	if json.Unmarshal(msg.Content, &text) != nil {
		var blocks []struct {
			Type string `json:"type"`
			Text string `json:"text"`
		}
		if json.Unmarshal(msg.Content, &blocks) != nil {
			return ""
		}
		for _, b := range blocks {
			if b.Type == "text" {
				text += b.Text + "\n"
			}
		}
	}
	text = strings.TrimSpace(text)
	if strings.HasPrefix(text, "<command-") || strings.HasPrefix(text, "<local-command") {
		return ""
	}
	return text
}

var customTitleRe = regexp.MustCompile(`"customTitle":"((?:[^"\\]|\\.)*)"`)

// lastCustomTitle finds the most recent rename; Claude appends `custom-title` records at the end.
func lastCustomTitle(f *os.File, size int64) string {
	start := max(size-claudeTailBytes, 0)
	buf := make([]byte, size-start)
	if _, err := f.ReadAt(buf, start); err != nil && !errors.Is(err, io.EOF) {
		return ""
	}
	all := customTitleRe.FindAllSubmatch(bytes.TrimSpace(buf), -1)
	if len(all) == 0 {
		return ""
	}
	var title string
	if json.Unmarshal([]byte(`"`+string(all[len(all)-1][1])+`"`), &title) != nil {
		return ""
	}
	return strings.TrimSpace(title)
}

// claudeProjects derives projects from the working directories that have sessions.
func claudeProjects(convs []Conversation) []Project {
	var out []Project
	seen := map[string]bool{}
	for _, c := range convs {
		if c.ProjectID == "" || seen[c.ProjectID] {
			continue
		}
		seen[c.ProjectID] = true
		out = append(out, Project{ID: c.ProjectID, Name: filepath.Base(c.ProjectID), Folders: []string{c.ProjectID}})
	}
	return out
}
