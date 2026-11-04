package session

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

// GetParentSession loads a parent session, its bindings, and branches from SQLite.
func (s *Store) GetParentSession(id string) (*ParentSession, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	var sess ParentSession
	var leaseState string
	row := s.db.QueryRow(`
		SELECT id, title, repo_id, checkout_id, root_path,
		       writer_agent_id, writer_revision, lease_state, last_seq, created_at, updated_at
		FROM parent_sessions WHERE id = ?
	`, id)

	err := row.Scan(
		&sess.ID, &sess.Title, &sess.Workspace.RepositoryID, &sess.Workspace.CheckoutID, &sess.Workspace.RootPath,
		&sess.WriterLease.WriterAgentID, &sess.WriterLease.Revision, &leaseState,
		&sess.LastSeq, &sess.CreatedAt, &sess.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, ErrSessionNotFound
	}
	if err != nil {
		return nil, err
	}
	sess.WriterLease.State = LeaseState(leaseState)
	sess.Agents = make(map[string]*AgentBinding)
	sess.Branches = make(map[string]*ConsultBranch)

	// Query agent bindings
	rows, err := s.db.Query(`
		SELECT agent_id, engine, alias, model, effort, role,
		       native_session_id, native_mtime, seen_seq, is_active_writer
		FROM agent_bindings WHERE parent_session_id = ?
	`, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var ab AgentBinding
		var engine string
		var isActive int
		var nativeMTime sql.NullTime
		if err := rows.Scan(
			&ab.AgentID, &engine, &ab.Alias, &ab.Model, &ab.Effort, &ab.Role,
			&ab.NativeSessionID, &nativeMTime, &ab.SeenSeq, &isActive,
		); err != nil {
			return nil, err
		}
		ab.Engine = protocol.AgentType(engine)
		ab.IsActiveWriter = isActive == 1
		if nativeMTime.Valid {
			ab.NativeMTime = nativeMTime.Time
		}
		sess.Agents[ab.AgentID] = &ab
	}

	// Query consult branches
	bRows, err := s.db.Query(`
		SELECT branch_id, agent_id, engine, native_session_id, origin_seq, query, response, status, created_at
		FROM consult_branches WHERE parent_session_id = ?
	`, id)
	if err == nil {
		defer bRows.Close()
		for bRows.Next() {
			var cb ConsultBranch
			var eng, stat string
			if err := bRows.Scan(
				&cb.BranchID, &cb.AgentID, &eng, &cb.NativeSessionID,
				&cb.OriginSeq, &cb.Query, &cb.Response, &stat, &cb.CreatedAt,
			); err == nil {
				cb.ParentSessionID = id
				cb.Engine = protocol.AgentType(eng)
				cb.Status = TurnState(stat)
				sess.Branches[cb.BranchID] = &cb
			}
		}
	}

	return &sess, nil
}

// AppendEvent records an immutable session event and increments last_seq atomically.
func (s *Store) AppendEvent(ev *SessionEvent) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	tx, err := s.db.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	var nextSeq int64
	row := tx.QueryRow(`SELECT last_seq + 1 FROM parent_sessions WHERE id = ?`, ev.ParentSessionID)
	if err := row.Scan(&nextSeq); err != nil {
		return fmt.Errorf("failed to increment event sequence: %w", err)
	}

	ev.Seq = nextSeq
	if ev.CreatedAt.IsZero() {
		ev.CreatedAt = time.Now()
	}

	_, err = tx.Exec(`
		INSERT INTO session_events (
			parent_session_id, seq, turn_id, origin_engine, requested_by, purpose, event_type, payload, created_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
	`, ev.ParentSessionID, ev.Seq, ev.TurnID, string(ev.OriginEngine), ev.RequestedBy, string(ev.Purpose), ev.EventType, ev.Payload, ev.CreatedAt)
	if err != nil {
		return err
	}

	_, err = tx.Exec(`UPDATE parent_sessions SET last_seq = ?, updated_at = ? WHERE id = ?`, nextSeq, ev.CreatedAt, ev.ParentSessionID)
	if err != nil {
		return err
	}

	return tx.Commit()
}

// GetEvents returns timeline events strictly occurring after a given sequence number.
func (s *Store) GetEvents(parentSessionID string, afterSeq int64) ([]*SessionEvent, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	rows, err := s.db.Query(`
		SELECT id, parent_session_id, seq, turn_id, origin_engine, requested_by, purpose, event_type, payload, created_at
		FROM session_events
		WHERE parent_session_id = ? AND seq > ?
		ORDER BY seq ASC
	`, parentSessionID, afterSeq)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var events []*SessionEvent
	for rows.Next() {
		var ev SessionEvent
		var eng, purp string
		if err := rows.Scan(
			&ev.ID, &ev.ParentSessionID, &ev.Seq, &ev.TurnID, &eng,
			&ev.RequestedBy, &purp, &ev.EventType, &ev.Payload, &ev.CreatedAt,
		); err != nil {
			return nil, err
		}
		ev.OriginEngine = protocol.AgentType(eng)
		ev.Purpose = EventPurpose(purp)
		events = append(events, &ev)
	}
	return events, nil
}

// SaveConsultBranch persists a consult branch state.
func (s *Store) SaveConsultBranch(cb *ConsultBranch) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	_, err := s.db.Exec(`
		INSERT INTO consult_branches (
			branch_id, parent_session_id, agent_id, engine, native_session_id,
			origin_seq, query, response, status, created_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(branch_id) DO UPDATE SET
			response = excluded.response,
			status = excluded.status
	`, cb.BranchID, cb.ParentSessionID, cb.AgentID, string(cb.Engine), cb.NativeSessionID,
		cb.OriginSeq, cb.Query, cb.Response, string(cb.Status), cb.CreatedAt)
	return err
}

// SaveCheckpoint records a milestone state snapshot.
func (s *Store) SaveCheckpoint(cp *Checkpoint, parentSessionID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	decJSON, _ := json.Marshal(cp.Decisions)
	taskJSON, _ := json.Marshal(cp.PendingTasks)

	_, err := s.db.Exec(`
		INSERT INTO checkpoints (
			checkpoint_id, parent_session_id, goal, decisions_json, pending_tasks_json, git_head, seen_seq, created_at
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
	`, cp.CheckpointID, parentSessionID, cp.Goal, string(decJSON), string(taskJSON), cp.GitHead, cp.SeenSeq, cp.CreatedAt)
	return err
}
