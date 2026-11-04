package webrtc

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"sync"
	"sync/atomic"

	"app.duinch.agentremote/server/internal/adapter"
	"app.duinch.agentremote/server/internal/catalog"
	"app.duinch.agentremote/server/internal/protocol"
	"github.com/nchungdev/remote-tunnel/go/signaling"
	"github.com/pion/webrtc/v4"
)

const FixedSignalingRoom = "default"

type Hub struct {
	roomCode         string
	sigHub           *signaling.MessageHub
	sessionID        string
	currentAgentType protocol.AgentType
	adapters         map[protocol.AgentType]adapter.Adapter
	catalog          *catalog.Service
	mu               sync.Mutex
	peers            map[string]*webrtc.PeerConnection
	nextConnID       atomic.Uint64
	onConnected      func()
	onDisconnected   func()
}

func NewHub(roomCode string, sessionID string, adapters map[protocol.AgentType]adapter.Adapter, catalog *catalog.Service, sigHub *signaling.MessageHub) *Hub {
	if roomCode == "" {
		roomCode = FixedSignalingRoom
	}
	return &Hub{
		roomCode:         roomCode,
		sessionID:        sessionID,
		currentAgentType: protocol.AgentTypeMock,
		adapters:         adapters,
		catalog:          catalog,
		sigHub:           sigHub,
		peers:            make(map[string]*webrtc.PeerConnection),
	}
}

func (h *Hub) SetCallbacks(onConnected, onDisconnected func()) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.onConnected = onConnected
	h.onDisconnected = onDisconnected
}

func (h *Hub) GetRoomCode() string {
	return FixedSignalingRoom
}

func (h *Hub) UpdateRoomCode(_ string) {
	// Fixed room: no-op for backward compatibility
}

func (h *Hub) IsConnected() bool {
	h.mu.Lock()
	defer h.mu.Unlock()
	for _, pc := range h.peers {
		if pc.ConnectionState() == webrtc.PeerConnectionStateConnected {
			return true
		}
	}
	return false
}

func (h *Hub) Start() error {
	if h.sigHub != nil {
		ch, err := h.sigHub.Register(FixedSignalingRoom, "host")
		if err == nil {
			go func() {
				for msg := range ch {
					h.handleSignal(msg)
				}
			}()
		}
	}
	return nil
}

func (h *Hub) handleSignal(msg signaling.SignalMessage) {
	switch msg.Type {
	case "offer":
		h.handleOffer(msg)
	case "candidate":
		h.handleCandidate(msg)
	case "ping":
		if h.sigHub != nil {
			h.sigHub.Broadcast(FixedSignalingRoom, signaling.SignalMessage{
				Type:      "pong",
				Sender:    "host",
				Recipient: msg.Sender,
			})
		}
	}
}

func (h *Hub) handleOffer(msg signaling.SignalMessage) {
	peerID := msg.Sender
	if peerID == "" {
		peerID = "client"
	}
	connID := h.nextConnID.Add(1)

	h.mu.Lock()
	if oldPC := h.peers[peerID]; oldPC != nil && oldPC.ConnectionState() != webrtc.PeerConnectionStateConnected {
		_ = oldPC.Close()
		delete(h.peers, peerID)
	}
	h.mu.Unlock()

	config := webrtc.Configuration{
		ICEServers: []webrtc.ICEServer{
			{URLs: []string{
				"stun:stun.l.google.com:19302",
				"stun:stun1.l.google.com:19302",
			}},
		},
	}

	pc, err := webrtc.NewPeerConnection(config)
	if err != nil {
		log.Printf("[WebRTC] Lỗi tạo PeerConnection (peer=%s, conn=%d): %v", peerID, connID, err)
		return
	}

	h.mu.Lock()
	h.peers[peerID] = pc
	h.mu.Unlock()

	pc.OnICECandidate(func(c *webrtc.ICECandidate) {
		if c == nil {
			return
		}
		candJSON, err := json.Marshal(c.ToJSON())
		if err != nil {
			return
		}
		if h.sigHub != nil {
			h.sigHub.Broadcast(FixedSignalingRoom, signaling.SignalMessage{
				Type:      "candidate",
				Sender:    "host",
				Recipient: peerID,
				Payload:   candJSON,
			})
		}
	})

	pc.OnConnectionStateChange(func(state webrtc.PeerConnectionState) {
		log.Printf("[WebRTC] Trạng thái P2P (peer=%s, conn=%d): %s", peerID, connID, state)
		if state == webrtc.PeerConnectionStateConnected {
			h.mu.Lock()
			cb := h.onConnected
			h.mu.Unlock()
			if cb != nil {
				cb()
			}
		} else if state == webrtc.PeerConnectionStateDisconnected || state == webrtc.PeerConnectionStateFailed || state == webrtc.PeerConnectionStateClosed {
			h.mu.Lock()
			if h.peers[peerID] == pc {
				delete(h.peers, peerID)
			}
			cb := h.onDisconnected
			h.mu.Unlock()
			_ = pc.Close()
			if cb != nil {
				cb()
			}
		}
	})

	pc.OnDataChannel(func(dc *webrtc.DataChannel) {
		log.Printf("[WebRTC] Nhận DataChannel '%s' từ peer=%s", dc.Label(), peerID)
		h.setupDataChannel(dc)
	})

	var sdp webrtc.SessionDescription
	if err := json.Unmarshal(msg.Payload, &sdp); err != nil {
		log.Printf("[WebRTC] Lỗi unmarshal SDP offer từ peer=%s: %v", peerID, err)
		return
	}

	if err := pc.SetRemoteDescription(sdp); err != nil {
		log.Printf("[WebRTC] Lỗi set remote description (peer=%s): %v", peerID, err)
		return
	}

	answer, err := pc.CreateAnswer(nil)
	if err != nil {
		log.Printf("[WebRTC] Lỗi create answer (peer=%s): %v", peerID, err)
		return
	}

	if err := pc.SetLocalDescription(answer); err != nil {
		log.Printf("[WebRTC] Lỗi set local description (peer=%s): %v", peerID, err)
		return
	}

	answerJSON, err := json.Marshal(answer)
	if err != nil {
		return
	}

	if h.sigHub != nil {
		h.sigHub.Broadcast(FixedSignalingRoom, signaling.SignalMessage{
			Type:      "answer",
			Sender:    "host",
			Recipient: peerID,
			Payload:   answerJSON,
		})
	}
	log.Printf("[WebRTC] Đã gửi SDP Answer tới peer=%s thành công", peerID)
}

func (h *Hub) handleCandidate(msg signaling.SignalMessage) {
	peerID := msg.Sender
	h.mu.Lock()
	pc := h.peers[peerID]
	h.mu.Unlock()
	if pc == nil {
		return
	}

	var init webrtc.ICECandidateInit
	if err := json.Unmarshal(msg.Payload, &init); err != nil {
		return
	}
	_ = pc.AddICECandidate(init)
}

func (h *Hub) setupDataChannel(dc *webrtc.DataChannel) {
	dc.OnOpen(func() {
		log.Println("[WebRTC] Kênh P2P DataChannel đã mở thành công! Sẵn sàng stream dữ liệu.")
		ad := h.getAdapter()
		_ = ad.Initialize()
		initBytes := protocol.SessionInit(h.sessionID, h.currentAgentType, "READY")
		_ = dc.SendText(string(initBytes))
	})

	dc.OnMessage(func(msg webrtc.DataChannelMessage) {
		var clientMsg protocol.ClientMessage
		if err := json.Unmarshal(msg.Data, &clientMsg); err != nil {
			errBytes := protocol.ErrorEvent("Payload không phải JSON hợp lệ")
			_ = dc.SendText(string(errBytes))
			return
		}

		h.handleClientAction(dc, clientMsg)
	})
}

func (h *Hub) getAdapter() adapter.Adapter {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.adapters[h.currentAgentType]
}

func (h *Hub) handleClientAction(dc *webrtc.DataChannel, msg protocol.ClientMessage) {
	emit := func(event []byte) {
		_ = dc.SendText(string(event))
	}

	log.Printf("[WebRTC] Nhận action: %s", msg.Action)

	switch msg.Action {
	case "PING":
		emit(protocol.Pong())

	case "SELECT_AGENT":
		h.mu.Lock()
		ad, exists := h.adapters[msg.AgentType]
		if !exists {
			h.mu.Unlock()
			emit(protocol.ErrorEvent(fmt.Sprintf("Agent '%s' không hợp lệ", msg.AgentType)))
			return
		}
		h.currentAgentType = msg.AgentType
		h.mu.Unlock()

		_ = ad.Initialize()
		log.Printf("[WebRTC] Đã chuyển sang agent: %s", msg.AgentType)
		emit(protocol.SessionInit(h.sessionID, h.currentAgentType, "READY"))

	case "LIST_CATALOG":
		if h.catalog != nil {
			go h.catalog.Stream(context.Background(), msg.Limit, func(c catalog.EngineCatalog) {
				emit(protocol.CatalogEvent(c))
			})
		}

	case "GET_HISTORY":
		engine := catalog.Engine(msg.AgentType)
		if engine == "" {
			engine = catalog.Engine(h.currentAgentType)
		}
		if h.catalog != nil {
			go func() {
				details, err := h.catalog.LoadConversationDetails(context.Background(), engine, msg.ConversationID, msg.Limit)
				if err != nil {
					log.Printf("[WebRTC] Lỗi tải lịch sử hội thoại %s: %v", msg.ConversationID, err)
					return
				}
				emit(protocol.ConversationHistoryEvent(string(engine), msg.ConversationID, details.Messages, details.ActiveModel, details.TokenUsage))
			}()
		}

	case "SEND_PROMPT":
		if msg.AgentType != "" && msg.AgentType != h.currentAgentType {
			h.mu.Lock()
			if ad, exists := h.adapters[msg.AgentType]; exists {
				h.currentAgentType = msg.AgentType
				h.mu.Unlock()
				_ = ad.Initialize()
			} else {
				h.mu.Unlock()
			}
		}
		prompt := strings.TrimSpace(msg.Prompt)
		if prompt == "" {
			emit(protocol.ErrorEvent("Prompt không được để trống"))
			return
		}
		currentAd := h.getAdapter()
		opts := adapter.PromptOptions{
			Prompt:         prompt,
			Model:          msg.Model,
			ConversationID: msg.ConversationID,
			PermissionMode: msg.PermissionMode,
			Workspace:      msg.Workspace,
		}
		go currentAd.HandlePrompt(opts, emit)

	case "APPROVE_TOOL":
		log.Printf("[WebRTC] Phê duyệt tool call %s: %v", msg.CallID, msg.Approved)
		currentAd := h.getAdapter()
		currentAd.HandleApproval(msg.CallID, msg.Approved)

	case "CANCEL_TASK":
		log.Println("[WebRTC] Nhận yêu cầu hủy task")
		currentAd := h.getAdapter()
		currentAd.Cancel()

	default:
		emit(protocol.ErrorEvent(fmt.Sprintf("Action '%s' không được hỗ trợ", msg.Action)))
	}
}

func (h *Hub) Close() {
	h.mu.Lock()
	defer h.mu.Unlock()

	for _, pc := range h.peers {
		_ = pc.Close()
	}
	h.peers = make(map[string]*webrtc.PeerConnection)
}
