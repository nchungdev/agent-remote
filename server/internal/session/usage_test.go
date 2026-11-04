package session

import (
	"testing"
)

func TestUsageTrackerDeduplicationAndSnapshotIsolation(t *testing.T) {
	tracker := NewUsageTracker()

	// 1. Record Turn 1
	turn1 := TurnUsage{
		TurnID:       "turn-1",
		ParentID:     "ps-1",
		AgentID:      "codex-1",
		InputTokens:  100,
		OutputTokens: 50,
		CacheRead:    20,
		CacheWrite:   10,
		HasData:      true,
	}
	if !tracker.RecordTurn(turn1) {
		t.Fatalf("expected turn 1 to be recorded")
	}

	// 2. Replay Turn 1 (should be deduplicated and return false)
	if tracker.RecordTurn(turn1) {
		t.Fatalf("expected replay of turn 1 to be deduplicated")
	}

	// 3. Record snapshot context of 15,000 tokens (must NOT be added to totals)
	tracker.RecordContextSnapshot("codex-1", 15000)

	// 4. Record Turn 2 with missing data
	turn2 := TurnUsage{
		TurnID:   "turn-2",
		ParentID: "ps-1",
		AgentID:  "claude-1",
		HasData:  false, // Adapter did not provide usage
	}
	tracker.RecordTurn(turn2)

	// 5. Record Consult Turn 3
	turn3 := TurnUsage{
		TurnID:       "turn-3",
		ParentID:     "ps-1",
		AgentID:      "claude-consult",
		InputTokens:  200,
		OutputTokens: 80,
		HasData:      true,
	}
	tracker.RecordTurn(turn3)

	totals := tracker.ComputeTotals()

	if totals.TurnCount != 3 {
		t.Errorf("expected 3 turns, got %d", totals.TurnCount)
	}

	// Total Input: 100 + 200 = 300 (15,000 snapshot NOT included)
	if totals.TotalInputTokens != 300 {
		t.Errorf("expected 300 total input tokens, got %d", totals.TotalInputTokens)
	}

	// Total Output: 50 + 80 = 130
	if totals.TotalOutputTokens != 130 {
		t.Errorf("expected 130 total output tokens, got %d", totals.TotalOutputTokens)
	}

	// 1 out of 3 turns missing data => 33.33%
	if totals.DataMissingPct < 33.0 || totals.DataMissingPct > 34.0 {
		t.Errorf("expected ~33.33%% missing data, got %.2f%%", totals.DataMissingPct)
	}
}
