package catalog

import (
	"context"
	"time"
)

type MockProvider struct{}

func NewMockProvider() *MockProvider { return &MockProvider{} }

func (m *MockProvider) Engine() Engine { return EngineMock }

func (m *MockProvider) Load(ctx context.Context) EngineCatalog {
	now := time.Now().UnixMilli()
	return EngineCatalog{
		Engine:    EngineMock,
		Installed: true,
		Binary:    "builtin-mock",
		Models: []Model{
			{ID: "mock-fast", Label: "Mock Fast", Description: "Phản hồi siêu tốc (Test)"},
			{ID: "mock-thinking", Label: "Mock Thinking", Description: "Mô phỏng suy luận sâu (Thinking)"},
			{ID: "mock-tool-approval", Label: "Mock Tool Approval", Description: "Kiểm thử phê duyệt lệnh (Interactive)"},
		},
		Projects: []Project{
			{
				ID:      "mock-demo-project",
				Name:    "Demo Workspace (Mock)",
				Folders: []string{"/Users/demo/workspace"},
			},
		},
		Conversations: []Conversation{
			{
				ID:          "mock-conv-interactive",
				Title:       "Kiểm thử phê duyệt Tool Execution & Stream",
				ProjectID:   "mock-demo-project",
				Workspace:   "/Users/demo/workspace",
				Section:     "Pinned",
				Pinned:      true,
				UpdatedAtMs: now,
			},
			{
				ID:          "mock-conv-fast",
				Title:       "Kiểm thử độ trễ kết nối P2P WebRTC",
				ProjectID:   "mock-demo-project",
				Workspace:   "/Users/demo/workspace",
				Section:     "Recent",
				UpdatedAtMs: now - 3600000,
			},
		},
	}
}
