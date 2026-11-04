package session

import (
	"context"
	"fmt"
	"strings"
	"time"
)

// SummarizerConfig controls rolling context compression (T2.12).
type SummarizerConfig struct {
	Enabled       bool
	TriggerEvents int
}

// RollingSummarizer condenses older timeline events into a structured Checkpoint.
type RollingSummarizer struct {
	config SummarizerConfig
}

// NewRollingSummarizer creates a summarizer with D8 default (disabled).
func NewRollingSummarizer(cfg SummarizerConfig) *RollingSummarizer {
	return &RollingSummarizer{config: cfg}
}

// MaybeSummarize evaluates timeline events and creates a checkpoint if enabled and threshold is met.
func (s *RollingSummarizer) MaybeSummarize(
	ctx context.Context,
	sess *ParentSession,
	events []*SessionEvent,
) (*Checkpoint, error) {
	if !s.config.Enabled || len(events) < s.config.TriggerEvents {
		return nil, nil // Disabled by default under Rule D8
	}

	var decisions []string
	var pending []string
	for _, ev := range events {
		if strings.Contains(strings.ToLower(ev.Payload), "decided") || strings.Contains(strings.ToLower(ev.Payload), "quyết định") {
			decisions = append(decisions, ev.Payload)
		} else if strings.Contains(strings.ToLower(ev.Payload), "todo") || strings.Contains(strings.ToLower(ev.Payload), "cần làm") {
			pending = append(pending, ev.Payload)
		}
	}

	cp := &Checkpoint{
		CheckpointID: fmt.Sprintf("cp-%d", time.Now().UnixNano()),
		Goal:         sess.Title,
		Decisions:    decisions,
		PendingTasks: pending,
		SeenSeq:      sess.LastSeq,
		CreatedAt:    time.Now(),
	}

	return cp, nil
}
