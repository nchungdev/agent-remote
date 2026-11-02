# ADR-0002: Kiến trúc Kết nối Điều khiển Khi Ra Khỏi Mạng Chung (Out-of-LAN Remote Control)

- **Ngày:** 2026-10-31 | **Trạng thái:** Proposed & Actionable | **Dự án:** `agent-remote`
- **Tham chiếu chéo:** `adb-agent` ([ADR-0002](file:///Volumes/512GB/AI%20Workspace/adb-agent/docs/adr/0002-cloudflare-reverse-websocket-tunnel.md), [ADR-0003](file:///Volumes/512GB/AI%20Workspace/adb-agent/docs/adr/0003-p2p-webrtc-datachannel-tunnel.md), [ADR-0004](file:///Volumes/512GB/AI%20Workspace/adb-agent/docs/adr/0004-embedded-wireguard-tunnel.md))

---

## 1. Bối cảnh & Thách thức
Khi người dùng mang điện thoại ra ngoài (dùng mạng 4G/5G hoặc Wi-Fi quán cafe) để điều khiển các AI agent (Claude Code, Antigravity) chạy trên máy tính ở nhà/công ty:
- **Máy chủ Host (Mac/PC):** Nằm sau Router NAT gia đình hoặc tường lửa công ty, không có Public IP tĩnh. Mở cổng Inbound (Port Forwarding) trên router tiềm ẩn rủi ro an ninh rất cao.
- **Điện thoại di động (Android Client):** Nằm sau mạng CGNAT của nhà mạng di động, IP thay đổi liên tục khi di chuyển.
- **Mục tiêu:** Kết nối điều khiển ổn định 2 chiều, bảo mật cao, không phụ thuộc mở port router, và độ trễ stream token cực thấp.

---

## 2. Phân tích 3 Phương án Kiến trúc (Dựa trên bài học từ `adb-agent`)

| Tiêu chí | Phương án 1: Cloudflare Tunnel (WSS) | Phương án 2: WebRTC DataChannel (P2P) | Phương án 3: Embedded WireGuard (L3) |
| :--- | :--- | :--- | :--- |
| **Mô hình kết nối** | Outbound Reverse WSS qua Edge Cloud | P2P trực tiếp đục lỗ NAT (ICE/STUN) | Mạng ảo TUN L3 (VPN trong app) |
| **Thay đổi Code `agent-remote`** | **0 DÒNG (Dùng ngay lập tức)** | Cần tích hợp thư viện WebRTC trên KMP & Python | Cần nhúng `com.wireguard.android` + VpnService |
| **Quyền trên Android** | **Zero Permission** (chỉ cần Internet) | **Zero Permission** | Cần người dùng cấp quyền `VpnService` |
| **Ảnh hưởng VPN khác** | **Không** (Chạy song song VPN công ty/WARP) | **Không** | **Xung đột** (Chiếm slot VPN duy nhất của OS) |
| **Độ trễ (Latency)** | ~20 - 40ms qua CDN Anycast | Cực thấp (Direct P2P DTLS) | Thấp nhất (Kernel ChaCha20) |
| **Rủi ro Port Forwarding** | **Zero Port Open** | **Zero Port Open** | Cần endpoint UDP công khai |

---

## 3. Lộ trình Triển khai Đề xuất

### Giai đoạn 1: Giải pháp Kích hoạt Ngay (Zero-Code, Sẵn sàng ngay hôm nay)
Do `agent-remote` đã xây dựng hoàn chỉnh trên giao thức **WebSocket** (`ktor-client-websockets` trên App và Python Gateway ở Server), hệ thống **hỗ trợ sẵn 100%** giao thức bảo mật `wss://`:
1. Trên máy Mac/PC, kích hoạt Cloudflare Tunnel trỏ về `http://localhost:8765`:
   ```bash
   cloudflared tunnel --url http://localhost:8765
   ```
   *(Hoặc gán domain cố định: `wss://agent.yourdomain.com`)*
2. Trên Android App, chỉ cần nhập:
   * **Gateway URL:** `wss://agent.yourdomain.com` (hoặc link Quick Tunnel dạng `wss://*.trycloudflare.com`)
   * **Auth Token:** Token bí mật đã cấu hình trên server.
3. Người dùng ra ngoài 4G/5G điều khiển máy tính ở nhà hoàn toàn an toàn mà không cần mở bất kỳ cổng nào trên router.

### Giai đoạn 2: Tối ưu P2P WebRTC DataChannel (Tương lai khi cần loại bỏ trung gian)
- Áp dụng kinh nghiệm từ ADR-0003 của `adb-agent`: sử dụng WebRTC DataChannel nhị phân thông qua STUN công cộng (`stun:stun.l.google.com:19302`).
- Phù hợp khi người dùng muốn tự chủ 100% đường truyền P2P mà không cần tài khoản Cloudflare.

---

## 4. Quyết định (Decision)
- **Chấp thuận Phương án 1 (Cloudflare WSS Tunnel)** làm chuẩn kết nối Out-of-LAN tức thì cho `agent-remote` vì:
  1. Tận dụng 100% kiến trúc WebSocket hiện có.
  2. Không làm gián đoạn các kết nối VPN đang có trên điện thoại của lập trình viên.
  3. Hoạt động mượt mà khi chuyển mạng (Wi-Fi ↔ 4G/5G) nhờ cơ chế Reconnect Backoff của Ktor.
