package session

import (
	"context"
	"errors"
	"fmt"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

var (
	ErrEngineCannotConsult = errors.New("engine does not support read-only consult branches")
	ErrWriterBusy          = errors.New("current writer is busy executing a turn; cancel it before switching")
)

// SwitchWriter transfers write authority to another agent with atomic CAS and event tracking (T2.5).
func (m *Manager) SwitchWriter(
	sessionID string,
	toAgentID string,
	toEngine protocol.AgentType,
	model string,
	expectedRevision int64,
) (*WriterLease, error) {
	return m.TransferWriter(sessionID, toAgentID, toEngine, model, expectedRevision)
}

// Consult executes an isolated, read-only consultation (@agent:btw) against a target engine (T2.6).
func (m *Manager) Consult(
	ctx context.Context,
	sessionID string,
	targetEngine protocol.AgentType,
	model string,
	query string,
	opts BuildContextOptions,
	emit EventEmitter,
) (*ConsultBranch, error) {
	sess, err := m.GetSession(sessionID)
	if err != nil {
		return nil, err
	}

	adapter, err := m.registry.Get(targetEngine)
	if err != nil {
		return nil, err
	}

	caps := adapter.Capabilities()
	if !caps.SupportsConsultBranch && !caps.HasReadOnlySandbox {
		return nil, ErrEngineCannotConsult
	}

	targetAgentID := fmt.Sprintf("%s-consult-%d", targetEngine, time.Now().UnixNano())
	turnID := fmt.Sprintf("branch-%d", time.Now().UnixNano())

	// Ensure consult target is registered in session
	m.mu.Lock()
	if _, exists := sess.Agents[targetAgentID]; !exists {
		sess.Agents[targetAgentID] = &AgentBinding{
			AgentID:        targetAgentID,
			Engine:         targetEngine,
			Alias:          string(targetEngine),
			Model:          model,
			IsActiveWriter: false,
		}
	}
	m.mu.Unlock()

	// Capture git diff if needed for review
	if opts.GitDiffSnapshot == "" && sess.Workspace.RootPath != "" {
		st, _ := CaptureGitStatus(sess.Workspace.RootPath)
		if st != nil && st.DiffStat != "" {
			opts.GitDiffSnapshot = st.DiffStat
		}
	}

	opts.Query = query
	built := BuildContext(PurposeConsult, sess, nil, opts)

	req := TurnRequest{
		ParentSessionID: sessionID,
		TurnID:          turnID,
		TargetAgentID:   targetAgentID,
		Prompt:          built.Prompt,
		Model:           model,
		Workspace:       sess.Workspace,
		IsConsult:       true,
		PermissionMode:  "read-only",
	}

	res, err := m.ExecuteTurn(ctx, req, emit)
	if err != nil {
		return nil, err
	}

	m.mu.RLock()
	branch := sess.Branches[turnID]
	m.mu.RUnlock()

	if branch == nil {
		branch = &ConsultBranch{
			BranchID:        turnID,
			ParentSessionID: sessionID,
			AgentID:         targetAgentID,
			Engine:          targetEngine,
			Query:           query,
			Response:        res.Output,
			Status:          res.State,
			CreatedAt:       time.Now(),
		}
	}

	return branch, nil
}
