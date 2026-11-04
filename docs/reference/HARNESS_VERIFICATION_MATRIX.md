# Ma trận xác minh harness ba engine (T0.5)

Tài liệu xác minh harness CLI theo phiên bản mục tiêu: **Codex CLI 0.153.4**, **Antigravity CLI 1.5.0**, và **Claude Code 0.2.x+**. Đối chiếu từ tài liệu gốc, source adapter trong `server/internal/adapter/` và fixture đã kiểm chứng tại `server/testdata/cli_fixtures/`.

## 1. Bảng ma trận khả năng

| Khả năng | Trạng thái Codex (0.153.4) | Trạng thái Antigravity (1.5.0) | Trạng thái Claude Code (0.2.x) | Cơ chế kiểm chứng |
|---|---|---|---|---|
| **Lượt đơn thành công** | Đã tái hiện | Đã tái hiện | Đã tái hiện | Fixture `turn_success.jsonl` qua `Test*Fixtures` |
| **Tool call & Result** | Đã tái hiện | Đã tái hiện | Đã tái hiện | Fixture `tool_call.jsonl` qua `Test*Fixtures` |
| **Hủy khi đang chạy** | Đã tái hiện | Đã tái hiện | Đã tái hiện | SIGINT / Process group kill & Fixture `cancel.jsonl` |
| **Báo lỗi hết quota/rate** | Đã tái hiện | Đã tái hiện | Đã tái hiện | Fixture `quota_exceeded.jsonl` (`insufficient_quota`, `ResourceExhausted`) |
| **Phiên logic & Resume** | Đã tái hiện | Đã tái hiện | Đã tái hiện | Codex: `thread_id`; Antigravity: `--conversation`; Claude: `--resume` / `session_id` |
| **Multi-turn cùng session** | Đã tái hiện | Đã tái hiện | Đã tái hiện | Gửi liên tiếp với `conversation_id` / `thread_id` được bảo toàn |
| **Phê duyệt quyền (Approval)** | Đã tái hiện | Đã tái hiện | Đã tái hiện | Codex `item.started`, Antigravity `mode=plan`, Claude `tool_use` prompt |
| **Chế độ chỉ đọc (`:btw`)** | Đã tái hiện | Đã tái hiện | Đã tái hiện | Worktree snapshot / `plan` mode / Shell sandbox cách ly |
| **Thu thập Usage & Token** | Có tài liệu | Có tài liệu | Có tài liệu | Parser sự kiện hoàn tất lượt & transcript log |

## 2. Chi tiết theo từng engine

### 2.1. Codex CLI (0.153.4)
- **Cơ chế**: Chạy `codex exec --json`. Các sự kiện JSON theo dòng gồm `thread.created` / `thread.started`, `item.started`, `item.completed`, `turn.completed`, `turn.failed`.
- **Resume**: Giữ `thread_id` giữa các lượt gọi. Lượt tiếp theo gọi `codex exec --resume <thread_id>` hoặc tiếp nối session logic.
- **Hủy**: Gửi tín hiệu ngắt (interrupt / SIGINT) vào tiến trình; Codex trả về lỗi `interrupt: user aborted the turn`.
- **Hạn mức**: Khi hết quota, API trả về JSON lồng trong `turn.failed` với mã `insufficient_quota`.

### 2.2. Antigravity CLI (1.5.0)
- **Cơ chế**: Chạy `agy --print=<prompt> --output-format stream-json`.
- **Sự kiện**: `step_update` với `step_type` gồm `thinking`, `tool`, `agent_response`, và sự kiện cuối `result` mang `status: "SUCCESS" | "ERROR" | "CANCELED"`.
- **Resume**: Sử dụng cờ `--conversation <id>` để tiếp tục phiên hiện có.
- **Sandboxing**: Dùng `--mode plan` khi chỉ đọc hoặc yêu cầu phê duyệt bước thực thi; non-interactive chạy với cờ quyền cụ thể.

### 2.3. Claude Code (0.2.x+)
- **Cơ chế**: Chạy `claude -p --output-format stream-json`.
- **Sự kiện**: `stream_event` (`content_block_delta`), `assistant` (`tool_use`), `user` (`tool_result`), `result` (`is_error: bool`).
- **Resume**: Sử dụng `--resume <session_id>` để tiếp nối ngữ cảnh lịch sử.
- **Hủy**: Gửi SIGINT làm gián đoạn tiến trình đang chạy mà không làm hỏng file lịch sử phiên.
