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
	Thinking    string `json:"thinking,omitempty"`
	TimestampMs int64  `json:"timestampMs,omitempty"`
}

type ConversationDetails struct {
	Messages    []HistoryMessage `json:"messages"`
	ActiveModel string           `json:"activeModel,omitempty"`
	TokenUsage  *TokenUsage      `json:"tokenUsage,omitempty"`
}

type TokenUsage struct {
	InputTokens     int `json:"inputTokens"`
	CacheReadTokens int `json:"cacheReadTokens"`
	OutputTokens    int `json:"outputTokens"`
	TotalTokens     int `json:"totalTokens"`
	ContextLimit    int `json:"contextLimit"`
}

var (
	userRequestRegex    = regexp.MustCompile(`(?s)<USER_REQUEST>(.*?)</USER_REQUEST>`)
	modelSelectionRegex = regexp.MustCompile("Model Selection`.*?to\\s+([^\\.\\n<]+)")
)

// LoadConversationDetails reads conversation dialogue, active model, and token usage from disk.
func (s *Service) LoadConversationDetails(ctx context.Context, engine Engine, conversationID string, limit int) (ConversationDetails, error) {
	if limit <= 0 {
		limit = 30
	}
	switch engine {
	case EngineAntigravity:
		return s.loadAntigravityDetails(ctx, conversationID, limit)
	case EngineClaudeCode:
		msgs, err := s.loadClaudeHistory(ctx, conversationID, limit)
		return ConversationDetails{Messages: msgs}, err
	case EngineCodex:
		msgs, err := s.loadCodexHistory(ctx, conversationID, limit)
		return ConversationDetails{Messages: msgs}, err
	default:
		return ConversationDetails{}, fmt.Errorf("engine '%s' does not support history loading yet", engine)
	}
}

// LoadHistory reads recent conversation dialogue from the engine's store on disk.
func (s *Service) LoadHistory(ctx context.Context, engine Engine, conversationID string, limit int) ([]HistoryMessage, error) {
	details, err := s.LoadConversationDetails(ctx, engine, conversationID, limit)
	return details.Messages, err
}

func (s *Service) loadAntigravityDetails(ctx context.Context, conversationID string, limit int) (ConversationDetails, error) {
	transcriptPath := filepath.Join(s.home, ".gemini", "antigravity", "brain", conversationID, ".system_generated", "logs", "transcript.jsonl")
	file, err := os.Open(transcriptPath)
	if err != nil {
		file, err = os.Open(filepath.Join(s.home, ".gemini", "antigravity-cli", "brain", conversationID, ".system_generated", "logs", "transcript.jsonl"))
		if err != nil {
			return ConversationDetails{}, fmt.Errorf("transcript not found: %w", err)
		}
	}
	defer file.Close()

	var allMessages []HistoryMessage
	var lastModel string
	var lastInputTokens, lastCacheTokens, lastOutputTokens int
	scanner := bufio.NewScanner(file)
	buf := make([]byte, 64*1024)
	scanner.Buffer(buf, 4*1024*1024)

	type transcriptEntry struct {
		StepIndex       int    `json:"step_index"`
		Type            string `json:"type"`
		Content         string `json:"content"`
		CreatedAt       string `json:"created_at"`
		Thinking        string `json:"thinking"`
		InputTokens     int    `json:"input_tokens"`
		CacheReadTokens int    `json:"cache_read_tokens"`
		OutputTokens    int    `json:"output_tokens"`
		ToolCalls       []struct {
			Name string         `json:"name"`
			Args map[string]any `json:"args"`
		} `json:"tool_calls"`
	}

	for scanner.Scan() {
		if ctx.Err() != nil {
			return ConversationDetails{}, ctx.Err()
		}

		line := strings.TrimSpace(scanner.Text())
		if line == "" {
			continue
		}

		var entry transcriptEntry
		if err := json.Unmarshal([]byte(line), &entry); err != nil {
			continue
		}

		if entry.Type == "USER_INPUT" && strings.Contains(entry.Content, "Model Selection") {
			if m := modelSelectionRegex.FindStringSubmatch(entry.Content); len(m) > 1 {
				lastModel = strings.TrimSpace(m[1])
			}
		}

		if entry.Type == "PLANNER_RESPONSE" && (entry.InputTokens > 0 || entry.CacheReadTokens > 0) {
			lastInputTokens = entry.InputTokens
			lastCacheTokens = entry.CacheReadTokens
			lastOutputTokens = entry.OutputTokens
		}

		ts := parseTimestamp(entry.CreatedAt)

		switch entry.Type {
		case "USER_INPUT":
			content := entry.Content
			if match := userRequestRegex.FindStringSubmatch(content); len(match) > 1 {
				content = strings.TrimSpace(match[1])
			}
			content = strings.TrimSpace(content)
			if content != "" {
				allMessages = append(allMessages, HistoryMessage{
					ID:          fmt.Sprintf("user-%d-%d", entry.StepIndex, len(allMessages)),
					Role:        "user",
					Content:     content,
					TimestampMs: ts,
				})
			}

		case "PLANNER_RESPONSE":
			content := strings.TrimSpace(entry.Content)
			if content == "None" {
				content = ""
			}
			thinking := strings.TrimSpace(entry.Thinking)
			if content == "" && len(entry.ToolCalls) > 0 {
				var summaries []string
				for _, tc := range entry.ToolCalls {
					if tc.Name != "" {
						desc := tc.Name
						if sum, ok := tc.Args["toolSummary"].(string); ok && sum != "" {
							desc += ": " + strings.Trim(sum, "\"")
						} else if cmd, ok := tc.Args["CommandLine"].(string); ok && cmd != "" {
							desc += ": `" + strings.Trim(cmd, "\"") + "`"
						} else if path, ok := tc.Args["Path"].(string); ok && path != "" {
							desc += ": " + strings.Trim(path, "\"")
						}
						summaries = append(summaries, "• "+desc)
					}
				}
				if len(summaries) > 0 {
					content = "🔧 *Thực thi công cụ:*\n" + strings.Join(summaries, "\n")
				}
			}
			if content != "" || thinking != "" {
				if len(allMessages) > 0 && allMessages[len(allMessages)-1].Role == "assistant" &&
					strings.HasPrefix(content, "🔧 *Thực thi công cụ:*") &&
					strings.HasPrefix(allMessages[len(allMessages)-1].Content, "🔧 *Thực thi công cụ:*") {
					allMessages[len(allMessages)-1].Content += "\n" + strings.TrimPrefix(content, "🔧 *Thực thi công cụ:*\n")
					allMessages[len(allMessages)-1].TimestampMs = ts
					if thinking != "" {
						if allMessages[len(allMessages)-1].Thinking != "" {
							allMessages[len(allMessages)-1].Thinking += "\n\n" + thinking
						} else {
							allMessages[len(allMessages)-1].Thinking = thinking
						}
					}
				} else {
					allMessages = append(allMessages, HistoryMessage{
						ID:          fmt.Sprintf("asst-%d-%d", entry.StepIndex, len(allMessages)),
						Role:        "assistant",
						Content:     content,
						Thinking:    thinking,
						TimestampMs: ts,
					})
				}
			}
		}
	}

	if len(allMessages) > limit {
		allMessages = allMessages[len(allMessages)-limit:]
	}

	return ConversationDetails{
		Messages:    allMessages,
		ActiveModel: lastModel,
		TokenUsage:  determineTokenUsage(lastModel, lastInputTokens, lastCacheTokens, lastOutputTokens),
	}, nil
}

func determineTokenUsage(model string, in, cache, out int) *TokenUsage {
	if in <= 0 && cache <= 0 {
		return nil
	}
	limit := 1000000
	lower := strings.ToLower(model)
	if strings.Contains(lower, "claude") {
		limit = 200000
	} else if strings.Contains(lower, "gpt") {
		limit = 128000
	}
	return &TokenUsage{
		InputTokens: in, CacheReadTokens: cache, OutputTokens: out,
		TotalTokens: in + cache, ContextLimit: limit,
	}
}

func (s *Service) loadClaudeHistory(ctx context.Context, conversationID string, limit int) ([]HistoryMessage, error) {
	matches, err := filepath.Glob(filepath.Join(s.home, ".claude", "projects", "*", conversationID+".jsonl"))
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

		ts := parseTimestamp(entry.Timestamp)

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
		if matches, _ := filepath.Glob(filepath.Join(s.home, ".codex", "sessions", "*", "*", "*", "*"+conversationID+"*.jsonl")); len(matches) > 0 {
			rolloutPath = matches[0]
		} else if archived, _ := filepath.Glob(filepath.Join(s.home, ".codex", "archived_sessions", "*"+conversationID+"*.jsonl")); len(archived) > 0 {
			rolloutPath = archived[0]
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

		ts := parseTimestamp(cl.Timestamp)

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

func parseTimestamp(raw string) int64 {
	if t, err := time.Parse(time.RFC3339Nano, raw); err == nil {
		return t.UnixMilli()
	}
	if t, err := time.Parse(time.RFC3339, raw); err == nil {
		return t.UnixMilli()
	}
	return 0
}
