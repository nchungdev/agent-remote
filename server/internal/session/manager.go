package session

import (
	"context"
	"fmt"
	"sync"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

// Manager coordinates parent sessions and multi-agent writer leases.
type Manager struct {
	mu       sync.RWMutex
	sessions map[string]*ParentSession
	registry *Registry
	store    *Store
}

func NewManager(registry *Registry, store ...*Store) *Manager {
	var s *Store
	if len(store) > 0 {
		s = store[0]
	}
	return &Manager{
		sessions: make(map[string]*ParentSession),
		registry: registry,
		store:    s,
	}
}

// CreateParentSession initializes a new parent session with a canonical checkout root.
func (m *Manager) CreateParentSession(
	id string,
	title string,
	ws WorkspaceRef,
	initialAgent protocol.AgentType,
	model string,
) (*ParentSession, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	agentID := fmt.Sprintf("%s-agent-1", initialAgent)
	now := time.Now()

	sess := &ParentSession{
		ID:        id,
		Title:     title,
		Workspace: ws,
		WriterLease: WriterLease{
			WriterAgentID: agentID,
			Revision:      1,
			State:         LeaseAcquired,
			AcquiredAt:    now,
		},
		Agents: map[string]*AgentBinding{
			agentID: {
				AgentID:        agentID,
				Engine:         initialAgent,
				Alias:          string(initialAgent),
				Model:          model,
				IsActiveWriter: true,
			},
		},
		Branches:  make(map[string]*ConsultBranch),
		CreatedAt: now,
		UpdatedAt: now,
	}

	m.sessions[id] = sess

	if m.store != nil {
		if err := m.store.SaveParentSession(sess); err != nil {
			return nil, fmt.Errorf("failed to persist parent session: %w", err)
		}
	}

	return sess, nil
}

func (m *Manager) GetSession(id string) (*ParentSession, error) {
	m.mu.RLock()
	defer m.mu.RUnlock()
	sess, exists := m.sessions[id]
	if !exists {
		return nil, ErrSessionNotFound
	}
	return sess, nil
}

// TransferWriter atomically transfers write authority using Compare-And-Swap (CAS).
func (m *Manager) TransferWriter(
	sessionID string,
	targetAgentID string,
	targetEngine protocol.AgentType,
	model string,
	expectedRevision int64,
) (*WriterLease, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	sess, exists := m.sessions[sessionID]
	if !exists {
		return nil, ErrSessionNotFound
	}

	now := time.Now()

	// Perform CAS update
	if m.store != nil {
		lease, err := m.store.UpdateWriterLeaseCAS(sessionID, expectedRevision, targetAgentID, LeaseAcquired)
		if err != nil {
			return nil, err
		}
		sess.WriterLease = *lease
	} else {
		if sess.WriterLease.Revision != expectedRevision {
			return nil, fmt.Errorf("%w: expected revision %d, actual %d",
				ErrWriterConflict, expectedRevision, sess.WriterLease.Revision)
		}
		sess.WriterLease = WriterLease{
			WriterAgentID: targetAgentID,
			Revision:      sess.WriterLease.Revision + 1,
			State:         LeaseAcquired,
			AcquiredAt:    now,
		}
	}

	// Demote previous writer
	if prev, ok := sess.Agents[sess.WriterLease.WriterAgentID]; ok {
		prev.IsActiveWriter = false
	}

	// Register or update target agent binding
	targetBinding, exists := sess.Agents[targetAgentID]
	if !exists {
		targetBinding = &AgentBinding{
			AgentID: targetAgentID,
			Engine:  targetEngine,
			Alias:   string(targetEngine),
			Model:   model,
		}
		sess.Agents[targetAgentID] = targetBinding
	}
	targetBinding.IsActiveWriter = true
	if model != "" {
		targetBinding.Model = model
	}

	sess.UpdatedAt = now

	if m.store != nil {
		_ = m.store.AppendEvent(&SessionEvent{
			ParentSessionID: sessionID,
			TurnID:          fmt.Sprintf("transfer-%d", sess.WriterLease.Revision),
			OriginEngine:    targetEngine,
			RequestedBy:     "user",
			Purpose:         PurposeWork,
			EventType:       "writer_transferred",
			Payload:         fmt.Sprintf("Writer transferred to %s (%s)", targetAgentID, targetEngine),
			CreatedAt:       now,
		})
	}

	return &sess.WriterLease, nil
}

// ExecuteTurn executes a prompt turn honoring single-writer lease or consult branch rules.
func (m *Manager) ExecuteTurn(
	ctx context.Context,
	req TurnRequest,
	emit EventEmitter,
) (*TurnResult, error) {
	m.mu.RLock()
	sess, exists := m.sessions[req.ParentSessionID]
	m.mu.RUnlock()

	if !exists {
		return nil, ErrSessionNotFound
	}

	// Workspace verification
	if req.Workspace.RootPath != "" && req.Workspace.RootPath != sess.Workspace.RootPath {
		return nil, ErrWorkspaceMismatch
	}

	// Single-writer enforcement for primary work
	if !req.IsConsult {
		if sess.WriterLease.WriterAgentID != req.TargetAgentID || sess.WriterLease.State != LeaseAcquired {
			return nil, ErrAgentNotWriter
		}
	}

	binding, ok := sess.Agents[req.TargetAgentID]
	if !ok {
		return nil, fmt.Errorf("agent %s not bound to session %s", req.TargetAgentID, req.ParentSessionID)
	}

	adapter, err := m.registry.Get(binding.Engine)
	if err != nil {
		return nil, err
	}

	// Execute turn via adapter
	res, err := adapter.ExecuteTurn(ctx, req, emit)
	if err != nil {
		return res, err
	}

	// If consult branch, store isolated result
	if req.IsConsult {
		m.mu.Lock()
		branch := &ConsultBranch{
			BranchID:        req.TurnID,
			ParentSessionID: req.ParentSessionID,
			AgentID:         req.TargetAgentID,
			Engine:          binding.Engine,
			Query:           req.Prompt,
			Response:        res.Output,
			Status:          res.State,
			CreatedAt:       time.Now(),
		}
		sess.Branches[req.TurnID] = branch
		m.mu.Unlock()

		if m.store != nil {
			_ = m.store.SaveConsultBranch(branch)
		}
	} else if m.store != nil {
		ev := &SessionEvent{
			ParentSessionID: req.ParentSessionID,
			TurnID:          req.TurnID,
			OriginEngine:    binding.Engine,
			RequestedBy:     "user",
			Purpose:         PurposeWork,
			EventType:       "turn_completed",
			Payload:         res.Output,
			CreatedAt:       time.Now(),
		}
		_ = m.store.AppendEvent(ev)
		binding.SeenSeq = ev.Seq
	}

	return res, nil
}

// HandleApproval routes a user tool approval decision to the agent executing the turn (T2.8).
func (m *Manager) HandleApproval(sessionID string, targetAgentID string, turnID string, callID string, approved bool) error {
	sess, err := m.GetSession(sessionID)
	if err != nil {
		return err
	}

	binding, exists := sess.Agents[targetAgentID]
	if !exists {
		return fmt.Errorf("agent %s not found in session %s", targetAgentID, sessionID)
	}

	adapter, err := m.registry.Get(binding.Engine)
	if err != nil {
		return err
	}

	if m.store != nil {
		_ = m.store.AppendEvent(&SessionEvent{
			ParentSessionID: sessionID,
			TurnID:          turnID,
			OriginEngine:    binding.Engine,
			RequestedBy:     "user",
			Purpose:         PurposeWork,
			EventType:       "tool_approval_resolved",
			Payload:         fmt.Sprintf("Approval for tool call %s: approved=%t", callID, approved),
			CreatedAt:       time.Now(),
		})
	}

	return adapter.HandleApproval(turnID, callID, approved)
}
