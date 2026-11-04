package session

import (
	"database/sql"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"sync"
	"time"

	_ "modernc.org/sqlite"
)

// Store manages SQLite persistence for parent sessions, child agent bindings, and event history.
type Store struct {
	mu sync.RWMutex
	db *sql.DB
}

// NewStore opens or creates an SQLite database configured for robust multi-agent concurrency.
func NewStore(dbPath string) (*Store, error) {
	if dbPath != ":memory:" {
		if err := os.MkdirAll(filepath.Dir(dbPath), 0755); err != nil {
			return nil, fmt.Errorf("failed to create db directory: %w", err)
		}
	}

	dsn := fmt.Sprintf("file:%s?_pragma=busy_timeout(5000)&_pragma=journal_mode(WAL)&_pragma=foreign_keys(ON)",
		(&url.URL{Path: dbPath}).EscapedPath())
	if dbPath == ":memory:" {
		dsn = "file:memdb1?mode=memory&cache=shared&_pragma=busy_timeout(5000)&_pragma=foreign_keys(ON)"
	}

	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open sqlite db: %w", err)
	}

	s := &Store{db: db}
	if err := s.migrate(); err != nil {
		db.Close()
		return nil, fmt.Errorf("migration failed: %w", err)
	}

	return s, nil
}

func (s *Store) Close() error {
	return s.db.Close()
}

func (s *Store) migrate() error {
	schema := `
	CREATE TABLE IF NOT EXISTS parent_sessions (
		id TEXT PRIMARY KEY,
		title TEXT NOT NULL,
		repo_id TEXT NOT NULL,
		checkout_id TEXT NOT NULL,
		root_path TEXT NOT NULL,
		writer_agent_id TEXT NOT NULL,
		writer_revision INTEGER NOT NULL DEFAULT 1,
		lease_state TEXT NOT NULL DEFAULT 'ACQUIRED',
		last_seq INTEGER NOT NULL DEFAULT 0,
		created_at TIMESTAMP NOT NULL,
		updated_at TIMESTAMP NOT NULL
	);

	CREATE TABLE IF NOT EXISTS agent_bindings (
		parent_session_id TEXT NOT NULL,
		agent_id TEXT NOT NULL,
		engine TEXT NOT NULL,
		alias TEXT NOT NULL,
		model TEXT NOT NULL,
		effort TEXT NOT NULL DEFAULT '',
		role TEXT NOT NULL DEFAULT '',
		native_session_id TEXT NOT NULL DEFAULT '',
		native_mtime TIMESTAMP,
		seen_seq INTEGER NOT NULL DEFAULT 0,
		is_active_writer INTEGER NOT NULL DEFAULT 0,
		PRIMARY KEY (parent_session_id, agent_id),
		FOREIGN KEY (parent_session_id) REFERENCES parent_sessions(id) ON DELETE CASCADE
	);

	CREATE TABLE IF NOT EXISTS session_events (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		parent_session_id TEXT NOT NULL,
		seq INTEGER NOT NULL,
		turn_id TEXT NOT NULL DEFAULT '',
		origin_engine TEXT NOT NULL DEFAULT '',
		requested_by TEXT NOT NULL DEFAULT '',
		purpose TEXT NOT NULL DEFAULT 'work',
		event_type TEXT NOT NULL,
		payload TEXT NOT NULL,
		created_at TIMESTAMP NOT NULL,
		FOREIGN KEY (parent_session_id) REFERENCES parent_sessions(id) ON DELETE CASCADE
	);

	CREATE TABLE IF NOT EXISTS consult_branches (
		branch_id TEXT PRIMARY KEY,
		parent_session_id TEXT NOT NULL,
		agent_id TEXT NOT NULL,
		engine TEXT NOT NULL,
		native_session_id TEXT NOT NULL DEFAULT '',
		origin_seq INTEGER NOT NULL,
		query TEXT NOT NULL,
		response TEXT NOT NULL DEFAULT '',
		status TEXT NOT NULL,
		created_at TIMESTAMP NOT NULL,
		FOREIGN KEY (parent_session_id) REFERENCES parent_sessions(id) ON DELETE CASCADE
	);

	CREATE TABLE IF NOT EXISTS checkpoints (
		checkpoint_id TEXT PRIMARY KEY,
		parent_session_id TEXT NOT NULL,
		goal TEXT NOT NULL,
		decisions_json TEXT NOT NULL,
		pending_tasks_json TEXT NOT NULL,
		git_head TEXT NOT NULL,
		seen_seq INTEGER NOT NULL,
		created_at TIMESTAMP NOT NULL,
		FOREIGN KEY (parent_session_id) REFERENCES parent_sessions(id) ON DELETE CASCADE
	);
	`
	_, err := s.db.Exec(schema)
	return err
}

// SaveParentSession inserts or updates a parent session and its child agent bindings in a single transaction.
func (s *Store) SaveParentSession(sess *ParentSession) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	tx, err := s.db.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	query := `
	INSERT INTO parent_sessions (
		id, title, repo_id, checkout_id, root_path,
		writer_agent_id, writer_revision, lease_state, last_seq, created_at, updated_at
	) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	ON CONFLICT(id) DO UPDATE SET
		title = excluded.title,
		repo_id = excluded.repo_id,
		checkout_id = excluded.checkout_id,
		root_path = excluded.root_path,
		writer_agent_id = excluded.writer_agent_id,
		writer_revision = excluded.writer_revision,
		lease_state = excluded.lease_state,
		last_seq = excluded.last_seq,
		updated_at = excluded.updated_at
	`
	_, err = tx.Exec(
		query,
		sess.ID, sess.Title, sess.Workspace.RepositoryID, sess.Workspace.CheckoutID, sess.Workspace.RootPath,
		sess.WriterLease.WriterAgentID, sess.WriterLease.Revision, string(sess.WriterLease.State),
		sess.LastSeq, sess.CreatedAt, sess.UpdatedAt,
	)
	if err != nil {
		return err
	}

	for _, ab := range sess.Agents {
		isActive := 0
		if ab.IsActiveWriter {
			isActive = 1
		}
		bindingQuery := `
		INSERT INTO agent_bindings (
			parent_session_id, agent_id, engine, alias, model, effort, role,
			native_session_id, native_mtime, seen_seq, is_active_writer
		) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(parent_session_id, agent_id) DO UPDATE SET
			engine = excluded.engine,
			alias = excluded.alias,
			model = excluded.model,
			effort = excluded.effort,
			role = excluded.role,
			native_session_id = excluded.native_session_id,
			native_mtime = excluded.native_mtime,
			seen_seq = excluded.seen_seq,
			is_active_writer = excluded.is_active_writer
		`
		_, err = tx.Exec(
			bindingQuery,
			sess.ID, ab.AgentID, string(ab.Engine), ab.Alias, ab.Model, ab.Effort, ab.Role,
			ab.NativeSessionID, ab.NativeMTime, ab.SeenSeq, isActive,
		)
		if err != nil {
			return err
		}
	}

	return tx.Commit()
}

// UpdateWriterLeaseCAS performs atomic Compare-And-Swap on the writer lease.
func (s *Store) UpdateWriterLeaseCAS(
	sessionID string,
	expectedRevision int64,
	newWriterAgentID string,
	newState LeaseState,
) (*WriterLease, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	now := time.Now()
	newRev := expectedRevision + 1

	tx, err := s.db.Begin()
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	res, err := tx.Exec(`
		UPDATE parent_sessions
		SET writer_agent_id = ?, writer_revision = ?, lease_state = ?, updated_at = ?
		WHERE id = ? AND writer_revision = ?
	`, newWriterAgentID, newRev, string(newState), now, sessionID, expectedRevision)
	if err != nil {
		return nil, err
	}

	rows, err := res.RowsAffected()
	if err != nil {
		return nil, err
	}
	if rows == 0 {
		return nil, ErrWriterConflict
	}

	// Demote older writer in agent_bindings
	_, _ = tx.Exec(`UPDATE agent_bindings SET is_active_writer = 0 WHERE parent_session_id = ?`, sessionID)
	// Promote new writer
	_, _ = tx.Exec(`UPDATE agent_bindings SET is_active_writer = 1 WHERE parent_session_id = ? AND agent_id = ?`, sessionID, newWriterAgentID)

	if err := tx.Commit(); err != nil {
		return nil, err
	}

	return &WriterLease{
		WriterAgentID: newWriterAgentID,
		Revision:      newRev,
		State:         newState,
		AcquiredAt:    now,
	}, nil
}
