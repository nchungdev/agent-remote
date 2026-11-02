package protocol

type AgentType string

const (
	AgentTypeAntigravity AgentType = "ANTIGRAVITY"
	AgentTypeClaudeCode  AgentType = "CLAUDE_CODE"
	AgentTypeCodex       AgentType = "CODEX"
	AgentTypeMock        AgentType = "MOCK"
)

type AgentState string

const (
	AgentStateIdle            AgentState = "IDLE"
	AgentStateThinking        AgentState = "THINKING"
	AgentStateExecutingTool   AgentState = "EXECUTING_TOOL"
	AgentStateWaitingApproval AgentState = "WAITING_APPROVAL"
	AgentStateError           AgentState = "ERROR"
)

const (
	ActionSendPrompt  = "SEND_PROMPT"
	ActionApproveTool = "APPROVE_TOOL"
	ActionSelectAgent = "SELECT_AGENT"
	ActionCancel      = "CANCEL"
	ActionPing        = "PING"
	ActionListCatalog = "LIST_CATALOG"
	ActionGetHistory  = "GET_HISTORY"
)

// ClientMessage represents an incoming JSON-RPC command from the mobile client
type ClientMessage struct {
	Action         string    `json:"action"`
	AgentType      AgentType `json:"agentType,omitempty"`
	Model          string    `json:"model,omitempty"`
	ConversationID string    `json:"conversationId,omitempty"`
	Workspace      string    `json:"workspace,omitempty"`
	Prompt         string    `json:"prompt,omitempty"`
	CallID         string    `json:"callId,omitempty"`
	Approved       bool      `json:"approved,omitempty"`
	Limit          int       `json:"limit,omitempty"`
}
