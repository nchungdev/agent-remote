package gateway

import (
	"bufio"
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"app.duinch.agentremote/server/internal/catalog"
	"github.com/nchungdev/remote-tunnel/go/pairing"
)

type ClientInfo struct {
	RemoteAddr  string `json:"remoteAddr"`
	ConnectedAt string `json:"connectedAt"`
	UserAgent   string `json:"userAgent"`
	DurationSec int64  `json:"durationSec"`
}

type StatusResponse struct {
	Online        bool         `json:"online"`
	UptimeSec     int64        `json:"uptimeSec"`
	Port          int          `json:"port"`
	Host          string       `json:"host"`
	LANIP         string       `json:"lanIp"`
	TunnelURL     string       `json:"tunnelUrl"`
	RoomCode      string       `json:"roomCode"`
	ServerID      string       `json:"serverId,omitempty"`
	ServerName    string       `json:"serverName,omitempty"`
	ActiveClients int          `json:"activeClients"`
	MemoryAllocMB float64      `json:"memoryAllocMB"`
	GoVersion     string       `json:"goVersion"`
	ActiveEngine  string       `json:"activeEngine"`
	Clients       []ClientInfo `json:"clients"`
}

func (s *Server) HandleAPIStatus(w http.ResponseWriter, r *http.Request) {
	s.clientsMu.RLock()
	var clientInfos []ClientInfo
	now := time.Now()
	for c := range s.clients {
		dur := int64(now.Sub(c.ConnectedAt).Seconds())
		clientInfos = append(clientInfos, ClientInfo{
			RemoteAddr:  c.RemoteAddr,
			ConnectedAt: c.ConnectedAt.Format("15:04:05"),
			UserAgent:   c.UserAgent,
			DurationSec: dur,
		})
	}
	activeClientsCount := len(s.clients)
	if s.webrtcHub != nil && s.webrtcHub.IsConnected() {
		clientInfos = append(clientInfos, ClientInfo{
			RemoteAddr:  "WebRTC P2P Peer",
			ConnectedAt: "Đang kết nối",
			UserAgent:   "Mobile App (WebRTC DataChannel)",
			DurationSec: 0,
		})
		activeClientsCount++
	}
	s.clientsMu.RUnlock()

	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	uptime := int64(now.Sub(s.startTime).Seconds())

	resp := StatusResponse{
		Online:        s.IsRunning(),
		UptimeSec:     uptime,
		Port:          s.GetPort(),
		Host:          s.host,
		LANIP:         getLocalLANIP(),
		TunnelURL:     s.GetTunnelURL(),
		RoomCode:      s.GetRoomCode(),
		ServerID:      s.identity.ID,
		ServerName:    s.identity.Name,
		ActiveClients: activeClientsCount,
		MemoryAllocMB: float64(m.Alloc) / 1024 / 1024,
		GoVersion:     runtime.Version(),
		ActiveEngine:  string(s.currentAgentType),
		Clients:       clientInfos,
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(resp)
}

func (s *Server) HandleAPIClients(w http.ResponseWriter, r *http.Request) {
	s.clientsMu.RLock()
	defer s.clientsMu.RUnlock()
	var clientInfos []ClientInfo
	now := time.Now()
	for c := range s.clients {
		dur := int64(now.Sub(c.ConnectedAt).Seconds())
		clientInfos = append(clientInfos, ClientInfo{
			RemoteAddr:  c.RemoteAddr,
			ConnectedAt: c.ConnectedAt.Format("15:04:05"),
			UserAgent:   c.UserAgent,
			DurationSec: dur,
		})
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(clientInfos)
}

type EngineSummary struct {
	Engine        string          `json:"engine"`
	Installed     bool            `json:"installed"`
	Binary        string          `json:"binary"`
	ModelsCount   int             `json:"modelsCount"`
	Models        []catalog.Model `json:"models"`
	ProjectsCount int             `json:"projectsCount"`
	ConvsCount    int             `json:"convsCount"`
	Error         string          `json:"error,omitempty"`
}

func (s *Server) HandleAPIEngines(w http.ResponseWriter, r *http.Request) {
	var summaries []EngineSummary
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()

	s.catalog.Stream(ctx, 5, func(c catalog.EngineCatalog) {
		summaries = append(summaries, EngineSummary{
			Engine:        string(c.Engine),
			Installed:     c.Installed,
			Binary:        c.Binary,
			ModelsCount:   len(c.Models),
			Models:        c.Models,
			ProjectsCount: len(c.Projects),
			ConvsCount:    c.Total,
			Error:         c.Error,
		})
	})

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(summaries)
}

func (s *Server) HandleAPITunnelStart(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: host loopback required to start Quick Tunnel", http.StatusForbidden)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 25*time.Second)
	defer cancel()

	url, err := s.quickTunnel.Start(ctx)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		_ = json.NewEncoder(w).Encode(map[string]any{
			"success": false,
			"error":   err.Error(),
		})
		return
	}

	s.serverMu.Lock()
	s.tunnelURL = url
	s.serverMu.Unlock()

	_ = json.NewEncoder(w).Encode(map[string]any{
		"success":   true,
		"tunnelUrl": url,
	})
}

func (s *Server) HandleAPITunnelStop(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: host loopback required to stop Quick Tunnel", http.StatusForbidden)
		return
	}
	_ = s.quickTunnel.Stop()
	s.serverMu.Lock()
	s.tunnelURL = ""
	s.serverMu.Unlock()

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"success": true,
		"active":  false,
	})
}

func (s *Server) HandleAPITunnelStatus(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(s.quickTunnel.Status())
}

func (s *Server) HandleAPIUpload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	// Max upload: 32MB
	if err := r.ParseMultipartForm(32 << 20); err != nil {
		http.Error(w, "Failed to parse multipart form (max 32MB)", http.StatusBadRequest)
		return
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		http.Error(w, "Missing file in form field 'file'", http.StatusBadRequest)
		return
	}
	defer file.Close()

	home, _ := os.UserHomeDir()
	uploadDir := filepath.Join(home, ".agent-remote", "uploads")
	_ = os.MkdirAll(uploadDir, 0755)

	ext := filepath.Ext(header.Filename)
	b := make([]byte, 8)
	_, _ = rand.Read(b)
	fileID := fmt.Sprintf("%x%s", b, ext)
	dstPath := filepath.Join(uploadDir, fileID)

	dst, err := os.Create(dstPath)
	if err != nil {
		http.Error(w, "Failed to create upload file", http.StatusInternalServerError)
		return
	}
	defer dst.Close()

	written, err := io.Copy(dst, file)
	if err != nil {
		http.Error(w, "Failed to write file contents", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"success":  true,
		"fileId":   fileID,
		"filename": header.Filename,
		"size":     written,
		"url":      "/api/files/" + fileID,
		"path":     dstPath,
	})
}

func (s *Server) HandleAPIFiles(w http.ResponseWriter, r *http.Request) {
	fileID := strings.TrimPrefix(r.URL.Path, "/api/files/")
	if fileID == "" || strings.Contains(fileID, "..") {
		http.Error(w, "Invalid file ID", http.StatusBadRequest)
		return
	}

	home, _ := os.UserHomeDir()
	filePath := filepath.Join(home, ".agent-remote", "uploads", fileID)
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		http.Error(w, "File not found", http.StatusNotFound)
		return
	}

	http.ServeFile(w, r, filePath)
}

func (s *Server) HandleAPIRoomRotate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	if !pairing.IsAuthorizedRequest(r, s.GetToken()) {
		http.Error(w, "Forbidden: host authorization required", http.StatusForbidden)
		return
	}
	b := make([]byte, 3)
	_, _ = rand.Read(b)
	num := (int(b[0])<<16|int(b[1])<<8|int(b[2]))%900000 + 100000
	newCode := fmt.Sprintf("%03d-%03d", num/1000, num%1000)

	s.serverMu.Lock()
	s.roomCode = newCode
	if s.webrtcHub != nil {
		s.webrtcHub.UpdateRoomCode(newCode)
	}
	s.serverMu.Unlock()

	home, err := os.UserHomeDir()
	if err == nil {
		file := filepath.Join(home, ".agent-remote", "room_code")
		_ = os.WriteFile(file, []byte(newCode), 0600)
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"success":  true,
		"roomCode": newCode,
	})
}

func (s *Server) HandleAPILogs(w http.ResponseWriter, r *http.Request) {
	logFile := "/tmp/server.log"
	f, err := os.Open(logFile)
	if err != nil {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		_ = json.NewEncoder(w).Encode(map[string]any{"logs": []string{"(Không tìm thấy log file /tmp/server.log)"}})
		return
	}
	defer f.Close()

	var lines []string
	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		lines = append(lines, strings.TrimRight(scanner.Text(), "\r\n"))
		if len(lines) > 200 {
			lines = lines[1:]
		}
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]any{"logs": lines})
}

func (s *Server) HandlePairRequest(w http.ResponseWriter, r *http.Request) {
	var req struct {
		DeviceID   string `json:"device_id"`
		DeviceName string `json:"device_name"`
		Code       string `json:"code"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	if req.DeviceID == "" {
		req.DeviceID = "android-device"
	}
	s.pairHub.SubmitRequest(req.DeviceID, req.DeviceName, req.Code)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]any{"status": "pending"})
}

func (s *Server) HandlePairPending(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	pending := s.pairHub.GetPending()
	_ = json.NewEncoder(w).Encode(pending)
}

func (s *Server) HandlePairConfirm(w http.ResponseWriter, r *http.Request) {
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: pairing confirmation must originate on host machine", http.StatusForbidden)
		return
	}
	var req struct {
		DeviceID string `json:"device_id"`
		Approved bool   `json:"approved"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	s.pairHub.Confirm(req.DeviceID, req.Approved)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]any{"status": "ok"})
}

func (s *Server) HandlePairCheck(w http.ResponseWriter, r *http.Request) {
	deviceID := r.URL.Query().Get("device_id")
	status := s.pairHub.CheckStatus(deviceID)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]any{"status": status})
}

func (s *Server) HandleRemoteRequestEnable(w http.ResponseWriter, r *http.Request) {
	var req struct {
		DeviceID   string `json:"device_id"`
		DeviceName string `json:"device_name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}
	if req.DeviceID == "" {
		req.DeviceID = "android-device"
	}
	s.pairHub.SubmitRemoteTunnelRequest(req.DeviceID, req.DeviceName)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]any{"status": "pending"})
}

func (s *Server) HandleRemotePendingRequests(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	pending := s.pairHub.GetPendingRemoteTunnelRequests()
	_ = json.NewEncoder(w).Encode(pending)
}

func (s *Server) HandleRemoteApproveRequest(w http.ResponseWriter, r *http.Request) {
	if !pairing.IsLoopbackRequest(r) {
		http.Error(w, "Forbidden: remote tunnel approval must originate on host machine", http.StatusForbidden)
		return
	}
	var req struct {
		DeviceID string `json:"device_id"`
		Approved bool   `json:"approved"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid json", http.StatusBadRequest)
		return
	}

	url := ""
	room := s.GetRoomCode()
	if req.Approved {
		if s.quickTunnel != nil && !s.quickTunnel.IsActive() {
			ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
			defer cancel()
			url, _ = s.quickTunnel.Start(ctx)
		} else if s.quickTunnel != nil {
			url = s.quickTunnel.GetURL()
		}
	}

	s.pairHub.ConfirmRemoteTunnelRequest(req.DeviceID, req.Approved, url, room)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"status": "ok",
		"active": req.Approved,
		"url":    url,
		"room":   room,
	})
}

func (s *Server) HandleRemoteRequestStatus(w http.ResponseWriter, r *http.Request) {
	deviceID := r.URL.Query().Get("device_id")
	status, url, room := s.pairHub.CheckRemoteTunnelStatus(deviceID)
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"status": status,
		"url":    url,
		"room":   room,
	})
}
