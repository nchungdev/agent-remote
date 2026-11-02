# Implementation Plan: Universal Agent Remote (KMP / CMP)

Dự án **`agent-remote`** là ứng dụng di động đa nền tảng (Android, iOS) viết bằng **Kotlin Multiplatform (KMP) & Compose Multiplatform (CMP)** kết nối tới **Gateway Server** trên máy Mac/Linux để điều khiển từ xa các AI Coding Agent (**Antigravity, Claude Code, Codex, Aider**) sử dụng tài khoản đăng nhập chính chủ (không cần API key).

---

## Danh sách Task chi tiết & Lộ trình triển khai

### [Phase 1] Thiết lập Cấu hình Gradle & Môi trường KMP
- [ ] **Task 1.1**: Cấu hình `settings.gradle.kts`, `build.gradle.kts` gốc và `composeApp/build.gradle.kts` hỗ trợ Android + JVM/Desktop + iOS với Ktor, Kotlinx.Serialization, Coroutines, và Material 3.
- [ ] **Task 1.2**: Kiểm tra biên dịch thử nghiệm với `./gradlew tasks` đảm bảo hệ thống build KMP hợp lệ.

### [Phase 2] Gateway Server (Host trên Mac) & Universal Agent Adapter
- [ ] **Task 2.1**: Xây dựng Gateway Server (`server/gateway.py`) chuẩn WebSocket + REST API: Quản lý active sessions, streaming duplex, port bind an toàn (mặc định port 8765).
- [ ] **Task 2.2**: Xây dựng lớp **Agent Adapter**:
  - `BaseAgentAdapter`: Lớp trừu tượng chuẩn hóa I/O.
  - `AntigravityAdapter`: Adapter kết nối Antigravity I/O, transcript stream và tool approval.
  - `ClaudeCodeAdapter`: Adapter kết nối phiên Claude Code CLI (`claude`).
  - `MockAgentAdapter`: Adapter giả lập đầy đủ luồng Thinking -> Tool Call -> Approval -> Output dùng để kiểm thử tự động.
- [ ] **Task 2.3**: Định nghĩa đầy đủ schema sự kiện hai chiều (Client Actions vs Server Events).

### [Phase 3] Domain Layer (`core/domain`)
- [ ] **Task 3.1**: Định nghĩa các Model Domain cốt lõi:
  - `AgentType`: Enum định danh agent (`ANTIGRAVITY`, `CLAUDE_CODE`, `CODEX`, `AIDER`).
  - `AgentSession`: Quản lý ID phiên, tên dự án, thư mục làm việc, trạng thái kết nối.
  - `ChatMessage` & `MessageRole`: Lịch sử trao đổi, hỗ trợ metadata.
  - `ThinkingBlock`: Dữ liệu chuỗi suy nghĩ nội bộ (Chain-of-Thought).
  - `ToolCall` & `ToolStatus`: Tên tool, câu lệnh shell, tham số, kết quả trả về.
  - `ApprovalRequest`: Yêu cầu phê duyệt hành động từ agent tới user.
  - `Artifact`: Tài liệu, file diff, code block.
- [ ] **Task 3.2**: Xây dựng `AppResult<T, DomainError>` và các `DomainError` cụ thể theo chuẩn Clean Architecture (bảo vệ chống rò rỉ ngoại lệ).
- [ ] **Task 3.3**: Định nghĩa giao diện Repository & Gateway Client (`AgentGatewayClient`, `SessionRepository`).

### [Phase 4] Data Layer (`core/data`)
- [ ] **Task 4.1**: Triển khai `KtorWebSocketClient`: Kết nối tới WebSocket Gateway, serialization/deserialization JSON qua `kotlinx.serialization`.
- [ ] **Task 4.2**: Xây dựng bộ điều khiển trạng thái kết nối (`ConnectionState`: Disconnected, Connecting, Connected, Reconnecting, Error).
- [ ] **Task 4.3**: Tích hợp cơ chế tự động kết nối lại (Auto-reconnect with exponential backoff) và Heartbeat Ping/Pong.
- [ ] **Task 4.4**: Triển khai `InMemorySessionRepository` lưu trữ phiên và tin nhắn cục bộ.

### [Phase 5] Presentation Layer & MVI Pattern (`features/chat/presentation`)
- [ ] **Task 5.1**: Thiết kế Hợp đồng MVI nghiêm ngặt theo chuẩn `chrisbanes/tivi`:
  - `ChatUiState`: Trạng thái bất biến (Immutable State).
  - `ChatIntent`: Ý định người dùng (Gửi prompt, Bấm duyệt lệnh, Chọn Agent, Hủy task).
  - `ChatUiEffect`: Tác vụ một lần (Hiển thị Snackbar, Rung haptic feedback, Cuộn cuối trang).
- [ ] **Task 5.2**: Xây dựng `ChatViewModel` điều phối dữ liệu từ Domain sang UiState qua Kotlin Coroutines `StateFlow`.
- [ ] **Task 5.3**: Xây dựng các UI Components đặc thù:
  - `AgentSelectorHeader`: Thanh chọn nhanh Agent đang điều khiển.
  - `ThinkingCard`: Khối hiển thị suy nghĩ (hỗ trợ đóng/mở collapsible).
  - `ToolCallCard`: Khối lệnh shell/file actions có badge trạng thái (Running, Success, Failed).
  - `ApprovalActionDialog`: Khối thông báo duyệt lệnh 1-chạm (Approve / Reject).
  - `ChatMessageBubble`: Bong bóng chat hỗ trợ syntax highlight.
- [ ] **Task 5.4**: Lắp ráp màn hình `ChatScreen` với giao diện Material 3 mượt mà, tự động cuộn theo token stream.

### [Phase 6] Kiểm thử, Audit & Đóng gói
- [ ] **Task 6.1**: Viết Unit Tests cho Domain Layer và MVI State Reducer trong `commonTest`.
- [ ] **Task 6.2**: Viết kịch bản kiểm thử kết nối tự động cho Gateway Server.
- [ ] **Task 6.3**: Cập nhật `.agents/CODEBASE_MAP.md` qua `project_pro.py map`.
- [ ] **Task 6.4**: Kiểm tra toàn diện qua `project_pro.py audit --fail-on-error` (Kiểm soát giới hạn 300/450 dòng, chống rò rỉ domain, chống commit secret).
- [ ] **Task 6.5**: Tạo commit Git hoàn tất giai đoạn khởi tạo nền tảng.
