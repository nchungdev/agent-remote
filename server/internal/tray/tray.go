package tray

import (
	"context"
	"fmt"
	"net"
	"os/exec"
	"strconv"
	"strings"
	"time"

	"app.duinch.agentremote/server/internal/gateway"
	"github.com/getlantern/systray"
)

type TrayManager struct {
	server *gateway.Server
}

func NewTrayManager(srv *gateway.Server) *TrayManager {
	return &TrayManager{server: srv}
}

func (t *TrayManager) Run() {
	systray.Run(t.onReady, t.onExit)
}

func getLocalLANIP() string {
	conn, err := net.Dial("udp", "8.8.8.8:80")
	if err == nil {
		defer conn.Close()
		localAddr := conn.LocalAddr().(*net.UDPAddr)
		return localAddr.IP.String()
	}
	return "127.0.0.1"
}

func copyToClipboard(text string) {
	cmd := exec.Command("pbcopy")
	cmd.Stdin = strings.NewReader(text)
	_ = cmd.Run()

	notifyCmd := fmt.Sprintf(`display notification "%s" with title "Agent Remote" subtitle "Copied to clipboard"`, text)
	_ = exec.Command("osascript", "-e", notifyCmd).Run()
}

func promptDialog(title, prompt, defaultValue string) (string, error) {
	script := fmt.Sprintf(`text returned of (display dialog "%s" default answer "%s" with title "%s")`, prompt, defaultValue, title)
	out, err := exec.Command("osascript", "-e", script).Output()
	if err != nil {
		return "", err
	}
	return strings.TrimSpace(string(out)), nil
}

func (t *TrayManager) onReady() {
	systray.SetTitle("✦")
	systray.SetTooltip("Agent Remote Gateway Server")

	// Status Header
	mStatus := systray.AddMenuItem(fmt.Sprintf("● Server Running (:%d)", t.server.GetPort()), "Server Status")
	mStatus.Disable()

	systray.AddSeparator()

	// Primary actions
	mDashboard := systray.AddMenuItem("Open Dashboard...", "Open Web Management Dashboard in browser")
	mShowQR := systray.AddMenuItem("Pair Device (QR Code)...", "Open device pairing QR code")

	systray.AddSeparator()

	// Quick Copy & Tunnel actions
	mToggleTunnel := systray.AddMenuItem("⚡ Start Cloudflare Tunnel", "Start on-demand Cloudflare Quick Tunnel")
	mTunnel := systray.AddMenuItem("Copy Cloudflare URL", "Copy ephemeral public URL")
	lanIP := getLocalLANIP()
	mLAN := systray.AddMenuItem(fmt.Sprintf("Copy LAN Address (%s)", lanIP), "Copy local WebSocket URL")

	systray.AddSeparator()

	// Controls & Settings
	mToggle := systray.AddMenuItem("Pause Server", "Pause or resume the gateway server")

	mSettings := systray.AddMenuItem("Settings", "Server Configuration")
	mSetPort := mSettings.AddSubMenuItem(fmt.Sprintf("Port (:%d)...", t.server.GetPort()), "Change listening port")
	mSetToken := mSettings.AddSubMenuItem("Auth Token...", "Configure access token")

	systray.AddSeparator()
	mQuit := systray.AddMenuItem("Quit Agent Remote", "Shut down server daemon")

	// Background ticker to update Cloudflare tunnel status
	go func() {
		ticker := time.NewTicker(2 * time.Second)
		defer ticker.Stop()
		for range ticker.C {
			url := t.server.GetTunnelURL()
			if url != "" {
				mToggleTunnel.SetTitle("🛑 Stop Cloudflare Tunnel")
				mToggleTunnel.SetTooltip("Click to stop public tunnel")
				mTunnel.SetTitle("Copy Cloudflare URL")
				mTunnel.SetTooltip(url)
				mTunnel.Enable()
			} else {
				mToggleTunnel.SetTitle("⚡ Start Cloudflare Tunnel")
				mToggleTunnel.SetTooltip("Click to start public tunnel")
				mTunnel.SetTitle("Cloudflare: Off")
				mTunnel.Disable()
			}
		}
	}()

	// Event handling loop
	go func() {
		for {
			select {
			case <-mDashboard.ClickedCh:
				port := t.server.GetPort()
				_ = exec.Command("open", fmt.Sprintf("http://127.0.0.1:%d/dashboard", port)).Run()

			case <-mShowQR.ClickedCh:
				qrURL := fmt.Sprintf("http://127.0.0.1:%d/qr", t.server.GetPort())
				_ = exec.Command("open", qrURL).Run()

			case <-mToggleTunnel.ClickedCh:
				url := t.server.GetTunnelURL()
				if url != "" {
					_ = t.server.StopQuickTunnel()
					notifyCmd := `display notification "Đã tắt Cloudflare Tunnel" with title "Agent Remote"`
					_ = exec.Command("osascript", "-e", notifyCmd).Run()
				} else {
					notifyCmd := `display notification "Đang kết nối Cloudflare..." with title "Agent Remote"`
					_ = exec.Command("osascript", "-e", notifyCmd).Run()
					go func() {
						ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
						defer cancel()
						newURL, err := t.server.StartQuickTunnel(ctx)
						if err != nil {
							errCmd := fmt.Sprintf(`display notification "%s" with title "Agent Remote" subtitle "Lỗi Tunnel"`, err.Error())
							_ = exec.Command("osascript", "-e", errCmd).Run()
						} else {
							okCmd := fmt.Sprintf(`display notification "%s" with title "Agent Remote" subtitle "Cloudflare Tunnel"`, newURL)
							_ = exec.Command("osascript", "-e", okCmd).Run()
						}
					}()
				}

			case <-mTunnel.ClickedCh:
				url := t.server.GetTunnelURL()
				if url != "" {
					copyToClipboard(url)
				}

			case <-mLAN.ClickedCh:
				wsURL := fmt.Sprintf("ws://%s:%d", lanIP, t.server.GetPort())
				copyToClipboard(wsURL)

			case <-mToggle.ClickedCh:
				if t.server.IsRunning() {
					_ = t.server.Stop()
					mStatus.SetTitle("○ Server Paused")
					mToggle.SetTitle("Resume Server")
					systray.SetTooltip("Agent Remote (Paused)")
				} else {
					go func() {
						_ = t.server.Start()
					}()
					time.Sleep(100 * time.Millisecond)
					mStatus.SetTitle(fmt.Sprintf("● Server Running (:%d)", t.server.GetPort()))
					mToggle.SetTitle("Pause Server")
					systray.SetTooltip("Agent Remote Gateway Server")
				}

			case <-mSetPort.ClickedCh:
				val, err := promptDialog("Port Configuration", "Enter port number (1024 - 65535):", fmt.Sprintf("%d", t.server.GetPort()))
				if err == nil && val != "" {
					newPort, pErr := strconv.Atoi(val)
					if pErr == nil && newPort > 0 && newPort < 65536 {
						wasRunning := t.server.IsRunning()
						if wasRunning {
							_ = t.server.Stop()
						}
						t.server.SetPort(newPort)
						mSetPort.SetTitle(fmt.Sprintf("Port (:%d)...", newPort))
						if wasRunning {
							go func() {
								_ = t.server.Start()
							}()
							time.Sleep(100 * time.Millisecond)
							mStatus.SetTitle(fmt.Sprintf("● Server Running (:%d)", newPort))
						}
						notifyCmd := fmt.Sprintf(`display notification "Listening on :%d" with title "Agent Remote"`, newPort)
						_ = exec.Command("osascript", "-e", notifyCmd).Run()
					}
				}

			case <-mSetToken.ClickedCh:
				val, err := promptDialog("Authentication Token", "Enter access token (leave empty to disable):", t.server.GetToken())
				if err == nil {
					t.server.SetToken(val)
					msg := "Authentication token disabled"
					if val != "" {
						msg = "Authentication token updated"
					}
					notifyCmd := fmt.Sprintf(`display notification "%s" with title "Agent Remote"`, msg)
					_ = exec.Command("osascript", "-e", notifyCmd).Run()
				}

			case <-mQuit.ClickedCh:
				_ = t.server.Stop()
				systray.Quit()
				return
			}
		}
	}()
}

func (t *TrayManager) onExit() {
	_ = t.server.Stop()
}
