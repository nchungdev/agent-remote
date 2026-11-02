package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func write(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}

func TestParseAgyModelsSkipsProgressLine(t *testing.T) {
	got := parseAgyModels("Fetching available models...\ngemini-4-argon-high\tGemini 4 Argon (High)\n")
	if len(got) != 1 || got[0].ID != "gemini-4-argon-high" || got[0].Label != "Gemini 4 Argon (High)" {
		t.Fatalf("got %+v", got)
	}
}

func TestUnescapeProtoTextDecodesOctalUTF8(t *testing.T) {
	if got := unescapeProtoText(`Ti\341\272\277ng \"x\"`); got != "Tiếng \"x\"" {
		t.Fatalf("got %q", got)
	}
}

func TestClaudeSessionTitleAndRename(t *testing.T) {
	home := t.TempDir()
	proj := filepath.Join(home, ".claude", "projects", "-w-app")
	write(t, filepath.Join(proj, "s1.jsonl"),
		`{"type":"queue-operation"}`+"\n"+
			`{"type":"user","cwd":"/w/app","message":{"role":"user","content":"<command-name>/clear</command-name>"}}`+"\n"+
			`{"type":"user","cwd":"/w/app","message":{"role":"user","content":[{"type":"text","text":"fix the login bug"}]}}`+"\n")
	write(t, filepath.Join(proj, "s2.jsonl"),
		`{"type":"user","cwd":"/w/app","message":{"role":"user","content":"hello"}}`+"\n"+
			`{"type":"custom-title","customTitle":"Renamed \"x\"","sessionId":"s2"}`+"\n")
	write(t, filepath.Join(proj, "empty.jsonl"), `{"type":"queue-operation"}`+"\n")

	cat := NewClaude(home).Load(context.Background())
	titles := map[string]string{}
	for _, c := range cat.Conversations {
		titles[c.ID] = c.Title
	}
	if len(titles) != 2 || titles["s1"] != "fix the login bug" || titles["s2"] != `Renamed "x"` {
		t.Fatalf("titles = %v", titles)
	}
	if len(cat.Projects) != 1 || cat.Projects[0].ID != "/w/app" || cat.Projects[0].Name != "app" {
		t.Fatalf("projects = %+v", cat.Projects)
	}
}

func TestCodexModelsAndThreads(t *testing.T) {
	home := t.TempDir()
	dir := filepath.Join(home, ".codex")
	write(t, filepath.Join(dir, "models_cache.json"), `{"models":[
		{"slug":"b","display_name":"B","visibility":"list","priority":2,"supported_reasoning_levels":[{"effort":"low"},{"effort":"high"}]},
		{"slug":"a","display_name":"A","visibility":"list","priority":1},
		{"slug":"h","display_name":"H","visibility":"hide","priority":0}]}`)
	write(t, filepath.Join(dir, ".codex-global-state.json"), `{"pinned-thread-ids":["t2"]}`)

	db, err := sql.Open("sqlite", filepath.Join(dir, "state_5.sqlite"))
	if err != nil {
		t.Fatal(err)
	}
	for _, q := range []string{
		`CREATE TABLE projects (id TEXT, name TEXT, position INTEGER)`,
		`CREATE TABLE project_roots (project_id TEXT, position INTEGER, path TEXT)`,
		`CREATE TABLE thread_sections (id TEXT, name TEXT)`,
		`CREATE TABLE threads (id TEXT, name TEXT, title TEXT, cwd TEXT, archived INTEGER, is_pinned INTEGER,
			thread_section_id TEXT, updated_at_ms INTEGER, source TEXT)`,
		`INSERT INTO projects VALUES ('p1','App',0),('p2','Deep',1)`,
		`INSERT INTO project_roots VALUES ('p1',0,'/w/app'),('p2',0,'/w/app/deep')`,
		`INSERT INTO thread_sections VALUES ('s1','Pinned')`,
		`INSERT INTO threads VALUES ('t1','Named','raw','/w/app',0,0,NULL,100,'vscode')`,
		`INSERT INTO threads VALUES ('t2',NULL,'Raw title','/w/app/deep/x',0,0,NULL,200,'cli')`,
		`INSERT INTO threads VALUES ('t3','Sect','x','/w/app',0,0,'s1',300,'vscode')`,
		`INSERT INTO threads VALUES ('sub','Sub','x','/w/app',0,0,NULL,400,'{"subagent":{}}')`,
	} {
		if _, err := db.Exec(q); err != nil {
			t.Fatalf("%s: %v", q, err)
		}
	}
	db.Close()

	cat := NewCodex(home).Load(context.Background())
	if len(cat.Models) != 2 || cat.Models[0].ID != "a" || cat.Models[1].Efforts[1] != "high" {
		t.Fatalf("models = %+v", cat.Models)
	}
	byID := map[string]Conversation{}
	for _, c := range cat.Conversations {
		byID[c.ID] = c
	}
	if _, ok := byID["sub"]; ok || len(byID) != 3 {
		t.Fatalf("sub-agent thread leaked: %v", byID)
	}
	if byID["t1"].Title != "Named" || byID["t2"].Title != "Raw title" {
		t.Fatalf("titles: %+v %+v", byID["t1"], byID["t2"])
	}
	if byID["t2"].ProjectID != "p2" || byID["t1"].ProjectID != "p1" {
		t.Fatalf("deepest project must win: %+v %+v", byID["t1"], byID["t2"])
	}
	if !byID["t2"].Pinned || !byID["t3"].Pinned || byID["t1"].Pinned {
		t.Fatalf("pins: %+v", byID)
	}
}

func TestCapKeepsAllPinnedEvenBeyondLimit(t *testing.T) {
	c := EngineCatalog{Conversations: []Conversation{
		{ID: "p-old", Pinned: true, UpdatedAtMs: 1},
		{ID: "p-new", Pinned: true, UpdatedAtMs: 8},
		{ID: "new", UpdatedAtMs: 9},
		{ID: "old", UpdatedAtMs: 2},
	}}
	got := capConversations(c, 1)
	ids := []string{}
	for _, x := range got.Conversations {
		ids = append(ids, x.ID)
	}
	if got.Total != 4 || len(ids) != 2 || ids[0] != "p-new" || ids[1] != "p-old" {
		t.Fatalf("total=%d ids=%v", got.Total, ids)
	}
}

// TestLiveHome prints the real catalog of this machine: CATALOG_LIVE=1 go test -run LiveHome -v
func TestLiveHome(t *testing.T) {
	if os.Getenv("CATALOG_LIVE") == "" {
		t.Skip("set CATALOG_LIVE=1")
	}
	NewDefaultService().Stream(context.Background(), 5, func(c EngineCatalog) {
		b, _ := json.MarshalIndent(c, "", " ")
		t.Logf("%s", b)
	})
}
