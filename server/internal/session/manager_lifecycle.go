package session

import (
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

var (
	ErrInvalidNativeID = errors.New("invalid native session ID: contains forbidden characters")
	ErrTooManyConsults = errors.New("maximum concurrent consult branches reached for parent session (limit: 3)")
)

// Allowed native session ID regex (disallows path traversals, flags, wildcards)
var validNativeIDRegex = regexp.MustCompile(`^[a-zA-Z0-9_\-\.:]+$`)

// ValidateNativeSessionID checks that an imported native session identifier is safe (T2.9).
func ValidateNativeSessionID(nativeID string) error {
	trimmed := strings.TrimSpace(nativeID)
	if trimmed == "" {
		return errors.New("native session ID cannot be empty")
	}
	if strings.Contains(trimmed, "..") || strings.Contains(trimmed, "*") || strings.HasPrefix(trimmed, "--") {
		return ErrInvalidNativeID
	}
	if !validNativeIDRegex.MatchString(trimmed) {
		return ErrInvalidNativeID
	}
	return nil
}

// ImportNativeSession creates a parent session initialized from a pre-existing CLI session (T2.9).
func (m *Manager) ImportNativeSession(
	id string,
	title string,
	engine protocol.AgentType,
	nativeSessionID string,
	ws WorkspaceRef,
) (*ParentSession, error) {
	if err := ValidateNativeSessionID(nativeSessionID); err != nil {
		return nil, err
	}

	m.mu.Lock()
	defer m.mu.Unlock()

	agentID := fmt.Sprintf("%s-native-1", engine)
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
				AgentID:         agentID,
				Engine:          engine,
				Alias:           string(engine),
				NativeSessionID: nativeSessionID,
				NativeMTime:     now,
				IsActiveWriter:  true,
			},
		},
		Branches:  make(map[string]*ConsultBranch),
		CreatedAt: now,
		UpdatedAt: now,
	}

	m.sessions[id] = sess

	if m.store != nil {
		if err := m.store.SaveParentSession(sess); err != nil {
			return nil, fmt.Errorf("failed to persist imported session: %w", err)
		}
		_ = m.store.AppendEvent(&SessionEvent{
			ParentSessionID: id,
			TurnID:          "import-init",
			OriginEngine:    engine,
			RequestedBy:     "user",
			Purpose:         PurposeWork,
			EventType:       "native_imported",
			Payload:         fmt.Sprintf("Imported from native session %s", nativeSessionID),
			CreatedAt:       now,
		})
	}

	return sess, nil
}

// CheckNativeDrift checks if the underlying engine session changed outside Agent Remote (T2.10).
func (m *Manager) CheckNativeDrift(sessionID string, agentID string, currentMTime time.Time) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()

	sess, exists := m.sessions[sessionID]
	if !exists {
		return false, ErrSessionNotFound
	}

	binding, exists := sess.Agents[agentID]
	if !exists {
		return false, fmt.Errorf("agent %s not found in session %s", agentID, sessionID)
	}

	if binding.NativeMTime.IsZero() {
		binding.NativeMTime = currentMTime
		return false, nil
	}

	// Detect if external modification occurred after recorded native mtime
	if currentMTime.After(binding.NativeMTime.Add(time.Second)) {
		sess.WriterLease.State = LeaseVerificationNeeded

		if m.store != nil {
			_ = m.store.AppendEvent(&SessionEvent{
				ParentSessionID: sessionID,
				TurnID:          fmt.Sprintf("drift-%d", time.Now().Unix()),
				OriginEngine:    binding.Engine,
				RequestedBy:     "system",
				Purpose:         PurposeWork,
				EventType:       "external_drift_detected",
				Payload:         fmt.Sprintf("External file modification detected for native session %s (mtime: %s)", binding.NativeSessionID, currentMTime.Format(time.RFC3339)),
				CreatedAt:       time.Now(),
			})
		}
		return true, nil
	}

	return false, nil
}

// CancelSession cancels active executions across the writer and all consult branches (T2.7).
func (m *Manager) CancelSession(sessionID string) error {
	sess, err := m.GetSession(sessionID)
	if err != nil {
		return err
	}

	// Interrupt all registered adapters for agents in this session
	for _, binding := range sess.Agents {
		if adapter, err := m.registry.Get(binding.Engine); err == nil {
			_ = adapter.Interrupt(sessionID)
		}
	}

	return nil
}
