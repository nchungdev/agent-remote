package gateway

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
	"app.duinch.agentremote/server/internal/session"
	"github.com/gorilla/websocket"
)

func TestParentSessionGatewayWebSocketFlow(t *testing.T) {
	srv := NewServer("127.0.0.1", 0, "")
	// Configure fake engines in session registry for deterministic integration test
	reg := session.DefaultRegistry()
	fakeCodex := session.NewFakeEngine(protocol.AgentTypeCodex, session.EngineCapability{
		SupportsAppServer:     true,
		SupportsNativeResume:  true,
		SupportsConsultBranch: true,
		HasReadOnlySandbox:    true,
	})
	fakeClaude := session.NewFakeEngine(protocol.AgentTypeClaudeCode, session.EngineCapability{
		SupportsNativeResume:  true,
		SupportsConsultBranch: true,
		HasReadOnlySandbox:    true,
	})
	reg.Register(fakeCodex)
	reg.Register(fakeClaude)

	testServer := httptest.NewServer(http.HandlerFunc(srv.HandleWS))
	defer testServer.Close()

	wsURL := "ws" + strings.TrimPrefix(testServer.URL, "http") + "/"
	ws, _, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("failed to dial websocket: %v", err)
	}
	defer ws.Close()

	// 1. Read initial SESSION_INIT event
	_, initMsg, err := ws.ReadMessage()
	if err != nil {
		t.Fatalf("failed to read initial session event: %v", err)
	}
	var initEv map[string]any
	_ = json.Unmarshal(initMsg, &initEv)
	if initEv["type"] != "SESSION_INIT" {
		t.Errorf("expected SESSION_INIT, got: %s", string(initMsg))
	}

	// 2. Send PARENT_SESSION_INIT
	initCmd := protocol.ClientMessage{
		Action:          protocol.ActionParentSessionInit,
		ParentSessionID: "ps-integration-test",
		AgentType:       protocol.AgentTypeCodex,
		Model:           "gpt-4o",
		Workspace:       t.TempDir(),
	}
	_ = ws.WriteJSON(initCmd)

	// Read PARENT_SESSION_STATE
	_, stateMsg, err := ws.ReadMessage()
	if err != nil {
		t.Fatalf("failed to read parent session state: %v", err)
	}
	var stateEv map[string]any
	_ = json.Unmarshal(stateMsg, &stateEv)
	if stateEv["type"] != "PARENT_SESSION_STATE" {
		t.Fatalf("expected PARENT_SESSION_STATE, got: %s", string(stateMsg))
	}

	// 3. Send TRANSFER_WRITER (from Codex to Claude)
	transferCmd := protocol.ClientMessage{
		Action:           protocol.ActionTransferWriter,
		ParentSessionID:  "ps-integration-test",
		TargetAgentID:    "claude-agent-1",
		AgentType:        protocol.AgentTypeClaudeCode,
		Model:            "claude-3-5-sonnet",
		ExpectedRevision: 1,
	}
	_ = ws.WriteJSON(transferCmd)

	// Read WRITER_TRANSFERRED event
	_, transferMsg, err := ws.ReadMessage()
	if err != nil {
		t.Fatalf("failed to read transfer event: %v", err)
	}
	var transferEv map[string]any
	_ = json.Unmarshal(transferMsg, &transferEv)
	if transferEv["type"] != "WRITER_TRANSFERRED" {
		t.Fatalf("expected WRITER_TRANSFERRED, got: %s", string(transferMsg))
	}
	if transferEv["newWriter"] != "claude-agent-1" || fmt.Sprintf("%v", transferEv["revision"]) != "2" {
		t.Fatalf("unexpected transfer event payload: %+v", transferEv)
	}

	// Drain the updated PARENT_SESSION_STATE
	_, _, _ = ws.ReadMessage()

	// 4. Send CONSULT_BRANCH (@agent:btw)
	fakeCodex.QueueResponse("Architecture looks solid.")
	consultCmd := protocol.ClientMessage{
		Action:          protocol.ActionConsultBranch,
		ParentSessionID: "ps-integration-test",
		AgentType:       protocol.AgentTypeCodex,
		Query:           "How does this look?",
	}
	_ = ws.WriteJSON(consultCmd)

	// Read until CONSULT_BRANCH_RESULT
	var consultResultEv map[string]any
	for i := 0; i < 5; i++ {
		_ = ws.SetReadDeadline(time.Now().Add(2 * time.Second))
		_, msgBytes, err := ws.ReadMessage()
		if err != nil {
			break
		}
		var parsed map[string]any
		_ = json.Unmarshal(msgBytes, &parsed)
		if parsed["type"] == "CONSULT_BRANCH_RESULT" {
			consultResultEv = parsed
			break
		}
	}

	if consultResultEv == nil {
		t.Fatalf("never received CONSULT_BRANCH_RESULT event")
	}
}
