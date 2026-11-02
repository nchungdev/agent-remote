package webrtc

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"sync"

	"app.duinch.agentremote/server/internal/adapter"
	"app.duinch.agentremote/server/internal/catalog"
	"app.duinch.agentremote/server/internal/protocol"
	"github.com/nchungdev/remote-tunnel/go/signaling"
	"github.com/pion/webrtc/v4"
)

type Hub struct {
	roomCode         string
	sigHub           *signaling.MessageHub
	peerConnection   *webrtc.PeerConnection
	dataChannel      *webrtc.DataChannel
	sessionID        string
	currentAgentType protocol.AgentType
	adapters         map[protocol.AgentType]adapter.Adapter
	catalog          *catalog.Service
	mu               sync.Mutex
	onConnected      func()
	onDisconnected   func()
}

func NewHub(roomCode string, sessionID string, adapters map[protocol.AgentType]adapter.Adapter, catalog *catalog.Service, sigHub *signaling.MessageHub) *Hub {
	return &Hub{
		roomCode:         roomCode,
		sessionID:        sessionID,
		currentAgentType: protocol.AgentTypeMock,
		adapters:         adapters,
		catalog:          catalog,
		sigHub:           sigHub,
	}
}

func (h *Hub) SetCallbacks(onConnected, onDisconnected func()) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.onConnected = onConnected
	h.onDisconnected = onDisconnected
}

func (h *Hub) GetRoomCode() string {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.roomCode
}

func (h *Hub) IsConnected() bool {
	h.mu.Lock()
	defer h.mu.Unlock()
	if h.peerConnection == nil {
		return false
	}
	return h.peerConnection.ConnectionState() == webrtc.PeerConnectionStateConnected
}

func (h *Hub) Start() error {
	if h.sigHub != nil {
		ch, err := h.sigHub.Register(h.roomCode, "host")
		if err == nil {
			go func() {
				for msg := range ch {
					h.handleSignal(msg)
				}
			}()
		}
	}
	return h.initPeerConnection()
}

func (h *Hub) initPeerConnection() error {
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
		return fmt.Errorf("không thể tạo webrtc peer connection: %w", err)
	}

	h.mu.Lock()
	if h.peerConnection != nil {
		_ = h.peerConnection.Close()
	}
	h.peerConnection = pc
	h.mu.Unlock()

	// ICE Candidate handler -> send via signal hub
	pc.OnICECandidate(func(c *webrtc.ICECandidate) {
		if c == nil {
			return
		}
		candidateJSON, err := json.Marshal(c.ToJSON())
		if err != nil {
			return
		}
		h.mu.Lock()
		sigHub := h.sigHub
		h.mu.Unlock()
		if sigHub != nil {
			sigHub.Broadcast(h.roomCode, signaling.SignalMessage{
				Type:      "candidate",
				Sender:    "host",
				Recipient: "client",
				Payload:   candidateJSON,
			})
		}
	})

	// Connection state handler
	pc.OnConnectionStateChange(func(state webrtc.PeerConnectionState) {
		log.Printf("[WebRTC] Trạng thái kết nối P2P: %s", state)
		if state == webrtc.PeerConnectionStateConnected {
			h.mu.Lock()
			cb := h.onConnected
			h.mu.Unlock()
			if cb != nil {
				cb()
			}
		} else if state == webrtc.PeerConnectionStateDisconnected || state == webrtc.PeerConnectionStateFailed || state == webrtc.PeerConnectionStateClosed {
			h.mu.Lock()
			cb := h.onDisconnected
			h.mu.Unlock()
			if cb != nil {
				cb()
			}
		}
	})

	// DataChannel listener
	pc.OnDataChannel(func(dc *webrtc.DataChannel) {
		log.Printf("[WebRTC] Nhận DataChannel mới từ client: %s", dc.Label())
		h.setupDataChannel(dc)
	})

	return nil
}

func (h *Hub) setupDataChannel(dc *webrtc.DataChannel) {
	h.mu.Lock()
	h.dataChannel = dc
	h.mu.Unlock()

	dc.OnOpen(func() {
		log.Println("[WebRTC] Kênh P2P DataChannel đã mở thành công! Sẵn sàng stream dữ liệu.")
		// Send initial SESSION_INIT event
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
				msgs, err := h.catalog.LoadHistory(context.Background(), engine, msg.ConversationID, msg.Limit)
				if err != nil {
					log.Printf("[WebRTC] Lỗi tải lịch sử hội thoại %s: %v", msg.ConversationID, err)
					return
				}
				emit(protocol.ConversationHistoryEvent(string(engine), msg.ConversationID, msgs))
			}()
		}

	case "SEND_PROMPT":
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

func (h *Hub) handleSignal(msg signaling.SignalMessage) {
	h.mu.Lock()
	pc := h.peerConnection
	sigHub := h.sigHub
	h.mu.Unlock()

	if pc == nil || sigHub == nil {
		return
	}

	switch msg.Type {
	case "offer":
		if err := h.initPeerConnection(); err != nil {
			log.Printf("[WebRTC] Lỗi khởi tạo lại PeerConnection: %v", err)
			return
		}
		h.mu.Lock()
		pc = h.peerConnection
		h.mu.Unlock()

		var sdp webrtc.SessionDescription
		if err := json.Unmarshal(msg.Payload, &sdp); err != nil {
			log.Printf("[WebRTC] Lỗi unmarshal SDP offer: %v", err)
			return
		}

		if err := pc.SetRemoteDescription(sdp); err != nil {
			log.Printf("[WebRTC] Lỗi set remote description: %v", err)
			return
		}

		answer, err := pc.CreateAnswer(nil)
		if err != nil {
			log.Printf("[WebRTC] Lỗi create answer: %v", err)
			return
		}

		if err := pc.SetLocalDescription(answer); err != nil {
			log.Printf("[WebRTC] Lỗi set local description: %v", err)
			return
		}

		answerJSON, err := json.Marshal(answer)
		if err != nil {
			return
		}

		sigHub.Broadcast(h.roomCode, signaling.SignalMessage{
			Type:      "answer",
			Sender:    "host",
			Recipient: "client",
			Payload:   answerJSON,
		})
		log.Println("[WebRTC] Đã gửi SDP Answer về client thành công")

	case "candidate":
		var init webrtc.ICECandidateInit
		if err := json.Unmarshal(msg.Payload, &init); err != nil {
			return
		}
		_ = pc.AddICECandidate(init)
	}
}

func (h *Hub) Close() {
	h.mu.Lock()
	defer h.mu.Unlock()

	if h.dataChannel != nil {
		_ = h.dataChannel.Close()
	}
	if h.peerConnection != nil {
		_ = h.peerConnection.Close()
	}
}
