package gateway

import (
	"fmt"
	"net"
	"net/http"
	"time"

	"github.com/nchungdev/remote-tunnel/go/pairing"
	"github.com/skip2/go-qrcode"
)

func getLocalLANIP() string {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err == nil {
		defer conn.Close()
		localAddr := conn.LocalAddr().(*net.UDPAddr)
		return localAddr.IP.String()
	}
	return "127.0.0.1"
}

func (s *Server) GetQRPayload() string {
	tunnelURL := s.GetTunnelURL()
	lanIP := getLocalLANIP()
	lanAddr := fmt.Sprintf("%s:%d", lanIP, s.GetPort())
	// When Quick Tunnel is active, omit lanAddr in default QR so mobile clients
	// connect directly via Quick Tunnel instead of forcing LAN Wi-Fi.
	effectiveLan := lanAddr
	if tunnelURL != "" {
		effectiveLan = ""
	}
	payload, err := pairing.BuildPairingJSON("agent-remote", tunnelURL, "", "", effectiveLan)
	if err != nil {
		if tunnelURL != "" {
			return fmt.Sprintf(`{"v":1,"app":"agent-remote","url":"%s"}`, tunnelURL)
		}
		return fmt.Sprintf(`{"v":1,"app":"agent-remote","lan":"%s"}`, lanAddr)
	}
	return payload
}

func (s *Server) HandleQRPNG(w http.ResponseWriter, r *http.Request) {
	content := s.GetQRPayload()
	// High contrast, crisp 320x320 PNG
	png, err := qrcode.Encode(content, qrcode.Medium, 320)
	if err != nil {
		http.Error(w, "Lỗi tạo QR code: "+err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
	_, _ = w.Write(png)
}

func (s *Server) HandleQRPage(w http.ResponseWriter, r *http.Request) {
	tunnelURL := s.GetTunnelURL()
	lanIP := getLocalLANIP()
	lanURL := fmt.Sprintf("ws://%s:%d", lanIP, s.GetPort())

	tunnelDisplay := tunnelURL
	if tunnelDisplay == "" {
		tunnelDisplay = "Đang khởi tạo Quick Tunnel..."
	}

	html := fmt.Sprintf(`<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Agent Remote — Quét mã kết nối</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: #141416;
            color: #F4F4F5;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 24px;
        }
        .card {
            background: #1E1E22;
            border: 1px solid #2E2E34;
            border-radius: 24px;
            max-width: 440px;
            width: 100%%;
            padding: 32px 28px;
            text-align: center;
            box-shadow: 0 20px 40px rgba(0,0,0,0.5);
        }
        .title {
            font-size: 22px;
            font-weight: 700;
            color: #FFFFFF;
            margin-bottom: 8px;
        }
        .subtitle {
            font-size: 13px;
            color: #A1A1AA;
            margin-bottom: 24px;
            line-height: 1.5;
        }
        .qr-wrapper {
            background: #FFFFFF;
            padding: 16px;
            border-radius: 18px;
            display: inline-block;
            margin-bottom: 24px;
            box-shadow: 0 8px 24px rgba(0,0,0,0.3);
        }
        .qr-wrapper img {
            display: block;
            width: 250px;
            height: 250px;
        }
        .info-pill {
            background: #27272A;
            border: 1px solid #3F3F46;
            border-radius: 12px;
            padding: 10px 14px;
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            font-size: 13px;
            font-family: ui-monospace, Menlo, Consolas, monospace;
        }
        .info-label { color: #A1A1AA; font-size: 11px; text-transform: uppercase; font-weight: 600; }
        .info-val { color: #F4F4F5; font-weight: 600; }
        .footer-note {
            font-size: 12px;
            color: #71717A;
            margin-top: 16px;
        }
    </style>
</head>
<body>
    <div class="card">
        <h1 class="title">⚡ Kết nối Agent Remote</h1>
        <p class="subtitle">Mở ứng dụng <strong>Agent Remote</strong> trên điện thoại và quét mã QR này để bắt đầu ghép nối tức thì.</p>
        
        <div class="qr-wrapper">
            <img src="/qr.png?t=%d" alt="Mã QR kết nối Agent Remote">
        </div>

        <div class="info-pill">
            <span class="info-label">☁️ Tunnel</span>
            <span class="info-val" style="color: #93C5FD; font-size: 11px;">%s</span>
        </div>

        <div class="info-pill">
            <span class="info-label">📶 Wi-Fi</span>
            <span class="info-val" style="font-size: 11px;">%s</span>
        </div>

        <p class="footer-note">Hỗ trợ mạng 4G/5G qua WebRTC P2P & Cloudflare Quick Tunnel.</p>
    </div>
</body>
</html>`, time.Now().UnixMilli(), tunnelDisplay, lanURL)

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = w.Write([]byte(html))
}
