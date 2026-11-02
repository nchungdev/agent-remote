package gateway

import (
	"fmt"
	"net/http"
	"time"
)

func (s *Server) HandleDashboard(w http.ResponseWriter, r *http.Request) {
	roomCode := s.GetRoomCode()
	tunnelURL := s.GetTunnelURL()
	lanIP := getLocalLANIP()
	lanURL := fmt.Sprintf("ws://%s:%d", lanIP, s.GetPort())

	html := fmt.Sprintf(dashboardHTMLTemplate, tunnelURL, roomCode, lanURL, time.Now().UnixMilli())

	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = w.Write([]byte(html))
}

const dashboardHTMLTemplate = `<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Agent Remote — Server Management Dashboard</title>
    <style>
        :root { --bg: #0C0C0E; --surface: #141418; --surface-hover: #1A1A22; --border: #262630; --text-primary: #F4F4F6; --text-secondary: #9090A0; --accent: #3B82F6; --accent-glow: rgba(59, 130, 246, 0.15); --green: #10B981; --green-glow: rgba(16, 185, 129, 0.2); --orange: #F59E0B; --purple: #8B5CF6; }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; background: var(--bg); color: var(--text-primary); line-height: 1.5; padding: 24px; max-width: 1200px; margin: 0 auto; }
        header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 28px; padding-bottom: 20px; border-bottom: 1px solid var(--border); flex-wrap: wrap; gap: 16px; }
        .brand { display: flex; align-items: center; gap: 12px; }
        .brand-icon { width: 40px; height: 40px; background: linear-gradient(135deg, #3B82F6, #8B5CF6); border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 20px; }
        .brand-text h1 { font-size: 20px; font-weight: 700; color: #FFF; }
        .brand-text p { font-size: 13px; color: var(--text-secondary); }
        .header-status { display: flex; align-items: center; gap: 10px; }
        .badge-live { background: var(--green-glow); color: #34D399; border: 1px solid rgba(16, 185, 129, 0.4); padding: 6px 14px; border-radius: 20px; font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
        .pulse { width: 8px; height: 8px; background: #10B981; border-radius: 50%%; box-shadow: 0 0 10px #10B981; animation: pulse-anim 2s infinite; }
        @keyframes pulse-anim { 0%% { transform: scale(0.95); opacity: 0.8; } 50%% { transform: scale(1.3); opacity: 1; } 100%% { transform: scale(0.95); opacity: 0.8; } }
        .btn { background: #22222C; border: 1px solid var(--border); color: var(--text-primary); padding: 8px 14px; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.15s ease; display: inline-flex; align-items: center; gap: 6px; }
        .btn:hover { background: #2C2C38; border-color: #3E3E50; }
        .btn-primary { background: var(--accent); border-color: var(--accent); color: #FFF; }
        .btn-primary:hover { background: #2563EB; }
        .grid-2 { display: grid; grid-template-columns: repeat(auto-fit, minmax(360px, 1fr)); gap: 20px; margin-bottom: 24px; }
        .card { background: var(--surface); border: 1px solid var(--border); border-radius: 16px; padding: 22px; box-shadow: 0 4px 20px rgba(0,0,0,0.25); }
        .card-title { font-size: 15px; font-weight: 700; color: #FFF; margin-bottom: 16px; display: flex; align-items: center; justify-content: space-between; }
        .info-row { background: #191920; border: 1px solid var(--border); border-radius: 10px; padding: 12px 14px; margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between; font-size: 13px; }
        .info-row .label { color: var(--text-secondary); font-size: 12px; font-weight: 500; }
        .info-row .val { font-family: ui-monospace, Menlo, Consolas, monospace; font-weight: 600; color: #FFF; max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .copy-btn { background: none; border: none; color: var(--accent); cursor: pointer; font-size: 12px; padding: 2px 6px; border-radius: 4px; }
        .copy-btn:hover { background: rgba(59, 130, 246, 0.15); }
        .engine-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; }
        .engine-card { background: #17171F; border: 1px solid var(--border); border-radius: 12px; padding: 16px; }
        .engine-card h3 { font-size: 14px; font-weight: 700; display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
        .pill-active { font-size: 11px; padding: 2px 8px; border-radius: 12px; background: var(--green-glow); color: #34D399; font-weight: 600; }
        .pill-inactive { font-size: 11px; padding: 2px 8px; border-radius: 12px; background: rgba(245, 158, 11, 0.15); color: #FBBF24; font-weight: 600; }
        .engine-detail { font-size: 12px; color: var(--text-secondary); margin-bottom: 6px; }
        .model-pills { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; max-height: 90px; overflow-y: auto; }
        .model-pill { background: #242430; border: 1px solid #363644; color: #D1D5DB; font-size: 11px; padding: 3px 8px; border-radius: 6px; font-family: ui-monospace, Menlo, monospace; }
        .log-box { background: #09090C; border: 1px solid var(--border); border-radius: 10px; padding: 14px; font-family: ui-monospace, Menlo, monospace; font-size: 12px; color: #A3A3B2; height: 260px; overflow-y: auto; white-space: pre-wrap; line-height: 1.6; }
        table { width: 100%%; border-collapse: collapse; font-size: 13px; }
        th { text-align: left; padding: 10px; border-bottom: 1px solid var(--border); color: var(--text-secondary); font-weight: 600; font-size: 12px; }
        td { padding: 12px 10px; border-bottom: 1px solid #1E1E28; color: #E4E4E7; }
        .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.75); backdrop-filter: blur(4px); display: none; align-items: center; justify-content: center; z-index: 1000; }
        .modal-content { background: #1A1A22; border: 1px solid #333344; border-radius: 20px; padding: 32px; max-width: 400px; width: 90%%; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.6); }
        .qr-img { background: #FFF; padding: 12px; border-radius: 14px; margin: 20px 0; width: 260px; height: 260px; }
    </style>
</head>
<body>
    <header>
        <div class="brand">
            <div class="brand-icon">⚡</div>
            <div class="brand-text">
                <h1>Agent Remote Server</h1>
                <p>Server Management & Gateway Dashboard</p>
            </div>
        </div>
        <div class="header-status">
            <div class="badge-live"><span class="pulse"></span><span>ONLINE</span></div>
            <button class="btn btn-primary" onclick="openQRModal()">📷 Mã QR Ghép Nối</button>
        </div>
    </header>

    <div class="grid-2">
        <div class="card">
            <div class="card-title">
                <span>🌐 Kênh Kết Nối Từ Xa</span>
                <button class="btn" style="padding: 4px 10px; font-size: 12px;" onclick="refreshStatus()">🔄 Cập nhật</button>
            </div>
            <div class="info-row">
                <div><div class="label">☁️ Cloudflare Quick Tunnel (4G/5G)</div><div class="val" id="tunnelUrlText">%s</div></div>
                <div style="display: flex; gap: 6px;">
                    <button class="copy-btn" onclick="copyText('tunnelUrlText')">Sao chép</button>
                    <button class="copy-btn" style="color: var(--orange);" onclick="restartTunnel()">Làm mới</button>
                </div>
            </div>
            <div class="info-row">
                <div><div class="label">🔑 P2P Room Code (WebRTC)</div><div class="val" id="roomCodeText" style="font-size: 16px; color: #60A5FA;">%s</div></div>
                <div style="display: flex; gap: 6px;">
                    <button class="copy-btn" onclick="copyText('roomCodeText')">Sao chép</button>
                    <button class="copy-btn" style="color: var(--orange);" onclick="rotateRoom()">Đổi mã</button>
                </div>
            </div>
            <div class="info-row">
                <div><div class="label">📶 Wi-Fi LAN WebSocket</div><div class="val" id="lanUrlText">%s</div></div>
                <button class="copy-btn" onclick="copyText('lanUrlText')">Sao chép</button>
            </div>
            <div class="info-row" style="margin-bottom: 0;">
                <div><div class="label">⏱️ Uptime / Bộ nhớ RAM</div><div class="val" id="serverStats">Đang tải...</div></div>
            </div>
        </div>

        <div class="card">
            <div class="card-title">
                <span>📱 Thiết Bị Đang Kết Nối</span>
                <span id="clientCountBadge" style="font-size: 12px; color: var(--text-secondary);">0 Client</span>
            </div>
            <div style="overflow-x: auto;">
                <table>
                    <thead>
                        <tr><th>Địa chỉ IP</th><th>Thời điểm</th><th>Thời lượng</th><th>Ứng dụng / Thiết bị</th></tr>
                    </thead>
                    <tbody id="clientsTableBody">
                        <tr><td colspan="4" style="text-align: center; color: var(--text-secondary); padding: 30px;">Chưa có thiết bị nào kết nối. Mở ứng dụng trên điện thoại để ghép nối!</td></tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>

    <div class="card" style="margin-bottom: 24px;">
        <div class="card-title">
            <span>⚙️ Trạng Thái Coding Engines</span>
            <button class="btn" style="padding: 4px 10px; font-size: 12px;" onclick="loadEngines()">🔄 Quét lại Catalog</button>
        </div>
        <div class="engine-grid" id="enginesContainer">
            <div style="color: var(--text-secondary); font-size: 13px;">Đang tải danh mục engine...</div>
        </div>
    </div>

    <div class="card">
        <div class="card-title">
            <span>📋 Realtime Server Logs</span>
            <div style="display: flex; gap: 8px;">
                <button class="btn" style="padding: 4px 10px; font-size: 12px;" onclick="loadLogs()">🔄 Tải lại</button>
                <button class="btn" style="padding: 4px 10px; font-size: 12px;" onclick="clearLogs()">Xóa</button>
            </div>
        </div>
        <div class="log-box" id="logBox">Đang tải nhật ký server...</div>
    </div>

    <div class="modal-overlay" id="qrModal" onclick="if(event.target===this) closeQRModal()">
        <div class="modal-content">
            <h2 style="font-size: 18px; font-weight: 700; color: #FFF;">⚡ Quét Mã Ghép Nối</h2>
            <p style="font-size: 12px; color: var(--text-secondary); margin-top: 4px;">Mở ứng dụng Agent Remote trên Android/iOS và quét mã này.</p>
            <img class="qr-img" id="qrImage" src="/qr.png?t=%d" alt="QR Code">
            <div style="display: flex; justify-content: center;"><button class="btn" onclick="closeQRModal()">Đóng cửa sổ</button></div>
        </div>
    </div>

    <script>
        function openQRModal() { document.getElementById('qrImage').src = '/qr.png?t=' + Date.now(); document.getElementById('qrModal').style.display = 'flex'; }
        function closeQRModal() { document.getElementById('qrModal').style.display = 'none'; }
        function copyText(elemId) {
            const text = document.getElementById(elemId).innerText.trim();
            navigator.clipboard.writeText(text).then(() => { alert('Đã sao chép: ' + text); });
        }
        async function refreshStatus() {
            try {
                const res = await fetch('/api/status');
                const data = await res.json();
                if (data.tunnelUrl) document.getElementById('tunnelUrlText').innerText = data.tunnelUrl;
                if (data.roomCode) document.getElementById('roomCodeText').innerText = data.roomCode;
                const hrs = Math.floor(data.uptimeSec / 3600);
                const mins = Math.floor((data.uptimeSec %% 3600) / 60);
                const secs = data.uptimeSec %% 60;
                document.getElementById('serverStats').innerText = 
                    hrs + 'h ' + mins + 'm ' + secs + 's • ' + data.memoryAllocMB.toFixed(1) + ' MB RAM (Go ' + data.goVersion + ')';
                document.getElementById('clientCountBadge').innerText = data.activeClients + ' Client online';
                renderClients(data.clients || []);
            } catch (e) { console.error(e); }
        }
        function renderClients(clients) {
            const tbody = document.getElementById('clientsTableBody');
            if (!clients || clients.length === 0) {
                tbody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: var(--text-secondary); padding: 24px;">Chưa có thiết bị nào kết nối. Mở ứng dụng trên điện thoại để quét mã QR!</td></tr>';
                return;
            }
            tbody.innerHTML = clients.map(c => ` + "`" + `
                <tr>
                    <td style="font-family: ui-monospace, Menlo, monospace; font-weight: 600;">${c.remoteAddr}</td>
                    <td>${c.connectedAt}</td>
                    <td>${c.durationSec}s</td>
                    <td style="color: var(--text-secondary); font-size: 12px;">${c.userAgent || 'Agent Remote Mobile'}</td>
                </tr>` + "`" + `).join('');
        }
        async function restartTunnel() {
            if (!confirm('Bạn có chắc chắn muốn khởi động lại Cloudflare Quick Tunnel? URL mới sẽ được cấp.')) return;
            document.getElementById('tunnelUrlText').innerText = 'Đang cấp lại Tunnel URL...';
            try {
                const res = await fetch('/api/tunnel/restart', { method: 'POST' });
                const data = await res.json();
                if (data.tunnelUrl) document.getElementById('tunnelUrlText').innerText = data.tunnelUrl;
            } catch (e) { alert('Lỗi khởi động lại tunnel: ' + e); }
        }
        async function rotateRoom() {
            if (!confirm('Đổi mã P2P Room Code mới? Các thiết bị hiện tại cần quét lại.')) return;
            try {
                const res = await fetch('/api/room/rotate', { method: 'POST' });
                const data = await res.json();
                if (data.roomCode) { document.getElementById('roomCodeText').innerText = data.roomCode; refreshStatus(); }
            } catch (e) { alert('Lỗi đổi room: ' + e); }
        }
        async function loadEngines() {
            const container = document.getElementById('enginesContainer');
            container.innerHTML = '<div style="color: var(--text-secondary); font-size: 13px;">Đang tải danh mục engine từ máy Mac...</div>';
            try {
                const res = await fetch('/api/engines');
                const engines = await res.json();
                container.innerHTML = engines.map(eng => {
                    const statusPill = eng.installed ? '<span class="pill-active">✓ Sẵn sàng</span>' : '<span class="pill-inactive">Chưa cài đặt</span>';
                    const models = eng.models || [];
                    const modelPills = models.map(m => ` + "`" + `<span class="model-pill" title="${m.description || ''}">${m.label || m.id}</span>` + "`" + `).join('');
                    return ` + "`" + `
                        <div class="engine-card">
                            <h3><span>${eng.engine}</span>${statusPill}</h3>
                            <div class="engine-detail">Đường dẫn: <span style="font-family: ui-monospace, monospace; color: #FFF;">${eng.binary || 'Không rõ'}</span></div>
                            <div class="engine-detail">Dự án: <strong>${eng.projectsCount}</strong> • Phiên: <strong>${eng.convsCount}</strong></div>
                            <div class="engine-detail" style="margin-top: 8px; font-weight: 600; color: #FFF;">Models (${eng.modelsCount}):</div>
                            <div class="model-pills">${modelPills || '<span style="color: #666; font-size: 11px;">Chưa có model</span>'}</div>
                        </div>` + "`" + `;
                }).join('');
            } catch (e) { container.innerHTML = '<div style="color: #EF4444; font-size: 13px;">Lỗi tải engines: ' + e + '</div>'; }
        }
        async function loadLogs() {
            try {
                const res = await fetch('/api/logs');
                const data = await res.json();
                const box = document.getElementById('logBox');
                if (data.logs && data.logs.length > 0) {
                    box.innerText = data.logs.join('\n');
                    box.scrollTop = box.scrollHeight;
                } else {
                    box.innerText = '(Chưa có log mới)';
                }
            } catch (e) { console.error(e); }
        }
        function clearLogs() { document.getElementById('logBox').innerText = ''; }

        refreshStatus();
        loadEngines();
        loadLogs();
        setInterval(refreshStatus, 3000);
        setInterval(loadLogs, 3000);
    </script>
</body>
</html>`
