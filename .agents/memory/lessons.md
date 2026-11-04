# Project Lessons Learned & Continuous Memory — `agent-remote`

> Tự động cập nhật theo Gate 6 của 6-Gate Execution Harness (`project-pro`). Agent bắt buộc phải đọc file này ở Gate 1 trước mỗi task để không lặp lại sai lầm.

### [2026-11-01] Kiến trúc P2P WebRTC DataChannel qua MQTT Signaling
* **Vấn đề / Gotcha:**
  - Chuẩn hóa mã phòng (Room Code): Khi hiển thị mã phòng có dấu gạch ngang (ví dụ `385-334`) để người dùng dễ đọc, nếu một bên dùng mã có gạch còn một bên strip gạch thì topic MQTT (`agent-remote/signal/{room}/...`) sẽ lệch nhau hoàn toàn, dẫn đến signaling không bao giờ tới đích.
  - Race condition khởi tạo Signaling: WebRTC `createOffer` và ICE candidate gathering là bất đồng bộ. Nếu gửi SDP offer hoặc Candidate trước khi MQTT client kết nối xong, các message sẽ bị drop âm thầm khiến đối phương không nhận được offer.
  - Dispatcher trên CMP Desktop: Khi chạy CMP trên desktop JVM/macOS, bắt buộc phải có dependency `kotlinx-coroutines-swing` để cung cấp `Dispatchers.Main`, nếu không app sẽ crash ngay khi khởi tạo với lỗi `Module with the Main dispatcher is missing`.
* **Giải pháp / Rule đã học:**
  - Luôn chuẩn hóa room code (`strings.ReplaceAll(code, "-", "")` / `code.replace("-", "")`) thành chuỗi số thuần túy trước khi tạo topic MQTT.
  - Triển khai hàng đợi pending (`pendingMessages`) trong Signaling client để buffer candidate/offer cho đến khi kết nối MQTT hoàn tất, đồng thời chủ động gọi `sig.connect()` trước khi tạo WebRTC offer.
  - Cấu hình failover đa broker công cộng (`tcp://broker.emqx.io:1883`, `tcp://broker.hivemq.com:1883`) để tránh rủi ro kết nối khi một node bị quá tải.


---

### [2026-10-31] Tách biệt Release Pipeline cho Monorepo (Client & Server)
* **Vấn đề / Gotcha:** 
  - Khi release Monorepo (gồm KMP Client và Python Server), gom tất cả vào 1 workflow build all sẽ gây lãng phí runner minutes (đặc biệt macOS runner), dễ chạm rate limit và concurrency limit của GitHub Actions.
  - Endpoint GitHub API `/releases/latest` chỉ trả về bản release mới nhất toàn repo. Nếu vừa release Server thì OTA updater trên App sẽ bị "mất" file `.apk` nếu chỉ gọi `/latest`.
* **Giải pháp / Rule đã học:**
  - Tách độc lập tag: `app-v*` chỉ build Android APK, `server-v*` chỉ build macOS/Linux/Windows binaries + Docker.
  - In-app OTA Updater luôn query `/releases?per_page=10` và duyệt tìm bản release gần nhất có chứa asset `.apk`, dùng Regex strip prefix (`app-v`, `v`) trước khi so sánh SemVer.
  - Luôn cấu hình `signingConfig = signingConfigs.getByName("debug")` cho `release` build trong Android Gradle khi xuất bản thử nghiệm để tránh lỗi `INSTALL_PARSE_FAILED_NO_CERTIFICATES`.
  - Trên macOS, có thể dễ dàng đóng gói CLI binary thành chuẩn macOS `.app` bundle và dùng công cụ native `hdiutil` tạo `.dmg` installer (kèm shortcut `/Applications`) mà không cần cài thêm công cụ bên thứ ba.

### [2026-10-31] Kết nối Out-of-LAN (Ra khỏi mạng chung)
* **Vấn đề / Gotcha:** 
  - Khi điện thoại ra ngoài dùng 4G/5G, IP nằm sau CGNAT và máy Mac ở nhà nằm sau Router NAT, không thể mở cổng port forwarding trên router vì rủi ro an ninh.
* **Giải pháp / Rule đã học:**
  - Vì `agent-remote` đã dùng WebSocket Client (`Ktor`) và Server (`websockets`), giải pháp nhanh nhất và an toàn nhất là Outbound WSS Tunnel qua `cloudflared` (0 dòng code thay đổi, zero inbound port, không chiếm slot VpnService trên Android).
  - WebRTC DataChannel (P2P STUN) là bước tối ưu hóa tiếp theo nếu muốn loại bỏ hoàn toàn trung gian (tham chiếu `adb-agent` ADR-0003).

### [2026-11-05] Kiến trúc Phiên cha (Parent Session) & Quản lý Đa Agent
* **Vấn đề / Gotcha:**
  - Định tuyến lệnh theo vai trò: Khi chuyển đổi agent giữa phiên làm việc chung, việc ghi đồng thời lên cùng một checkout Git sẽ gây conflict và drift trạng thái không kiểm soát được.
  - Context leak và command injection: Output hoặc git diff không đáng tin cậy nếu nối trực tiếp vào system prompt của agent tiếp theo có thể chứa prompt injection (jailbreak).
  - Tràn kích thước file (God-file) trong CMP: Màn hình chat phức tạp (`ChatViewModel.kt`, `ChatScreen.kt`, `ChatInputBar.kt`) dễ vượt ngưỡng 450 LOC khi gom cả xử lý mention popup, writer badge và event routing.
* **Giải pháp / Rule đã học:**
  - Quy tắc Đơn Quyền Ghi (Single-Writer Law & CAS Lease): Mỗi checkout chỉ có duy nhất 1 writer agent tại một thời điểm, kiểm soát bằng Compare-And-Swap (CAS) `writer_revision` nguyên tử trong SQLite. Các agent khác (`@agent:btw`) chỉ đọc trên snapshot worktree độc lập.
  - Bọc ngữ cảnh bằng Nonce ngẫu nhiên: Context builder luôn bọc dữ liệu ngoại lai bằng ranh giới `<<<EXTERNAL_UNTRUSTED_DATA_NONCE_<hex>>>>` và sanitize các delimiter trùng lặp để ngăn injection.
  - Phân rã Component & Dispatcher sớm: Tách `ChatPromptParser`, `ChatEventDispatcher` và `ChatInputBarMenus` thành các file độc lập `<= 300 LOC` trước khi vượt ngưỡng cảnh báo.
  - Fixture Contract Tests cho Harness: Lưu mẫu output thực (thành công, tool call, hủy, hết quota) trong `server/testdata/cli_fixtures/` cho từng CLI (Codex, Antigravity, Claude) và chạy contract tests để đảm bảo parser không bị vỡ khi CLI cập nhật.



---
