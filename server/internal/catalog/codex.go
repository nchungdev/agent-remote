package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

// Codex keeps threads, projects and sections in one SQLite state db; the model list is the cache the
// CLI refreshes from the account's entitlements; pins live in the desktop app's global state file.
type Codex struct {
	home string
}

func NewCodex(home string) *Codex { return &Codex{home: home} }

func (c *Codex) Engine() Engine { return EngineCodex }

func (c *Codex) dir() string { return filepath.Join(c.home, ".codex") }

func (c *Codex) Load(ctx context.Context) EngineCatalog {
	bin := findBinary(c.home, "codex")
	out := EngineCatalog{Engine: EngineCodex, Installed: bin != "", Binary: bin}
	var errs []string

	models, err := c.models()
	out.Models = models
	if err != nil {
		errs = append(errs, "models: "+err.Error())
	}
	projects, convs, err := c.threads(ctx)
	out.Projects, out.Conversations = projects, convs
	if err != nil {
		errs = append(errs, "threads: "+err.Error())
	}
	out.Error = strings.Join(errs, "; ")
	return out
}

type codexModelsCache struct {
	Models []struct {
		Slug        string `json:"slug"`
		DisplayName string `json:"display_name"`
		Description string `json:"description"`
		Visibility  string `json:"visibility"`
		Priority    int    `json:"priority"`
		Levels      []struct {
			Effort string `json:"effort"`
		} `json:"supported_reasoning_levels"`
	} `json:"models"`
}

func (c *Codex) models() ([]Model, error) {
	raw, err := os.ReadFile(filepath.Join(c.dir(), "models_cache.json"))
	if err != nil {
		return defaultCodexModels(), nil
	}
	var cache codexModelsCache
	if err := json.Unmarshal(raw, &cache); err != nil {
		return defaultCodexModels(), nil
	}
	sort.SliceStable(cache.Models, func(i, j int) bool { return cache.Models[i].Priority < cache.Models[j].Priority })
	var out []Model
	for _, m := range cache.Models {
		if m.Visibility != "list" {
			continue
		}
		model := Model{ID: m.Slug, Label: m.DisplayName, Description: m.Description}
		for _, l := range m.Levels {
			model.Efforts = append(model.Efforts, l.Effort)
		}
		out = append(out, model)
	}
	if len(out) == 0 {
		return defaultCodexModels(), nil
	}
	return out, nil
}

func defaultCodexModels() []Model {
	return []Model{
		{ID: "o3-mini", Label: "o3-mini", Description: "Reasoning"},
		{ID: "o1", Label: "o1", Description: "Khuyên dùng"},
		{ID: "gpt-4o", Label: "GPT-4o", Description: "Omni"},
		{ID: "gpt-4o-mini", Label: "GPT-4o mini", Description: "Nhẹ"},
	}
}

var stateDBRe = regexp.MustCompile(`^state_(\d+)\.sqlite$`)

// stateDBPath picks the newest schema version (state_5.sqlite today) so a CLI upgrade needs no change.
func (c *Codex) stateDBPath() string {
	files, _ := filepath.Glob(filepath.Join(c.dir(), "state_*.sqlite"))
	best, bestVer := "", -1
	for _, f := range files {
		if m := stateDBRe.FindStringSubmatch(filepath.Base(f)); m != nil {
			if v, _ := strconv.Atoi(m[1]); v > bestVer {
				best, bestVer = f, v
			}
		}
	}
	return best
}

func (c *Codex) threads(ctx context.Context) ([]Project, []Conversation, error) {
	path := c.stateDBPath()
	if path == "" {
		return nil, nil, os.ErrNotExist
	}
	db, err := openReadOnly(path)
	if err != nil {
		return nil, nil, err
	}
	defer db.Close()

	projects, err := codexProjects(ctx, db)
	if err != nil {
		return nil, nil, err
	}
	sections := codexSections(ctx, db)
	pinned := c.globalPinned()

	// Sub-agent threads have a JSON `source`; user-visible threads are cli/vscode/exec/app sources.
	rows, err := db.QueryContext(ctx, `SELECT id, COALESCE(NULLIF(name,''), title), cwd, archived, is_pinned,
		COALESCE(thread_section_id,''), updated_at_ms
		FROM threads WHERE source NOT LIKE '{%' ORDER BY updated_at_ms DESC`)
	if err != nil {
		return projects, nil, err
	}
	defer rows.Close()

	var out []Conversation
	for rows.Next() {
		var id, title, cwd, sectionID string
		var archived, isPinned int
		var updated int64
		if rows.Scan(&id, &title, &cwd, &archived, &isPinned, &sectionID, &updated) != nil {
			continue
		}
		section := sections[sectionID]
		out = append(out, Conversation{
			ID:          id,
			Title:       clip(title, 120),
			ProjectID:   projectForCwd(projects, cwd),
			Workspace:   cwd,
			Section:     section,
			Pinned:      isPinned == 1 || pinned[id] || strings.EqualFold(section, "Pinned"),
			Archived:    archived == 1,
			UpdatedAtMs: updated,
		})
	}
	return projects, out, rows.Err()
}

func codexProjects(ctx context.Context, db *sql.DB) ([]Project, error) {
	rows, err := db.QueryContext(ctx, `SELECT p.id, p.name, COALESCE(r.path,'')
		FROM projects p LEFT JOIN project_roots r ON r.project_id = p.id
		ORDER BY p.position, r.position`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []Project
	idx := map[string]int{}
	for rows.Next() {
		var id, name, path string
		if rows.Scan(&id, &name, &path) != nil {
			continue
		}
		i, ok := idx[id]
		if !ok {
			i = len(out)
			idx[id] = i
			out = append(out, Project{ID: id, Name: name, Folders: []string{}})
		}
		if path != "" {
			out[i].Folders = append(out[i].Folders, path)
		}
	}
	return out, rows.Err()
}

func codexSections(ctx context.Context, db *sql.DB) map[string]string {
	out := map[string]string{}
	rows, err := db.QueryContext(ctx, `SELECT id, name FROM thread_sections`)
	if err != nil {
		return out
	}
	defer rows.Close()
	for rows.Next() {
		var id, name string
		if rows.Scan(&id, &name) == nil {
			out[id] = name
		}
	}
	return out
}

func (c *Codex) globalPinned() map[string]bool {
	out := map[string]bool{}
	raw, err := os.ReadFile(filepath.Join(c.dir(), ".codex-global-state.json"))
	if err != nil {
		return out
	}
	var state struct {
		Pinned []string `json:"pinned-thread-ids"`
	}
	if json.Unmarshal(raw, &state) == nil {
		for _, id := range state.Pinned {
			out[id] = true
		}
	}
	return out
}

// projectForCwd returns the project whose folder is the deepest ancestor of cwd.
func projectForCwd(projects []Project, cwd string) string {
	best, bestLen := "", -1
	for _, p := range projects {
		for _, f := range p.Folders {
			if (cwd == f || strings.HasPrefix(cwd, f+string(filepath.Separator))) && len(f) > bestLen {
				best, bestLen = p.ID, len(f)
			}
		}
	}
	return best
}
