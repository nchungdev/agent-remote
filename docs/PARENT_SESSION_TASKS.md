# Danh sách task: session cha và tương tác giữa các agent

> **Tài liệu bàn giao.** Khảo sát ngày 2026-11-04 trên `agent-remote@f9d8a79` và `nchungdev/agent-bridge@b97b025`. Số dòng và tên hàm có thể lệch, phải kiểm tra lại trước khi sửa.
>
> Tài liệu này **thay** [SESSION_HANDOFF_PLAN.md](./SESSION_HANDOFF_PLAN.md) về kiến trúc; phần nghiên cứu và bảo mật của plan đó vẫn dùng được.

## 1. Mục tiêu

Một **session cha** là nguồn sự thật của công việc và chứa nhiều **session con**, mỗi session con ứng với một agent (Claude Code, Codex, Antigravity).

```text
Session cha
├── Event log có thứ tự, tóm tắt, workspace, quyền, writer hiện tại
└── Session con: engine, nativeId, model, effort, seenSeq, trạng thái
```

**Trong phạm vi lần này:**
- Mức 1, chuyển lượt: Claude dừng, Codex làm tiếp.
- Mức 2, hỏi ý kiến: `@codex review diff này`, chạy chỉ đọc, trả lời vào timeline.

**Ngoài phạm vi, không tự làm:** agent tự giao việc cho nhau, agent tự trao đổi nhiều lượt, ghi song song trên nhiều worktree, tự động chuyển engine khi hết quota, kho memory riêng.

## 2. Quyết định

Agent thực hiện **không tự đổi** các mục dưới đây. Mục "chờ chủ repo" phải được xác nhận ở T0.2; các mục khác dùng theo mặc định.

| ID | Quyết định | Mặc định | Trạng thái |
|---|---|---|---|
| D1 | Nơi đặt lõi dùng chung | Repo mới `nchungdev/agent-core`, gắn vào `agent-remote` qua submodule và `replace`, giống `shared/remote-tunnel` | **Chờ chủ repo** |
| D2 | Cách kế thừa Agent Bridge | Lấy `core`, `store`, `manager`, `enginetest`, adapter v2; không lấy `internal/bridge` (handoff kiểu cũ) | **Chờ chủ repo** |
| D3 | License của `agent-core` và `agent-bridge` | Chủ repo chọn; `agent-bridge` hiện chưa có license | **Chờ chủ repo** |
| D4 | Kho dữ liệu | SQLite (`modernc.org/sqlite`) trong thư mục dữ liệu của server | Mặc định |
| D5 | Quyền ghi | Mỗi session cha có tối đa **một writer**; agent khác chỉ đọc | Mặc định |
| D6 | Kiểu tiến trình | Dùng `Engine`/`Session` sống lâu của Bridge; cơ chế `--resume` theo prompt chỉ còn là legacy | Mặc định |
| D7 | Triển khai | Bật bằng feature flag; luồng cũ vẫn chạy cho tới khi T5.6 hoàn tất | Mặc định |
| D8 | Chi phí | Không tự chuyển engine, không tự bơm memory; tóm tắt cuốn chiếu tắt mặc định cho tới khi đo xong T5.3 | Mặc định |

## 3. Quy tắc cho agent thực hiện

1. **Trước mỗi phiên:** đọc `PROJECT_MEMORY.md`, `agent-remote/AGENTS.md`, `.agents/CODEBASE_MAP.md`, `.agents/memory/lessons.md`. Không sửa file của task đang `[IN_PROGRESS]` nếu chưa được yêu cầu.
2. **Repo có thể đang được sửa đồng thời.** Chạy `git status` và đọc lại file ngay trước khi sửa; không ghi đè thay đổi của người khác; làm trên branch riêng theo từng milestone.
3. **Kích thước file:** trên 300 dòng là cảnh báo, trên 450 dòng là thất bại. `ChatViewModel.kt` và `ChatScreen.kt` đều đã trên 400 dòng, gần ngưỡng, logic mới phải ở file khác.
4. **Không dùng `--no-verify`.** Không hardcode secret, IP hay mã phòng. Không log prompt.
5. **Commit, push, release và tạo repo** chỉ khi chủ repo yêu cầu. Chỉ release khi không còn message trong queue.
6. **Task có nhãn `💰` tốn quota thật:** phải hỏi trước.
7. **Xong mỗi task:** chạy test, tick vào file này, ghi một dòng vào `PROJECT_MEMORY.md`. Sửa 3 lần không được thì dừng và báo cáo.
8. **Lệnh Kotlin:** `AGENTS.md` ghi `:composeApp:...`, nhưng wrapper nằm ở `app/gradlew`. Kiểm tra tên project thật ở T0.3 rồi cập nhật tài liệu này.

Kích thước: **S** dưới nửa ngày, **M** 1 ngày, **L** 2–3 ngày. Đây chỉ là ước lượng.

## 4. Milestone và thứ tự

```text
M0 Chuẩn bị → M1 Tách lõi → M2 Session cha → M3 Server Agent Remote → M4 App → M5 Kiểm chứng
```

Có thể làm song song: T2.3 với T2.4; T4.5 với T4.7 sau khi có T4.3.

---

## M0 — Chuẩn bị

- [ ] **T0.1 Đọc bối cảnh (S).** Đọc tài liệu này, `MODEL_COST_PLAN.md`, `SESSION_HANDOFF_PLAN.md`, và các file Bridge: `internal/core/core.go`, `internal/manager/manager.go`, `internal/manager/policy.go`, `internal/store/store.go`, `internal/enginetest/fake.go`.
  **Xong khi:** liệt kê được điểm khác giữa tài liệu và code hiện tại; báo ngay nếu có khác biệt làm đổi thiết kế.
- [ ] **T0.2 Chốt quyết định với chủ repo (S).** Hỏi D1, D2, D3. Nhờ chủ repo tạo repo nếu chọn D1. Hỏi Agent Bridge còn được dùng ở đâu, vì T1.6 sẽ ảnh hưởng bản đang chạy.
  **Xong khi:** bảng ở mục 2 đã cập nhật. **Không bắt đầu M1 nếu chưa xong.**
- [ ] **T0.3 Chạy baseline (S).** Chạy test Go của `agent-bridge`, `agent-remote/server`, và test/lint Kotlin. Ghi số test và lỗi có sẵn.
  **Xong khi:** có bảng baseline; lỗi có sẵn được tách khỏi lỗi mới.
- [ ] **T0.4 Bắt mẫu output CLI 💰 (M).** Với mỗi CLI: một lượt thành công, một tool call, một lần hủy, một lỗi hết quota nếu có. Lưu vào `testdata` kèm phiên bản CLI, đã xóa nội dung nhạy cảm.
  **Xong khi:** contract test ở T1.4 dùng được các fixture này.

---

## M1 — Tách lõi dùng chung

Giai đoạn này **không đổi hành vi**.

- [ ] **T1.1 Tạo module (S).** Tạo `agent-core` theo D1. Thống nhất phiên bản: Bridge đang Go 1.26 và sqlite 1.60.1; Agent Remote đang Go 1.27.3 và sqlite 1.61.1. Thêm CI chạy `go test -race ./...`.
- [ ] **T1.2 Chuyển `core` (S).** Chuyển `Event`, `Engine`, `Session`, `Capabilities`, `Quota`, trạng thái, kèm test. Gói này chỉ dùng stdlib.
- [ ] **T1.3 Chuyển `store` (M).** Chuyển schema `v2_*`, `Redact`, event log, binding, approval, working state. Giữ nguyên tên bảng để DB cũ vẫn chạy.
  **Xong khi:** migration chạy được trên **bản sao** DB Bridge thật.
- [ ] **T1.4 Chuyển adapter (L).** Chuyển `claude`, `codex`, `agy`, `proc`, `enginetest`, fake CLI trong `testdata`. Thêm contract test chung bằng fixture T0.4; tất cả adapter phải đi qua cùng bộ test.
- [ ] **T1.5 Chuyển `manager` và `policy` (M).** Module không được import HTTP, WebSocket hay UI.
  **Xong khi:** `go list -deps` không có các gói này.
- [ ] **T1.6 Cho Agent Bridge dùng module (M).** Đổi import, xóa bản cũ, không đổi API.
  **Xong khi:** test Bridge bằng baseline; smoke test tạo hội thoại, gửi prompt, duyệt quyền, chuyển engine bằng fake engine.

---

## M2 — Session cha trong lõi

- [ ] **T2.1 Mô hình dữ liệu (M).** Ánh xạ conversation thành session cha, binding thành session con. Thêm vào event: `turn_id`, `origin_engine`, `requested_by`, `purpose` (`work` hoặc `consult`). Thêm vào binding: `model`, `effort`, `role`, `native_mtime`. Migration chỉ thêm cột, có giá trị mặc định.
  **Xong khi:** test migration trên DB cũ và DB trống; mở DB đã migrate bằng code cũ không hỏng dữ liệu, hoặc ghi rõ cần sao lưu.
- [ ] **T2.2 Quyền ghi (M).** Thêm `writer_engine` vào session cha, cùng hàm giành và trả quyền có khóa. Chỉ writer được chạy ở mode có quyền ghi.
  **Xong khi:** test race 2 agent cùng giành quyền; agent thường trả về lỗi có kiểu; `go test -race` đạt.
- [ ] **T2.3 Dựng ngữ cảnh theo mục đích (L).** Thêm `BuildContext(purpose, session, child, opts)`, thay `manager.handoff`:
  - `work`: tóm tắt, event sau `seenSeq`, trạng thái git.
  - `consult`: câu hỏi, diff, tóm tắt; không gửi cả lịch sử.
  - Giới hạn khoảng 24.000 ký tự, bỏ phần cũ trước.
  - Bọc bằng nonce ngẫu nhiên và dòng ghi rõ đây là dữ liệu, không phải chỉ dẫn; output của agent khác cũng theo quy tắc này.
  - Có mục "Source fidelity" nêu phần bị bỏ.
  **Xong khi:** có test cho vượt ngân sách, ký tự tiếng Việt, nội dung giả dấu đóng, câu lệnh độc trong lịch sử.
- [ ] **T2.4 Đọc trạng thái git (M).** Viết lại, sửa các lỗi đã thấy ở `CaptureWorkspaceState`:
  - dùng `rev-parse` để hỗ trợ thư mục con và worktree, không kiểm tra `.git` tại chỗ;
  - dùng `status --porcelain -z` để đúng với khoảng trắng và đổi tên;
  - so với `HEAD` để tính cả phần đã stage;
  - dùng `-c core.fsmonitor=false`, `GIT_OPTIONAL_LOCKS=0`, timeout 5 giây.
  **Xong khi:** test bằng repo thật cho từng trường hợp trên và thư mục không phải repo.
- [ ] **T2.5 Chuyển lượt (M).** `SwitchWriter(session, to, opts)`: chỉ chạy khi writer cũ rảnh, hoặc hủy xong theo yêu cầu người dùng; chuyển quyền ghi; ghi event chuyển; lượt sau dùng T2.3. Không dùng lại kiểu băm git của `SyncHandoff`, vì nó bỏ qua tiến triển chỉ nằm trong hội thoại.
  **Xong khi:** test hội thoại không đổi file vẫn đồng bộ; quay lại engine cũ chỉ nhận phần chưa thấy; lỗi giữa chừng không làm mất quyền ghi.
- [ ] **T2.6 Hỏi ý kiến (L).** `Consult(session, to, question, opts)`: session con chạy chỉ đọc, ép mode theo `Capabilities`; engine không hỗ trợ chỉ đọc thật thì **từ chối**; kết quả ghi với `purpose=consult` và **không tự chuyển vào writer**; có thao tác riêng để người dùng gửi nó cho writer dưới dạng dữ liệu.
  **Xong khi:** test agent được hỏi không ghi được file; kết quả không làm thay đổi `seenSeq` của writer ngoài ý muốn; hủy được; không thể tự gọi tiếp agent khác.
- [ ] **T2.7 Giới hạn và hủy (S).** Giới hạn số consult đồng thời, độ dài câu trả lời, thời gian chạy; ghi `requested_by` để thấy chuỗi gọi. Hủy session cha phải dừng mọi session con.
- [ ] **T2.8 Duyệt quyền (M).** Mỗi yêu cầu ghi rõ session cha, agent, người khởi xướng. Quyền không truyền qua các agent. Dùng `DefaultPolicy` sẵn có; lệnh leo thang đặc quyền luôn hỏi.
- [ ] **T2.9 Nhập phiên CLI đã có (M).** Tạo session cha từ phiên gốc đã có; session con trỏ tới `nativeId` cũ để giữ cache; chỉ nhập metadata và tin nhắn gần đây. Dùng regex kiểm tra id, từ chối `..`, `*`, `--`.
- [ ] **T2.10 Phát hiện lệch (S).** Nếu `native_mtime` đổi ngoài Agent Remote, ghi event `external_change` và cảnh báo; không tự gộp.
- [ ] **T2.11 Usage và quota (M).** Lưu `EvUsage` theo session con và cộng dồn theo session cha, có cache đọc và ghi. Dùng lại `QuotaProvider`. Lỗi `quota_exceeded` chỉ phát gợi ý. Thay nhận dạng lỗi dựa trên chuỗi bằng fixture T0.4 khi có thể.
- [ ] **T2.12 Tóm tắt cuốn chiếu (S).** Giữ `maybeSummarize`, mặc định tắt theo D8, ghi usage riêng, thử engine đã đăng nhập theo thứ tự cấu hình.

---

## M3 — Server Agent Remote

- [ ] **T3.1 Gắn module (S).** Thêm submodule và `replace` trong `server/go.mod`, giống `remote-tunnel`. Cập nhật script build và CI `release-server.yml`.
- [ ] **T3.2 Gom xử lý action (M).** `gateway/server.go` và `webrtc/hub.go` đang lặp logic `SEND_PROMPT`, `GET_HISTORY`, v.v. Tách thành bộ xử lý dùng chung trước khi thêm action mới.
  **Xong khi:** test chạy cùng kịch bản trên cả hai transport; hành vi cũ không đổi.
- [ ] **T3.3 Protocol session (M).** Thêm action `SESSION_CREATE`, `SESSION_LIST`, `SESSION_GET`, `SESSION_SEND`, `SESSION_SWITCH_WRITER`, `SESSION_CONSULT`, `SESSION_FORWARD_CONSULT`, `SESSION_IMPORT`, `SESSION_DECIDE`, `SESSION_CANCEL`. Thêm event `SESSION_EVENT` có `sessionId`, `seq`, và `features: ["sessions_v2"]` trong `SESSION_INIT`. Giữ nguyên action cũ.
  **Xong khi:** có tài liệu protocol và test tuần tự hóa; app cũ vẫn chạy được.
- [ ] **T3.4 Khởi tạo lõi (M).** Tạo store và manager khi server khởi động, sau feature flag (D7). Dùng thư mục dữ liệu hiện có, quyền file 0600. Tắt server đúng cách phải suspend tiến trình con.
- [ ] **T3.5 Phát event và nối lại (L).** Nhiều client xem cùng session; client gửi `afterSeq` khi nối lại để không mất và không trùng event. Mạng di động và chuyển LAN/tunnel phải được coi là tình huống bình thường. Có backpressure cho client chậm.
  **Xong khi:** test ngắt giữa lượt rồi nối lại thấy đủ event, kể cả yêu cầu duyệt đang chờ.
- [ ] **T3.6 Bảo mật (M).** Mọi action mới đi qua xác thực hiện có trên cả WebSocket và WebRTC. Kiểm tra workspace theo danh sách cho phép; không lặp lại lỗi thiếu `PathAllowed` ở `handoff-content` của Bridge, và sửa lỗi đó ở Bridge nếu còn dùng. Lọc secret trước khi lưu và gửi. Không log nội dung.
  **Xong khi:** có test từ chối khi thiếu token, sai đường dẫn, sai id.
- [ ] **T3.7 Test tích hợp (M).** Dùng fake engine đi hết luồng tạo, gửi, duyệt, chuyển writer, consult, hủy, nối lại trên cả hai transport, với `-race`.

---

## M4 — App

Thứ tự Domain → Data → State → UI. Mọi thứ nằm sau kiểm tra `features`; server cũ vẫn dùng luồng cũ.

- [ ] **T4.1 Domain (S).** Thêm `ParentSession`, `ChildSession`, `SessionEvent`, chỉ dùng kiểu thuần, không import UI hay HTTP.
- [ ] **T4.2 Data (M).** Thêm DTO, mapper và phương thức ở `AgentGatewayClient.kt`; cập nhật `KtorWebSocketClient.kt`, `WebRtcDataChannelClient.kt`, `AndroidCompositeGatewayClient.kt`, `FakeAgentGatewayClient.kt`. Lỗi trả về dạng `DomainError` có kiểu.
- [ ] **T4.3 State (L).** Tạo `SessionTimelineReducer` thuần và `SessionController` riêng, không nhồi vào `ChatViewModel.kt`. Reducer xử lý thứ tự theo `seq`, event trùng, nối lại, gộp text delta. `activeConversationId` chuyển thành `activeSessionId` chỉ trong luồng mới.
  **Xong khi:** có unit test cho event đến sai thứ tự, trùng và thiếu.
- [ ] **T4.4 Timeline (M).** Mỗi tin có nhãn agent; có dấu "Đã chuyển sang ..." và thẻ consult riêng. Tách component khỏi `ChatScreen.kt`.
- [ ] **T4.5 Thanh agent (M).** Hiện writer hiện tại, agent trong session, nút thêm hoặc chuyển. Chuyển khi đang chạy phải hỏi xác nhận. Lần đầu gọi một agent cần nêu việc này tốn thêm token và gửi dữ liệu cho nhà cung cấp đó.
- [ ] **T4.6 Gọi `@agent` (M).** Dùng lại menu `@` sẵn có. `@codex ...` là consult, không tự đổi writer. Có thao tác "Gửi cho agent đang làm".
- [ ] **T4.7 Drawer (M).** Gom theo session cha trong project, xem được session con, có thao tác nhập phiên CLI cũ. Danh sách phải có phân trang.
- [ ] **T4.8 Duyệt quyền và quota (S).** Thẻ duyệt ghi agent và người khởi xướng. Hết quota thì hiện nút chuyển engine, **không tự chuyển**. Hiện usage theo session cha, ghi rõ số là ước tính.
- [ ] **T4.9 Tương thích (S).** Test cả ba trường hợp: app mới với server mới, app mới với server cũ, app cũ với server mới.
- [ ] **T4.10 Kiểm tra trên thiết bị (M).** Chạy trên emulator và desktop với fake/Mock. Kiểm tra stream dài không giật, xoay màn hình, bàn phím, ngắt mạng giữa lượt.

---

## M5 — Kiểm chứng và phát hành

- [ ] **T5.1 Toàn bộ test (S).** Chạy test Go với `-race`, test Kotlin, lint, cổng bảo mật. So với baseline T0.3.
- [ ] **T5.2 Thử thật 💰 (M).** Trên repo test, không dùng repo thật:
  - Mọi cặp chuyển lượt giữa 3 engine, kể cả quay lại engine cũ.
  - Consult khi writer đang rảnh và khi đang chạy.
  - Consult cố gắng ghi file phải thất bại.
  - Chèn lệnh độc vào lịch sử để kiểm tra agent đích không làm theo.
  - Ngắt mạng và đổi LAN/tunnel giữa lượt.
- [ ] **T5.3 Đo 💰 (M).** Trên ít nhất 10 task thật, so sánh với một engine chạy một mình: token lượt đầu sau khi chuyển, tỷ lệ cache hit khi quay lại, số lần phải giải thích lại, thời gian. Quyết định có bật tóm tắt cuốn chiếu không.
  **Đạt khi:** engine đích nắm đúng việc đang dở trong ít nhất 8/10 lần; quay lại engine cũ không nạp lại toàn bộ lịch sử.
- [ ] **T5.4 Tài liệu (S).** Viết ADR mới trong `docs/architecture/`, cập nhật `.agents/CODEBASE_MAP.md`, tài liệu protocol. Đánh dấu `SESSION_HANDOFF_PLAN.md` đã được thay thế và cập nhật những mục của `MODEL_COST_PLAN.md` đã được xử lý.
- [ ] **T5.5 Phát hành có kiểm soát (S).** Chỉ khi chủ repo yêu cầu và queue trống. Bật flag cho một server trước; sao lưu binary và DB; có đường quay lại.
- [ ] **T5.6 Bỏ luồng cũ (M).** Làm ở lượt phát hành sau, khi đã có số liệu ổn định và chủ repo đồng ý: bỏ `cliRunner` theo prompt và action cũ.

---

## 5. Tiêu chí hoàn thành chung

- [ ] Một session cha chứa được cả ba agent, và người dùng thấy một timeline duy nhất.
- [ ] Chuyển lượt không cần giải thích lại; quay lại agent cũ dùng đúng session con và chỉ nhận phần chưa thấy.
- [ ] `@agent` hoạt động ở chế độ chỉ đọc và không tự truyền kết quả.
- [ ] Không bao giờ có hai agent cùng có quyền ghi trong một session cha.
- [ ] Output của agent không thể tự ra lệnh hay nâng quyền cho agent khác.
- [ ] Ngắt kết nối không làm mất event hay yêu cầu duyệt.
- [ ] Server cũ, app cũ và luồng cũ vẫn hoạt động trong thời gian chuyển đổi.

## 6. Rủi ro cần báo ngay nếu gặp

| Tình huống | Cách xử lý |
|---|---|
| CLI không cho chạy tiến trình sống lâu hoặc chỉ đọc thật | Dừng tính năng cho engine đó, ghi vào `Capabilities`; không giả lập |
| Module lõi kéo theo dependency của UI hoặc HTTP | Dừng và tách lại ranh giới, không chấp nhận tạm |
| Đổi Agent Bridge làm hỏng bản đang chạy | Quay lại ngay, báo chủ repo |
| Token lượt đầu vượt mức dự kiến | Giảm ngân sách ở T2.3, báo số liệu |
| Sửa cùng file với thay đổi đang diễn ra | Dừng phần đó, báo; không ghi đè |

## 7. Lời nhắc giao việc

```text
Implement docs/PARENT_SESSION_TASKS.md in agent-remote, one task at a time, starting at the first unchecked task.
Read section 3 first and follow it strictly. Do not start M1 until T0.2 is confirmed by the repo owner.
Keep scope to the listed task. Ask before tasks marked 💰, and before any commit, push, release, or repo creation.
After each task, run its tests, tick it, add one line to PROJECT_MEMORY.md, and report results and anything unverified.
```
