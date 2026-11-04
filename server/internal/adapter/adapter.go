package adapter

import "app.duinch.agentremote/server/internal/protocol"

type Emitter func(event []byte)

type PromptOptions struct {
	Prompt         string
	Model          string
	ConversationID string
	PermissionMode string
	Workspace      string
}

type Adapter interface {
	AgentType() protocol.AgentType
	Initialize() error
	HandlePrompt(opts PromptOptions, emit Emitter)
	HandleApproval(callID string, approved bool)
	Cancel()
	Close()
}
