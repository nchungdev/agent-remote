package session

import (
	"sync"
	"time"
)

// TurnUsage records token consumption for a discrete execution turn.
type TurnUsage struct {
	TurnID       string    `json:"turnId"`
	ParentID     string    `json:"parentId"`
	AgentID      string    `json:"agentId"`
	InputTokens  int       `json:"inputTokens"`
	OutputTokens int       `json:"outputTokens"`
	CacheRead    int       `json:"cacheRead,omitempty"`
	CacheWrite   int       `json:"cacheWrite,omitempty"`
	HasData      bool      `json:"hasData"`
	ReportedAt   time.Time `json:"reportedAt"`
}

// SessionTotalUsage aggregates per-turn metrics for the parent session (T2.11).
type SessionTotalUsage struct {
	TotalInputTokens  int     `json:"totalInputTokens"`
	TotalOutputTokens int     `json:"totalOutputTokens"`
	TotalCacheRead    int     `json:"totalCacheRead"`
	TotalCacheWrite   int     `json:"totalCacheWrite"`
	TurnCount         int     `json:"turnCount"`
	DataMissingPct    float64 `json:"dataMissingPct"`
}

// UsageTracker manages deduplicated token tracking across turns and replay streams.
type UsageTracker struct {
	mu          sync.RWMutex
	recorded    map[string]TurnUsage // keyed by turnID for replay deduplication
	turnOrder   []string
	lastContext map[string]int // agentID -> latest snapshot context size (not added to total)
}

func NewUsageTracker() *UsageTracker {
	return &UsageTracker{
		recorded:    make(map[string]TurnUsage),
		turnOrder:   make([]string, 0),
		lastContext: make(map[string]int),
	}
}

// RecordTurn records usage for a turn, deduplicating replays.
func (u *UsageTracker) RecordTurn(usage TurnUsage) bool {
	u.mu.Lock()
	defer u.mu.Unlock()

	if _, exists := u.recorded[usage.TurnID]; exists {
		// Already recorded: replay deduplication prevents double-counting
		return false
	}

	if usage.ReportedAt.IsZero() {
		usage.ReportedAt = time.Now()
	}

	u.recorded[usage.TurnID] = usage
	u.turnOrder = append(u.turnOrder, usage.TurnID)
	return true
}

// RecordContextSnapshot updates the active context footprint without polluting total token usage.
func (u *UsageTracker) RecordContextSnapshot(agentID string, contextTokens int) {
	u.mu.Lock()
	defer u.mu.Unlock()
	u.lastContext[agentID] = contextTokens
}

// ComputeTotals calculates the session aggregate metrics.
func (u *UsageTracker) ComputeTotals() SessionTotalUsage {
	u.mu.RLock()
	defer u.mu.RUnlock()

	var totals SessionTotalUsage
	if len(u.turnOrder) == 0 {
		return totals
	}

	missingCount := 0
	for _, id := range u.turnOrder {
		t := u.recorded[id]
		totals.TurnCount++
		if !t.HasData {
			missingCount++
			continue
		}
		totals.TotalInputTokens += t.InputTokens
		totals.TotalOutputTokens += t.OutputTokens
		totals.TotalCacheRead += t.CacheRead
		totals.TotalCacheWrite += t.CacheWrite
	}

	if totals.TurnCount > 0 {
		totals.DataMissingPct = float64(missingCount) / float64(totals.TurnCount) * 100.0
	}

	return totals
}
