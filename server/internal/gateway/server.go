package gateway

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"strings"
	"sync"
	"time"

	"app.duinch.agentremote/server/internal/adapter"
	"app.duinch.agentremote/server/internal/catalog"
	zeroconfadv "app.duinch.agentremote/server/internal/discovery"
	"app.duinch.agentremote/server/internal/protocol"
	"app.duinch.agentremote/server/internal/webrtc"
	"github.com/gorilla/websocket"
	"github.com/nchungdev/remote-tunnel/go/pairing"
	"github.com/nchungdev/remote-tunnel/go/quicktunnel"
	"github.com/nchungdev/remote-tunnel/go/signaling"
	"os"
	"path/filepath"
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return pairing.IsSafeWebSocketOrigin(r)
	},
}

type ClientConnection struct {
	conn        *websocket.Conn
	mu          sync.Mutex
	RemoteAddr  string
	ConnectedAt time.Time
	UserAgent   string
}

func (c *ClientConnection) Send(data []byte) error {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.conn.WriteMessage(websocket.TextMessage, data)
}

type Server struct {
	host             string
	port             int
	token            string
	sessionID        string
	roomCode         string
	webrtcHub        *webrtc.Hub
	sigHub           *signaling.MessageHub
	quickTunnel      *quicktunnel.QuickTunnel
	pairHub          *pairing.Hub
	tunnelURL        string
	currentAgentType protocol.AgentType
	adapters         map[protocol.AgentType]adapter.Adapter
	catalog          *catalog.Service
	clients          map[*ClientConnection]bool
	clientsMu        sync.RWMutex
	adapterMu        sync.RWMutex
	httpServer       *http.Server
	running          bool
	startTime        time.Time
	identity         ServerIdentity
	serverMu         sync.Mutex
}

func NewServer(host string, port int, token string) *Server {
	sessionBytes := make([]byte, 4)
	_, _ = rand.Read(sessionBytes)
	sessionID := hex.EncodeToString(sessionBytes)

	home, _ := os.UserHomeDir()
	dataDir := filepath.Join(home, ".agent-remote")
	identity, _ := LoadServerIdentity(dataDir)

	adapters := map[protocol.AgentType]adapter.Adapter{
		protocol.AgentTypeMock:        adapter.NewMockAdapter(),
		protocol.AgentTypeAntigravity: adapter.NewAntigravityAdapter(),
		protocol.AgentTypeClaudeCode:  adapter.NewClaudeCodeAdapter(""),
		protocol.AgentTypeCodex:       adapter.NewCodexAdapter(),
	}

	srv := &Server{
		host:             host,
		port:             port,
		token:            token,
		sessionID:        sessionID,
		roomCode:         webrtc.FixedSignalingRoom,
		identity:         identity,
		currentAgentType: protocol.AgentTypeMock,
		adapters:         adapters,
		catalog:          catalog.NewDefaultService(),
		clients:          make(map[*ClientConnection]bool),
		startTime:        time.Now(),
		sigHub:           signaling.NewMessageHub(),
		pairHub:          pairing.NewHub(3 * time.Minute),
	}

	srv.quickTunnel = quicktunnel.New(port, quicktunnel.WithURLCallback(func(url string) {
		srv.serverMu.Lock()
		srv.tunnelURL = url
		srv.serverMu.Unlock()
	}))

	srv.webrtcHub = webrtc.NewHub(webrtc.FixedSignalingRoom, sessionID, adapters, srv.catalog, srv.sigHub)

	return srv
}

func (s *Server) GetRoomCode() string {
	return webrtc.FixedSignalingRoom
}

func (s *Server) GetIdentity() ServerIdentity {
	return s.identity
}

func (s *Server) GetPort() int {
	s.serverMu.Lock()
	defer s.serverMu.Unlock()
	return s.port
}

func (s *Server) SetPort(port int) {
	s.serverMu.Lock()
	defer s.serverMu.Unlock()
	s.port = port
}

func (s *Server) GetToken() string {
	s.serverMu.Lock()
	defer s.serverMu.Unlock()
	return s.token
}

func (s *Server) SetToken(token string) {
	s.serverMu.Lock()
	defer s.serverMu.Unlock()
	s.token = token
}

func (s *Server) IsRunning() bool {
	s.serverMu.Lock()
	defer s.serverMu.Unlock()
	return s.running
}

func (s *Server) getAdapter() adapter.Adapter {
	s.adapterMu.RLock()
	defer s.adapterMu.RUnlock()
	return s.adapters[s.currentAgentType]
}

func (s *Server) setAdapter(agentType protocol.AgentType) error {
	s.adapterMu.Lock()
	defer s.adapterMu.Unlock()
	ad, exists := s.adapters[agentType]
	if !exists {
		return fmt.Errorf("agent '%s' không hợp lệ", agentType)
	}
	s.currentAgentType = agentType
	return ad.Initialize()
}

func (s *Server) HandleWS(w http.ResponseWriter, r *http.Request) {
	// 1. Verify token if configured
	if s.token != "" && !pairing.IsAuthorizedRequest(r, s.token) {
		http.Error(w, "Unauthorized: Invalid or missing token", http.StatusUnauthorized)
		log.Printf("[Gateway] Từ chối kết nối từ %s: Sai auth token", r.RemoteAddr)
		return
	}

	ws, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("[Gateway] Lỗi upgrade websocket: %v", err)
		return
	}

	client := &ClientConnection{
		conn:        ws,
		RemoteAddr:  r.RemoteAddr,
		ConnectedAt: time.Now(),
		UserAgent:   r.UserAgent(),
	}
	s.clientsMu.Lock()
	s.clients[client] = true
	s.clientsMu.Unlock()

	log.Printf("[Gateway] Mobile Client kết nối từ: %s", ws.RemoteAddr())

	// Send initial SESSION_INIT event
	ad := s.getAdapter()
	_ = ad.Initialize()
	_ = client.Send(protocol.SessionInit(s.sessionID, s.currentAgentType, "READY"))

	defer func() {
		s.clientsMu.Lock()
		delete(s.clients, client)
		s.clientsMu.Unlock()
		_ = ws.Close()
		log.Printf("[Gateway] Client %s đã ngắt kết nối", ws.RemoteAddr())
	}()

	emit := func(event []byte) {
		_ = client.Send(event)
	}

	for {
		_, rawMsg, err := ws.ReadMessage()
		if err != nil {
			break
		}

		var msg protocol.ClientMessage
		if err := json.Unmarshal(rawMsg, &msg); err != nil {
			emit(protocol.ErrorEvent("Payload không phải JSON hợp lệ"))
			continue
		}

		log.Printf("[Gateway] Nhận action: %s", msg.Action)

		switch msg.Action {
		case "PING":
			emit(protocol.Pong())

		case "SELECT_AGENT":
			if err := s.setAdapter(msg.AgentType); err != nil {
				emit(protocol.ErrorEvent(err.Error()))
			} else {
				log.Printf("[Gateway] Đã chuyển sang agent: %s", msg.AgentType)
				emit(protocol.SessionInit(s.sessionID, s.currentAgentType, "READY"))
			}

		case "LIST_CATALOG":
			go s.catalog.Stream(context.Background(), msg.Limit, func(c catalog.EngineCatalog) {
				emit(protocol.CatalogEvent(c))
			})

		case "GET_HISTORY":
			engine := catalog.Engine(msg.AgentType)
			if engine == "" {
				engine = catalog.Engine(s.currentAgentType)
			}
			go func() {
				msgs, err := s.catalog.LoadHistory(context.Background(), engine, msg.ConversationID, msg.Limit)
				if err != nil {
					log.Printf("[Gateway] Lỗi tải lịch sử hội thoại %s: %v", msg.ConversationID, err)
					return
				}
				emit(protocol.ConversationHistoryEvent(string(engine), msg.ConversationID, msgs))
			}()

		case "SEND_PROMPT":
			if msg.AgentType != "" && msg.AgentType != s.currentAgentType {
				_ = s.setAdapter(msg.AgentType)
			}
			prompt := strings.TrimSpace(msg.Prompt)
			if prompt == "" {
				emit(protocol.ErrorEvent("Prompt không được để trống"))
				continue
			}
			currentAd := s.getAdapter()
			opts := adapter.PromptOptions{
				Prompt:         prompt,
				Model:          msg.Model,
				ConversationID: msg.ConversationID,
				PermissionMode: msg.PermissionMode,
				Workspace:      msg.Workspace,
			}
			go currentAd.HandlePrompt(opts, emit)

		case "APPROVE_TOOL":
			log.Printf("[Gateway] Phê duyệt tool call %s: %v", msg.CallID, msg.Approved)
			currentAd := s.getAdapter()
			currentAd.HandleApproval(msg.CallID, msg.Approved)

		case "CANCEL_TASK":
			log.Printf("[Gateway] Nhận yêu cầu hủy task")
			currentAd := s.getAdapter()
			currentAd.Cancel()

		default:
			emit(protocol.ErrorEvent(fmt.Sprintf("Action '%s' không được hỗ trợ", msg.Action)))
		}
	}
}

func (s *Server) HandleRoot(w http.ResponseWriter, r *http.Request) {
	if r.Header.Get("Upgrade") == "websocket" {
		s.HandleWS(w, r)
		return
	}
	s.HandleDashboard(w, r)
}

func (s *Server) Start() error {
	s.serverMu.Lock()
	if s.running {
		s.serverMu.Unlock()
		return nil
	}
	addr := fmt.Sprintf("%s:%d", s.host, s.port)
	mux := http.NewServeMux()
	mux.HandleFunc("/", s.HandleRoot)
	mux.HandleFunc("/dashboard", s.HandleDashboard)
	mux.HandleFunc("/ws", s.HandleWS)
	mux.HandleFunc("/qr", s.HandleQRPage)
	mux.HandleFunc("/qr.png", s.HandleQRPNG)
	mux.HandleFunc("/api/status", s.HandleAPIStatus)
	mux.HandleFunc("/api/clients", s.HandleAPIClients)
	mux.HandleFunc("/api/engines", s.HandleAPIEngines)
	mux.HandleFunc("/api/tunnel/start", s.HandleAPITunnelStart)
	mux.HandleFunc("/api/tunnel/stop", s.HandleAPITunnelStop)
	mux.HandleFunc("/api/tunnel/status", s.HandleAPITunnelStatus)
	mux.HandleFunc("/api/room/rotate", s.HandleAPIRoomRotate)
	mux.HandleFunc("/api/logs", s.HandleAPILogs)
	mux.HandleFunc("/api/upload", s.HandleAPIUpload)
	mux.HandleFunc("/api/files/", s.HandleAPIFiles)
	mux.HandleFunc("/api/pair/request", s.HandlePairRequest)
	mux.HandleFunc("/api/pair/pending", s.HandlePairPending)
	mux.HandleFunc("/api/pair/confirm", s.HandlePairConfirm)
	mux.HandleFunc("/api/pair/check", s.HandlePairCheck)
	mux.HandleFunc("/api/remote/request-enable", s.HandleRemoteRequestEnable)
	mux.HandleFunc("/api/remote/pending-requests", s.HandleRemotePendingRequests)
	mux.HandleFunc("/api/remote/approve-request", s.HandleRemoteApproveRequest)
	mux.HandleFunc("/api/remote/request-status", s.HandleRemoteRequestStatus)

	sigHandler := NewSignalHTTPHandler(s.sigHub, s.token)
	mux.Handle("/api/webrtc/signal", sigHandler)
	mux.Handle("/api/signal", sigHandler)

	s.httpServer = &http.Server{
		Addr:    addr,
		Handler: mux,
	}
	s.running = true

	// Khởi động WebRTC P2P DataChannel Hub
	if s.webrtcHub != nil {
		go func() {
			if err := s.webrtcHub.Start(); err != nil {
				log.Printf("[WebRTC] Lỗi kết nối WebRTC Hub: %v", err)
			}
		}()
	}
	s.serverMu.Unlock()

	mdnsAdv := zeroconfadv.Start("_agent-remote._tcp", s.port, nil)
	defer mdnsAdv.Stop()

	log.Printf("[Gateway] Khởi động Agent Remote Gateway tại ws://%s (Room P2P: %s)", addr, s.roomCode)
	err := s.httpServer.ListenAndServe()
	s.serverMu.Lock()
	s.running = false
	if s.webrtcHub != nil {
		s.webrtcHub.Close()
	}
	s.serverMu.Unlock()

	if err == http.ErrServerClosed {
		return nil
	}
	return err
}

func (s *Server) GetTunnelURL() string {
	s.serverMu.Lock()
	defer s.serverMu.Unlock()
	return s.tunnelURL
}

func (s *Server) StartQuickTunnel(ctx context.Context) (string, error) {
	if s.quickTunnel == nil {
		return "", errors.New("quick tunnel not initialized")
	}
	url, err := s.quickTunnel.Start(ctx)
	if err == nil {
		s.serverMu.Lock()
		s.tunnelURL = url
		s.serverMu.Unlock()
	}
	return url, err
}

func (s *Server) StopQuickTunnel() error {
	if s.quickTunnel != nil {
		s.quickTunnel.Stop()
	}
	s.serverMu.Lock()
	s.tunnelURL = ""
	s.serverMu.Unlock()
	return nil
}

func (s *Server) Stop() error {
	s.serverMu.Lock()
	defer s.serverMu.Unlock()
	if s.quickTunnel != nil {
		s.quickTunnel.Stop()
	}
	if s.webrtcHub != nil {
		s.webrtcHub.Close()
	}
	if !s.running || s.httpServer == nil {
		return nil
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	err := s.httpServer.Shutdown(ctx)
	s.running = false
	log.Println("[Gateway] Server đã dừng lại.")
	return err
}
