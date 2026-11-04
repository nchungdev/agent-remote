package session

import (
	"errors"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
)

var (
	ErrWriterConflict    = errors.New("writer revision conflict: another agent acquired writer lease")
	ErrAgentNotWriter    = errors.New("agent does not hold active writer lease for this parent session")
	ErrSessionNotFound   = errors.New("parent session not found")
	ErrTurnNotFound      = errors.New("turn not found")
	ErrWorkspaceMismatch = errors.New("workspace root does not match active checkout")
)

// LeaseState represents the lifecycle of a writer lease in a parent session.
type LeaseState string

const (
	LeaseAcquired           LeaseState = "ACQUIRED"
	LeaseReleased           LeaseState = "RELEASED"
	LeaseVerificationNeeded LeaseState = "VERIFICATION_NEEDED"
)

// TurnState represents the execution state of an agent turn.
type TurnState string

const (
	TurnPending   TurnState = "PENDING"
	TurnRunning   TurnState = "RUNNING"
	TurnApproval  TurnState = "APPROVAL_REQUIRED"
	TurnCompleted TurnState = "COMPLETED"
	TurnCancelled TurnState = "CANCELLED"
	TurnFailed    TurnState = "FAILED"
)

// WorkspaceRef defines the shared single checkout root for a parent session.
type WorkspaceRef struct {
	RepositoryID string `json:"repositoryId"`
	CheckoutID   string `json:"checkoutId"`
	RootPath     string `json:"rootPath"`
}

// WriterLease guarantees that at any instant, exactly one agent has write access (D5).
type WriterLease struct {
	WriterAgentID string     `json:"writerAgentId"`
	Revision      int64      `json:"revision"`
	State         LeaseState `json:"state"`
	AcquiredAt    time.Time  `json:"acquiredAt"`
	ExpiresAt     time.Time  `json:"expiresAt,omitempty"`
}

// AgentBinding tracks an engine agent associated with a parent session.
type AgentBinding struct {
	AgentID         string             `json:"agentId"`
	Engine          protocol.AgentType `json:"engine"`
	Alias           string             `json:"alias"`
	Model           string             `json:"model"`
	Effort          string             `json:"effort,omitempty"`
	Role            string             `json:"role,omitempty"`
	NativeSessionID string             `json:"nativeSessionId"`
	NativeMTime     time.Time          `json:"nativeMTime,omitempty"`
	SeenSeq         int64              `json:"seenSeq"`
	IsActiveWriter  bool               `json:"isActiveWriter"`
}

// EventPurpose indicates whether an event belongs to primary work or an isolated consultation.
type EventPurpose string

const (
	PurposeWork    EventPurpose = "work"
	PurposeConsult EventPurpose = "consult"
)

// SessionEvent models an immutable log event in the parent session timeline.
type SessionEvent struct {
	ID              int64              `json:"id"`
	ParentSessionID string             `json:"parentSessionId"`
	Seq             int64              `json:"seq"`
	TurnID          string             `json:"turnId"`
	OriginEngine    protocol.AgentType `json:"originEngine"`
	RequestedBy     string             `json:"requestedBy"`
	Purpose         EventPurpose       `json:"purpose"`
	EventType       string             `json:"eventType"`
	Payload         string             `json:"payload"`
	CreatedAt       time.Time          `json:"createdAt"`
}

// ConsultBranch represents an isolated, read-only sidechat query (@agent:btw).
type ConsultBranch struct {
	BranchID        string             `json:"branchId"`
	ParentSessionID string             `json:"parentSessionId"`
	AgentID         string             `json:"agentId"`
	Engine          protocol.AgentType `json:"engine"`
	NativeSessionID string             `json:"nativeSessionId"`
	OriginSeq       int64              `json:"originSeq"`
	Query           string             `json:"query"`
	Response        string             `json:"response"`
	Status          TurnState          `json:"status"`
	CreatedAt       time.Time          `json:"createdAt"`
}

// Checkpoint captures snapshot state for seamless handoff between engines.
type Checkpoint struct {
	CheckpointID string    `json:"checkpointId"`
	Goal         string    `json:"goal"`
	Decisions    []string  `json:"decisions"`
	PendingTasks []string  `json:"pendingTasks"`
	GitHead      string    `json:"gitHead"`
	SeenSeq      int64     `json:"seenSeq"`
	CreatedAt    time.Time `json:"createdAt"`
}

// TurnRequest defines a typed execution turn submitted to an agent.
type TurnRequest struct {
	ParentSessionID  string       `json:"parentSessionId"`
	TurnID           string       `json:"turnId"`
	TargetAgentID    string       `json:"targetAgentId"`
	ExpectedRevision int64        `json:"expectedRevision"`
	Prompt           string       `json:"prompt"`
	Model            string       `json:"model,omitempty"`
	PermissionMode   string       `json:"permissionMode,omitempty"`
	Workspace        WorkspaceRef `json:"workspace"`
	IsConsult        bool         `json:"isConsult"`
}

// TurnResult conveys the final or interim typed result of a turn.
type TurnResult struct {
	TurnID       string     `json:"turnId"`
	State        TurnState  `json:"state"`
	Output       string     `json:"output,omitempty"`
	Error        string     `json:"error,omitempty"`
	InputTokens  int        `json:"inputTokens,omitempty"`
	OutputTokens int        `json:"outputTokens,omitempty"`
	Checkpoint   *Checkpoint `json:"checkpoint,omitempty"`
}

// ParentSession is the source of truth managing all child engine sessions.
type ParentSession struct {
	ID          string                  `json:"id"`
	Title       string                  `json:"title"`
	Workspace   WorkspaceRef            `json:"workspace"`
	WriterLease WriterLease             `json:"writerLease"`
	Agents      map[string]*AgentBinding `json:"agents"`
	Branches    map[string]*ConsultBranch `json:"branches"`
	LastSeq     int64                   `json:"lastSeq"`
	CreatedAt   time.Time               `json:"createdAt"`
	UpdatedAt   time.Time               `json:"updatedAt"`
}
