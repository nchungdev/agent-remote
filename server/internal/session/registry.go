package session

import (
	"context"
	"fmt"
	"sync"

	"app.duinch.agentremote/server/internal/protocol"
)

// EventEmitter defines the callback for streaming raw event frames to clients.
type EventEmitter func(event []byte)

// EngineCapability declares feature sets supported by an agent adapter.
type EngineCapability struct {
	SupportsAppServer     bool `json:"supportsAppServer"`
	SupportsNativeResume  bool `json:"supportsNativeResume"`
	SupportsConsultBranch bool `json:"supportsConsultBranch"`
	HasReadOnlySandbox    bool `json:"hasReadOnlySandbox"`
	SupportsApprovalFlow  bool `json:"supportsApprovalFlow"`
}

// EngineSessionAdapter manages turn execution for an engine agent in a parent session.
type EngineSessionAdapter interface {
	Engine() protocol.AgentType
	Capabilities() EngineCapability
	ExecuteTurn(ctx context.Context, req TurnRequest, emit EventEmitter) (*TurnResult, error)
	HandleApproval(turnID string, callID string, approved bool) error
	Interrupt(turnID string) error
	Close() error
}

// Registry maintains the pool of registered engine adapters.
type Registry struct {
	mu       sync.RWMutex
	adapters map[protocol.AgentType]EngineSessionAdapter
}

var (
	defaultRegistry = &Registry{adapters: make(map[protocol.AgentType]EngineSessionAdapter)}
)

func DefaultRegistry() *Registry {
	return defaultRegistry
}

func (r *Registry) Register(adapter EngineSessionAdapter) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.adapters[adapter.Engine()] = adapter
}

func (r *Registry) Get(engine protocol.AgentType) (EngineSessionAdapter, error) {
	r.mu.RLock()
	defer r.mu.RUnlock()
	adapter, exists := r.adapters[engine]
	if !exists {
		return nil, fmt.Errorf("no adapter registered for engine %s", engine)
	}
	return adapter, nil
}

func (r *Registry) ListEngines() []protocol.AgentType {
	r.mu.RLock()
	defer r.mu.RUnlock()
	list := make([]protocol.AgentType, 0, len(r.adapters))
	for eng := range r.adapters {
		list = append(list, eng)
	}
	return list
}
