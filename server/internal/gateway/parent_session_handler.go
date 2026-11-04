package gateway

import (
	"context"
	"fmt"
	"log"
	"strings"
	"time"

	"app.duinch.agentremote/server/internal/protocol"
	"app.duinch.agentremote/server/internal/session"
)

// handleParentSessionAction dispatches parent session and multi-agent coordination commands (M3).
func (s *Server) handleParentSessionAction(msg protocol.ClientMessage, emit func([]byte)) bool {
	if s.sessionMgr == nil {
		return false
	}

	switch msg.Action {
	case protocol.ActionParentSessionInit:
		go func() {
			sessionID := strings.TrimSpace(msg.ParentSessionID)
			if sessionID == "" {
				sessionID = fmt.Sprintf("ps_%d", time.Now().UnixMilli())
			}

			// If session already exists, return current state
			if existing, err := s.sessionMgr.GetSession(sessionID); err == nil && existing != nil {
				emit(protocol.ParentSessionEvent(existing))
				return
			}

			engine := msg.AgentType
			if engine == "" {
				engine = s.currentAgentType
			}

			ws := session.WorkspaceRef{
				RootPath: msg.Workspace,
			}

			sess, err := s.sessionMgr.CreateParentSession(
				sessionID,
				"Parent Session",
				ws,
				engine,
				msg.Model,
			)
			if err != nil {
				emit(protocol.ErrorEvent(fmt.Sprintf("Khởi tạo session cha thất bại: %v", err)))
				return
			}

			log.Printf("[Gateway] Đã khởi tạo parent session: %s (writer: %s)", sess.ID, sess.WriterLease.WriterAgentID)
			emit(protocol.ParentSessionEvent(sess))
		}()
		return true

	case protocol.ActionTransferWriter:
		go func() {
			if msg.ParentSessionID == "" {
				emit(protocol.ErrorEvent("Thiếu parentSessionId để chuyển quyền ghi"))
				return
			}

			targetAgentID := msg.TargetAgentID
			if targetAgentID == "" {
				targetAgentID = fmt.Sprintf("%s-agent-1", msg.AgentType)
			}

			lease, err := s.sessionMgr.SwitchWriter(
				msg.ParentSessionID,
				targetAgentID,
				msg.AgentType,
				msg.Model,
				msg.ExpectedRevision,
			)
			if err != nil {
				emit(protocol.ErrorEvent(fmt.Sprintf("Chuyển quyền ghi thất bại: %v", err)))
				return
			}

			log.Printf("[Gateway] Chuyển writer session %s sang %s (rev %d)", msg.ParentSessionID, targetAgentID, lease.Revision)
			emit(protocol.WriterTransferredEvent(msg.ParentSessionID, lease.WriterAgentID, lease.Revision))

			if sess, err := s.sessionMgr.GetSession(msg.ParentSessionID); err == nil {
				emit(protocol.ParentSessionEvent(sess))
			}
		}()
		return true

	case protocol.ActionConsultBranch:
		go func() {
			if msg.ParentSessionID == "" {
				emit(protocol.ErrorEvent("Thiếu parentSessionId để hỏi ý kiến"))
				return
			}
			query := strings.TrimSpace(msg.Query)
			if query == "" {
				query = strings.TrimSpace(msg.Prompt)
			}
			if query == "" {
				emit(protocol.ErrorEvent("Nội dung câu hỏi consult không được để trống"))
				return
			}

			engine := msg.AgentType
			if engine == "" {
				engine = protocol.AgentTypeCodex
			}

			branch, err := s.sessionMgr.Consult(
				context.Background(),
				msg.ParentSessionID,
				engine,
				msg.Model,
				query,
				session.BuildContextOptions{},
				emit,
			)
			if err != nil {
				emit(protocol.ErrorEvent(fmt.Sprintf("Consult thất bại: %v", err)))
				return
			}

			log.Printf("[Gateway] Hoàn tất consult branch %s cho session %s", branch.BranchID, msg.ParentSessionID)
			emit(protocol.ConsultBranchEvent(branch))
		}()
		return true

	default:
		return false
	}
}
