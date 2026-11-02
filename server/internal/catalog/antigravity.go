package catalog

import (
	"context"
	"encoding/json"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

// Antigravity keeps conversations in one SQLite summary db (shared by the IDE app and the agy CLI),
// project definitions as JSON files, and user-edited titles as protobuf-text annotations.
type Antigravity struct {
	home   string
	models *agyModels
}

func NewAntigravity(home string) *Antigravity {
	return &Antigravity{home: home, models: &agyModels{}}
}

func (a *Antigravity) Engine() Engine { return EngineAntigravity }

func (a *Antigravity) root() string { return filepath.Join(a.home, ".gemini", "antigravity") }

func (a *Antigravity) Load(ctx context.Context) EngineCatalog {
	bin := findBinary(a.home, "agy")
	out := EngineCatalog{Engine: EngineAntigravity, Installed: bin != "", Binary: bin}
	var errs []string

	if bin != "" {
		models, err := a.models.get(ctx, bin)
		out.Models = models
		if err != nil {
			errs = append(errs, "models: "+err.Error())
		}
	}
	if len(out.Models) == 0 {
		out.Models = defaultAntigravityModels()
	}
	convs, err := a.conversations(ctx)
	out.Conversations = convs
	if err != nil {
		errs = append(errs, "conversations: "+err.Error())
	}
	out.Projects = a.projects(convs)
	out.Error = strings.Join(errs, "; ")
	return out
}

func defaultAntigravityModels() []Model {
	return []Model{
		{ID: "gemini-2.5-pro", Label: "Gemini 2.5 Pro", Description: "Khuyên dùng"},
		{ID: "gemini-2.5-flash", Label: "Gemini 2.5 Flash", Description: "Siêu tốc"},
		{ID: "gemini-2.0-flash-thinking", Label: "Gemini 2.0 Flash Thinking", Description: "Thinking"},
		{ID: "claude-3.7-sonnet", Label: "Claude 3.7 Sonnet", Description: "Hybrid"},
	}
}

func (a *Antigravity) conversations(ctx context.Context) ([]Conversation, error) {
	db, err := openReadOnly(filepath.Join(a.root(), "conversation_summaries.db"))
	if err != nil {
		return nil, err
	}
	defer db.Close()

	// Sub-agent conversations (parent set) are internal to a parent run, not user-visible threads.
	rows, err := db.QueryContext(ctx, `SELECT conversation_id, title, project_id, workspace_uris,
		CAST(last_modified_time AS TEXT), status, step_count
		FROM conversation_summaries
		WHERE parent_conversation_id = '' AND killed = 0
		ORDER BY last_modified_time DESC`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	titles := a.annotationTitles()
	var out []Conversation
	for rows.Next() {
		var id, title, project, uris, modified, status string
		var steps int
		if rows.Scan(&id, &title, &project, &uris, &modified, &status, &steps) != nil {
			continue
		}
		if t, ok := titles[id]; ok {
			title = t
		}
		if strings.TrimSpace(title) == "" && steps == 0 {
			continue
		}
		if strings.TrimSpace(title) == "" {
			title = "Untitled"
		}
		out = append(out, Conversation{
			ID:          id,
			Title:       clip(title, 120),
			ProjectID:   project,
			Workspace:   firstWorkspace(uris),
			Running:     status == "CASCADE_RUN_STATUS_RUNNING",
			UpdatedAtMs: parseDBTime(modified).UnixMilli(),
		})
	}
	return out, rows.Err()
}

type agyProjectFile struct {
	ID               string `json:"id"`
	Name             string `json:"name"`
	ProjectResources struct {
		Resources []struct {
			FolderURI string `json:"folderUri"`
			GitFolder struct {
				FolderURI string `json:"folderUri"`
			} `json:"gitFolder"`
		} `json:"resources"`
	} `json:"projectResources"`
}

// projects lists project definitions in the order the Antigravity app shows them. Projects with no
// folder and no conversation (plugin scratch projects) are noise and are dropped.
func (a *Antigravity) projects(convs []Conversation) []Project {
	used := map[string]bool{}
	for _, c := range convs {
		used[c.ProjectID] = true
	}
	files, _ := filepath.Glob(filepath.Join(a.home, ".gemini", "config", "projects", "*.json"))
	byID := map[string]Project{}
	for _, f := range files {
		raw, err := os.ReadFile(f)
		if err != nil {
			continue
		}
		var pf agyProjectFile
		if json.Unmarshal(raw, &pf) != nil || pf.ID == "" {
			continue
		}
		p := Project{ID: pf.ID, Name: pf.Name, Folders: []string{}}
		for _, r := range pf.ProjectResources.Resources {
			uri := r.FolderURI
			if uri == "" {
				uri = r.GitFolder.FolderURI
			}
			if path := uriToPath(uri); path != "" {
				p.Folders = append(p.Folders, path)
			}
		}
		if len(p.Folders) > 0 || used[p.ID] {
			byID[p.ID] = p
		}
	}
	return orderProjects(byID, a.projectsOrder())
}

func (a *Antigravity) projectsOrder() []string {
	path := filepath.Join(a.home, "Library", "Application Support", "Antigravity", "app_storage.json")
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	var storage map[string]json.RawMessage
	if json.Unmarshal(raw, &storage) != nil {
		return nil
	}
	var encoded string
	if json.Unmarshal(storage["projectsOrder"], &encoded) != nil {
		return nil
	}
	var order []string
	_ = json.Unmarshal([]byte(encoded), &order)
	return order
}

func orderProjects(byID map[string]Project, order []string) []Project {
	out := make([]Project, 0, len(byID))
	for _, id := range order {
		if p, ok := byID[id]; ok {
			out = append(out, p)
			delete(byID, id)
		}
	}
	rest := make([]Project, 0, len(byID))
	for _, p := range byID {
		rest = append(rest, p)
	}
	sort.Slice(rest, func(i, j int) bool { return rest[i].Name < rest[j].Name })
	return append(out, rest...)
}

var annotationTitleRe = regexp.MustCompile(`title:\s*"((?:[^"\\]|\\.)*)"`)

// annotationTitles reads titles the user (or the app) set; they override the summary db title.
func (a *Antigravity) annotationTitles() map[string]string {
	files, _ := filepath.Glob(filepath.Join(a.root(), "annotations", "*.pbtxt"))
	out := make(map[string]string, len(files))
	for _, f := range files {
		raw, err := os.ReadFile(f)
		if err != nil {
			continue
		}
		if m := annotationTitleRe.FindSubmatch(raw); m != nil {
			if t := strings.TrimSpace(unescapeProtoText(string(m[1]))); t != "" {
				out[strings.TrimSuffix(filepath.Base(f), ".pbtxt")] = t
			}
		}
	}
	return out
}

// unescapeProtoText decodes protobuf text-format escapes, including the octal bytes used for UTF-8.
func unescapeProtoText(s string) string {
	var b strings.Builder
	for i := 0; i < len(s); i++ {
		if s[i] != '\\' || i+1 >= len(s) {
			b.WriteByte(s[i])
			continue
		}
		i++
		switch c := s[i]; {
		case c >= '0' && c <= '7':
			end := i + 1
			for end < len(s) && end < i+3 && s[end] >= '0' && s[end] <= '7' {
				end++
			}
			n, _ := strconv.ParseUint(s[i:end], 8, 8)
			b.WriteByte(byte(n))
			i = end - 1
		case c == 'n':
			b.WriteByte('\n')
		case c == 't':
			b.WriteByte('\t')
		default:
			b.WriteByte(c)
		}
	}
	return b.String()
}

func firstWorkspace(uris string) string {
	var list []string
	if json.Unmarshal([]byte(uris), &list) != nil {
		return ""
	}
	for _, u := range list {
		if p := uriToPath(u); p != "" {
			return p
		}
	}
	return ""
}

func uriToPath(raw string) string {
	u, err := url.Parse(raw)
	if err != nil || u.Scheme != "file" {
		return ""
	}
	return filepath.Clean(u.Path)
}

func parseDBTime(s string) time.Time {
	for _, layout := range []string{"2006-01-02 15:04:05.999999999-07:00", time.RFC3339Nano, "2006-01-02 15:04:05"} {
		if t, err := time.Parse(layout, s); err == nil {
			return t
		}
	}
	return time.Time{}
}
