package catalog

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"
)

type HistoryMessage struct {
	ID          string `json:"id"`
	Role        string `json:"role"` // "user" or "assistant"
	Content     string `json:"content"`
	TimestampMs int64  `json:"timestampMs,omitempty"`
}

var userRequestRegex = regexp.MustCompile(`(?s)<USER_REQUEST>(.*?)</USER_REQUEST>`)

// LoadHistory reads recent conversation dialogue from the engine's store on disk.
func (s *Service) LoadHistory(ctx context.Context, engine Engine, conversationID string, limit int) ([]HistoryMessage, error) {
	if limit <= 0 {
		limit = 30
	}

	switch engine {
	case EngineAntigravity:
		return s.loadAntigravityHistory(ctx, conversationID, limit)
	case EngineClaudeCode:
		return s.loadClaudeHistory(ctx, conversationID, limit)
	case EngineCodex:
		return s.loadCodexHistory(ctx, conversationID, limit)
	default:
		return nil, fmt.Errorf("engine '%s' does not support history loading yet", engine)
	}
}

func (s *Service) loadAntigravityHistory(ctx context.Context, conversationID string, limit int) ([]HistoryMessage, error) {
	transcriptPath := filepath.Join(s.home, ".gemini", "antigravity", "brain", conversationID, ".system_generated", "logs", "transcript.jsonl")
	file, err := os.Open(transcriptPath)
	if err != nil {
		cliPath := filepath.Join(s.home, ".gemini", "antigravity-cli", "brain", conversationID, ".system_generated", "logs", "transcript.jsonl")
		file, err = os.Open(cliPath)
		if err != nil {
			return nil, fmt.Errorf("transcript not found: %w", err)
		}
	}
	defer file.Close()

	var allMessages []HistoryMessage
	scanner := bufio.NewScanner(file)
	// Allow large lines (up to 4MB for large outputs)
	buf := make([]byte, 64*1024)
	scanner.Buffer(buf, 4*1024*1024)

	type transcriptEntry struct {
		StepIndex int    `json:"step_index"`
		Type      string `json:"type"`
		Content   string `json:"content"`
		CreatedAt string `json:"created_at"`
	}

	for scanner.Scan() {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}

		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		var entry transcriptEntry
		if err := json.Unmarshal([]byte(line), &entry); err != nil {
			continue
		}

		var ts int64
		if t, err := time.Parse(time.RFC3339Nano, entry.CreatedAt); err == nil {
			ts = t.UnixMilli()
		} else if t, err := time.Parse(time.RFC3339, entry.CreatedAt); err == nil {
			ts = t.UnixMilli()
		}

		switch entry.Type {
		case "USER_INPUT":
			content := entry.Content
			if match := userRequestRegex.FindStringSubmatch(content); len(match) > 1 {
				content = strings.TrimSpace(match[1])
			}
			content = strings.TrimSpace(content)
			if content != "" {
				allMessages = append(allMessages, HistoryMessage{
					ID:          fmt.Sprintf("user-%d", entry.StepIndex),
					Role:        "user",
					Content:     content,
					TimestampMs: ts,
				})
			}

		case "PLANNER_RESPONSE":
			content := strings.TrimSpace(entry.Content)
			if content != "" {
				allMessages = append(allMessages, HistoryMessage{
					ID:          fmt.Sprintf("asst-%d", entry.StepIndex),
					Role:        "assistant",
					Content:     content,
					TimestampMs: ts,
				})
			}
		}
	}

	if len(allMessages) > limit {
		allMessages = allMessages[len(allMessages)-limit:]
	}
	return allMessages, nil
}

func (s *Service) loadClaudeHistory(ctx context.Context, conversationID string, limit int) ([]HistoryMessage, error) {
	projectsDir := filepath.Join(s.home, ".claude", "projects")
	pattern := filepath.Join(projectsDir, "*", conversationID+".jsonl")
	matches, err := filepath.Glob(pattern)
	if err != nil || len(matches) == 0 {
		return nil, fmt.Errorf("claude transcript not found for conversation %s", conversationID)
	}

	file, err := os.Open(matches[0])
	if err != nil {
		return nil, err
	}
	defer file.Close()

	var allMessages []HistoryMessage
	scanner := bufio.NewScanner(file)
	buf := make([]byte, 64*1024)
	scanner.Buffer(buf, 4*1024*1024)

	type claudeEntry struct {
		Type      string `json:"type"`
		Timestamp string `json:"timestamp"`
		Message   struct {
			Role    string          `json:"role"`
			Content json.RawMessage `json:"content"`
		} `json:"message"`
	}

	step := 0
	for scanner.Scan() {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}

		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		var entry claudeEntry
		if err := json.Unmarshal([]byte(line), &entry); err != nil {
			continue
		}

		var ts int64
		if t, err := time.Parse(time.RFC3339Nano, entry.Timestamp); err == nil {
			ts = t.UnixMilli()
		} else if t, err := time.Parse(time.RFC3339, entry.Timestamp); err == nil {
			ts = t.UnixMilli()
		}

		step++
		if entry.Type == "user" {
			var strContent string
			if err := json.Unmarshal(entry.Message.Content, &strContent); err == nil {
				strContent = strings.TrimSpace(strContent)
				if strContent != "" {
					allMessages = append(allMessages, HistoryMessage{
						ID:          fmt.Sprintf("user-%d", step),
						Role:        "user",
						Content:     strContent,
						TimestampMs: ts,
					})
				}
			}
		} else if entry.Type == "assistant" {
			type block struct {
				Type string `json:"type"`
				Text string `json:"text"`
			}
			var blocks []block
			if err := json.Unmarshal(entry.Message.Content, &blocks); err == nil {
				var sb strings.Builder
				for _, b := range blocks {
					if b.Type == "text" && strings.TrimSpace(b.Text) != "" {
						if sb.Len() > 0 {
							sb.WriteString("\n\n")
						}
						sb.WriteString(strings.TrimSpace(b.Text))
					}
				}
				if sb.Len() > 0 {
					allMessages = append(allMessages, HistoryMessage{
						ID:          fmt.Sprintf("asst-%d", step),
						Role:        "assistant",
						Content:     sb.String(),
						TimestampMs: ts,
					})
				}
			}
		}
	}

	if len(allMessages) > limit {
		allMessages = allMessages[len(allMessages)-limit:]
	}
	return allMessages, nil
}

func (s *Service) loadCodexHistory(ctx context.Context, conversationID string, limit int) ([]HistoryMessage, error) {
	var rolloutPath string
	dbFiles, _ := filepath.Glob(filepath.Join(s.home, ".codex", "state_*.sqlite"))
	for _, dbFile := range dbFiles {
		db, err := openReadOnly(dbFile)
		if err == nil {
			_ = db.QueryRowContext(ctx, "SELECT rollout_path FROM threads WHERE id = ?", conversationID).Scan(&rolloutPath)
			_ = db.Close()
			if rolloutPath != "" {
				break
			}
		}
	}
	if rolloutPath == "" {
		matches, _ := filepath.Glob(filepath.Join(s.home, ".codex", "sessions", "*", "*", "*", "*"+conversationID+"*.jsonl"))
		if len(matches) == 0 {
			matches, _ = filepath.Glob(filepath.Join(s.home, ".codex", "archived_sessions", "*"+conversationID+"*.jsonl"))
		}
		if len(matches) > 0 {
			rolloutPath = matches[0]
		}
	}
	if rolloutPath == "" {
		return nil, fmt.Errorf("codex rollout not found for thread %s", conversationID)
	}

	file, err := os.Open(rolloutPath)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	var allMessages []HistoryMessage
	scanner := bufio.NewScanner(file)
	buf := make([]byte, 64*1024)
	scanner.Buffer(buf, 4*1024*1024)

	type codexLine struct {
		Type      string          `json:"type"`
		Timestamp string          `json:"timestamp"`
		Payload   json.RawMessage `json:"payload"`
	}

	step := 0
	for scanner.Scan() {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}
		var cl codexLine
		if err := json.Unmarshal([]byte(line), &cl); err != nil {
			continue
		}

		var ts int64
		if t, err := time.Parse(time.RFC3339Nano, cl.Timestamp); err == nil {
			ts = t.UnixMilli()
		} else if t, err := time.Parse(time.RFC3339, cl.Timestamp); err == nil {
			ts = t.UnixMilli()
		}

		if cl.Type == "response_item" {
			var respItem struct {
				Type    string `json:"type"`
				Role    string `json:"role"`
				Content []struct {
					Type string `json:"type"`
					Text string `json:"text"`
				} `json:"content"`
			}
			if json.Unmarshal(cl.Payload, &respItem) == nil && respItem.Type == "message" && respItem.Role == "user" {
				for _, c := range respItem.Content {
					text := strings.TrimSpace(c.Text)
					if text != "" && !strings.HasPrefix(text, "<") {
						step++
						allMessages = append(allMessages, HistoryMessage{
							ID:          fmt.Sprintf("user-%d", step),
							Role:        "user",
							Content:     text,
							TimestampMs: ts,
						})
					}
				}
			}
		} else if cl.Type == "event_msg" {
			var evtMsg struct {
				Type string `json:"type"`
				Item struct {
					Type    string `json:"type"`
					Content []struct {
						Type string `json:"type"`
						Text string `json:"text"`
					} `json:"content"`
					Text string `json:"text"`
				} `json:"item"`
			}
			if json.Unmarshal(cl.Payload, &evtMsg) == nil && evtMsg.Type == "item_completed" && evtMsg.Item.Type == "AgentMessage" {
				var sb strings.Builder
				for _, c := range evtMsg.Item.Content {
					if strings.TrimSpace(c.Text) != "" {
						if sb.Len() > 0 {
							sb.WriteString("\n\n")
						}
						sb.WriteString(strings.TrimSpace(c.Text))
					}
				}
				if sb.Len() == 0 && strings.TrimSpace(evtMsg.Item.Text) != "" {
					sb.WriteString(strings.TrimSpace(evtMsg.Item.Text))
				}
				if sb.Len() > 0 {
					step++
					allMessages = append(allMessages, HistoryMessage{
						ID:          fmt.Sprintf("asst-%d", step),
						Role:        "assistant",
						Content:     sb.String(),
						TimestampMs: ts,
					})
				}
			}
		}
	}

	if len(allMessages) > limit {
		allMessages = allMessages[len(allMessages)-limit:]
	}
	return allMessages, nil
}
