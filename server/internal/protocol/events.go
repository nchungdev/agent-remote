package protocol

import (
	"encoding/json"
	"time"
)

// Helper to create and marshal a gateway event
func marshalEvent(eventType string, data map[string]any) []byte {
	payload := make(map[string]any, len(data)+1)
	payload["type"] = eventType
	for k, v := range data {
		payload[k] = v
	}
	bytes, _ := json.Marshal(payload)
	return bytes
}

func SessionInit(sessionID string, agentType AgentType, status string) []byte {
	return marshalEvent("SESSION_INIT", map[string]any{
		"sessionId": sessionID,
		"agentType": string(agentType),
		"status":    status,
	})
}

func AgentStateEvent(state AgentState, details string) []byte {
	data := map[string]any{
		"state": string(state),
	}
	if details != "" {
		data["details"] = details
	}
	return marshalEvent("AGENT_STATE", data)
}

func TextChunk(text string) []byte {
	return marshalEvent("TEXT_CHUNK", map[string]any{
		"text": text,
	})
}

func ThinkingChunk(thought string) []byte {
	return marshalEvent("THINKING_CHUNK", map[string]any{
		"thought": thought,
	})
}

func ToolCall(callID, toolName string, args map[string]any) []byte {
	return marshalEvent("TOOL_CALL", map[string]any{
		"callId":   callID,
		"toolName": toolName,
		"args":     args,
	})
}

func ApprovalRequired(callID, toolName, summary string, dangerous bool) []byte {
	return marshalEvent("APPROVAL_REQUIRED", map[string]any{
		"callId":    callID,
		"toolName":  toolName,
		"summary":   summary,
		"dangerous": dangerous,
	})
}

func ToolResult(callID, toolName, output string, isError bool) []byte {
	return marshalEvent("TOOL_RESULT", map[string]any{
		"callId":   callID,
		"toolName": toolName,
		"output":   output,
		"isError":  isError,
	})
}

// MessageEnd closes a prompt's message. conversationID lets the client continue the
// same conversation with its next prompt.
func MessageEnd(messageID, conversationID string) []byte {
	data := map[string]any{"messageId": messageID}
	if conversationID != "" {
		data["conversationId"] = conversationID
	}
	return marshalEvent("MESSAGE_END", data)
}

func ErrorEvent(message string) []byte {
	return marshalEvent("ERROR", map[string]any{
		"message": message,
	})
}

func Pong() []byte {
	return marshalEvent("PONG", map[string]any{
		"timestamp": time.Now().UnixMilli(),
	})
}

// CatalogEvent carries one engine's models, projects and conversations. One event per engine keeps
// each message small enough for a WebRTC DataChannel frame.
func CatalogEvent(engineCatalog any) []byte {
	bytes, _ := json.Marshal(map[string]any{
		"type":    "CATALOG",
		"catalog": engineCatalog,
	})
	return bytes
}

func ConversationHistoryEvent(engine string, conversationID string, messages any, activeModel string, tokenUsage any) []byte {
	payload := map[string]any{
		"engine":         engine,
		"conversationId": conversationID,
		"messages":       messages,
	}
	if activeModel != "" {
		payload["activeModel"] = activeModel
	}
	if tokenUsage != nil {
		payload["tokenUsage"] = tokenUsage
	}
	return marshalEvent("CONVERSATION_HISTORY", payload)
}

func ParentSessionEvent(session any) []byte {
	return marshalEvent("PARENT_SESSION_STATE", map[string]any{
		"session": session,
	})
}

func WriterTransferredEvent(sessionID string, newWriter string, revision int64) []byte {
	return marshalEvent("WRITER_TRANSFERRED", map[string]any{
		"sessionId": sessionID,
		"newWriter": newWriter,
		"revision":  revision,
	})
}

func ConsultBranchEvent(branch any) []byte {
	return marshalEvent("CONSULT_BRANCH_RESULT", map[string]any{
		"branch": branch,
	})
}
