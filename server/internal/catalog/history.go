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
	default:
		return nil, fmt.Errorf("engine '%s' does not support history loading yet", engine)
	}
}

func (s *Service) loadAntigravityHistory(ctx context.Context, conversationID string, limit int) ([]HistoryMessage, error) {
	transcriptPath := filepath.Join(s.home, ".gemini", "antigravity", "brain", conversationID, ".system_generated", "logs", "transcript.jsonl")
	file, err := os.Open(transcriptPath)
	if err != nil {
		return nil, fmt.Errorf("transcript not found: %w", err)
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
