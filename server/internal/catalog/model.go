// Package catalog reads what each coding engine itself knows: models, projects and conversations.
// Nothing here is hardcoded; every value comes from the engine's CLI or its on-disk store.
package catalog

import "context"

// Engine ids match protocol.AgentType values so the app can use them as-is.
type Engine string

const (
	EngineAntigravity Engine = "ANTIGRAVITY"
	EngineClaudeCode  Engine = "CLAUDE_CODE"
	EngineCodex       Engine = "CODEX"
	EngineMock        Engine = "MOCK"
)

type Model struct {
	ID          string   `json:"id"`
	Label       string   `json:"label"`
	Description string   `json:"description,omitempty"`
	Efforts     []string `json:"efforts,omitempty"`
}

type Project struct {
	ID      string   `json:"id"`
	Name    string   `json:"name"`
	Folders []string `json:"folders"`
}

type Conversation struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	ProjectID   string `json:"projectId,omitempty"`
	Workspace   string `json:"workspace,omitempty"`
	Section     string `json:"section,omitempty"`
	Pinned      bool   `json:"pinned"`
	Archived    bool   `json:"archived"`
	Running     bool   `json:"running"`
	UpdatedAtMs int64  `json:"updatedAtMs"`
}

// EngineCatalog is one engine's complete view. Error is set when part of the data could not be read;
// the rest is still returned so one broken store never hides the others.
type EngineCatalog struct {
	Engine        Engine         `json:"engine"`
	Installed     bool           `json:"installed"`
	Binary        string         `json:"binary,omitempty"`
	Models        []Model        `json:"models"`
	Projects      []Project      `json:"projects"`
	Conversations []Conversation `json:"conversations"`
	Total         int            `json:"total"`
	Error         string         `json:"error,omitempty"`
}

type Provider interface {
	Engine() Engine
	Load(ctx context.Context) EngineCatalog
}
