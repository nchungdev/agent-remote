# Roadmap phiên cha và tương tác giữa các agent

> **Tài liệu bàn giao.** Khảo sát ngày 2026-11-04 trên `agent-remote@f9d8a79` và `nchungdev/agent-bridge@b97b025`. Rà soát triển khai đối chiếu thêm với `agent-remote@0b95773`, working tree, source Bridge tại SHA trên và metadata CLI cục bộ. Agent Bridge là prototype lịch sử. Agent Remote sở hữu thiết kế, lưu trữ, adapter và phát hành của hệ thống mới.
>
> Tham khảo [nghiên cứu handoff và bảo mật ban đầu](./archive/SESSION_HANDOFF_PLAN.md) trong kho lưu trữ.

## 1. Mục tiêu

Một **session cha** là nguồn sự thật của công việc và chứa nhiều **session con**, mỗi session con ứng với một agent (Claude Code, Codex, Antigravity).

```text
Session cha
├── Event log có thứ tự, tóm tắt, workspace, quyền, writer hiện tại
└── Agent theo engine: agentId, alias, model, effort
    ├── Phiên chính: childId, nativeId, seenSeq, trạng thái
    └── Các nhánh hỏi bên lề: branchId, nativeId, lượt gốc, trạng thái
```

**Trong phạm vi lần này:**
- Mức 1, chuyển lượt: Claude dừng, Codex làm tiếp.
- Mức 2, hỏi ý kiến: `@codex:btw review diff này`, chạy chỉ đọc, trả lời vào timeline.

**Ngoài phạm vi, không tự làm:** agent tự giao việc cho nhau, agent tự trao đổi nhiều lượt, chia một phiên cha để nhiều agent ghi song song trên nhiều worktree, tự động chuyển engine khi hết quota, kho memory riêng.

## 2. Quyết định

Agent thực hiện **không tự đổi** các mục dưới đây. Phạm vi triển khai là repo Agent Remote. Lát cắt đầu tiên mở rộng adapter hiện có và dùng hợp đồng được kiểm chứng ở M0.

| ID | Quyết định | Mặc định | Trạng thái |
|---|---|---|---|
| D1 | Nơi đặt lõi | Agent Remote, trong `server/internal`, sau ranh giới adapter và gateway | Đã chốt |
| D2 | Agent Bridge | Tham chiếu lịch sử cho event log, binding và các lỗi bàn giao đã quan sát | Đã chốt |
| D3 | Dữ liệu cần hỗ trợ | Nhập phiên native của CLI theo T2.9; bảo toàn tương thích với dữ liệu Agent Remote trong thời gian rollout | Đã chốt |
| D4 | Kho dữ liệu | SQLite (`modernc.org/sqlite`) trong thư mục dữ liệu của server | Mặc định |
| D5 | Quyền ghi | Mỗi session cha có tối đa **một writer**; agent khác chỉ đọc | Mặc định |
| D6 | Kiểu tiến trình | Dùng `Engine`/`Session` logic; chọn process sống lâu hay native resume theo khả năng đã kiểm chứng ở T0.5 | Mặc định |
| D7 | Triển khai | Bật bằng feature flag; luồng cũ vẫn chạy cho tới khi T5.6 hoàn tất | Mặc định |
| D9 | Workspace | Mỗi phiên cha gắn một checkout; mọi lần chuyển writer dùng chung checkout. Tạo worktree riêng khi người dùng mở task mới | Đã chốt |
| D8 | Chi phí | Không tự chuyển engine, không tự bơm memory; tóm tắt cuốn chiếu tắt mặc định cho tới khi đo xong T5.3 | Mặc định |

### Định danh và vòng đời

- Mỗi phiên cha có tối đa một agent chính cho mỗi engine trong MVP. Server đảm bảo duy nhất theo `(parentSessionId, engine)`, kể cả khi hai client cùng thêm agent. Alias là đích chọn trong UI; request dùng `agentId`. Đổi model/effort cập nhật cấu hình, giữ nguyên định danh; nếu adapter cần native session mới, ghi lại lần thay thế và dựng lại ngữ cảnh cần thiết.
- Mỗi agent có một phiên chính logic và có thể có nhiều nhánh chỉ đọc. Nhánh có ID riêng, gắn với agent và nguồn đã chọn. Chỉ thao tác **Hỏi tiếp** mới dùng lại ngữ cảnh nhánh. Nhánh không cập nhật `seenSeq` của phiên chính, không thể nhận quyền writer. `:sub` dành cho luồng agent chính giao việc và dùng kết quả trong cùng lượt, nằm ngoài MVP.
- Mỗi phiên cha lưu `writerAgentId` và `writerRevision`. Khi chuyển, server kiểm tra revision và chờ lượt cũ dừng thực sự trước khi cấp quyền mới. Action gửi từ draft có đích agent và revision dự kiến; nếu client khác đã chuyển writer, trả xung đột để UI giữ draft và hiện đích mới. Luồng **Chuyển và gửi** đợi chuyển thành công rồi gửi một lần với khóa idempotency; nếu chỉ gửi thất bại, UI hiện writer mới và cho thử lại thao tác gửi.
- Timeline có thể hiển thị mọi nhánh. Khi dựng ngữ cảnh phiên chính, chỉ lấy các event thuộc công việc chính và các kết quả consult đã được người dùng chuyển tiếp. Quay lại agent cũ gửi phần tiến triển liên quan sau mốc đã thấy; chỉ cập nhật mốc khi lượt xác nhận đã nhận ngữ cảnh theo hợp đồng adapter.

## 3. Quy tắc cho agent thực hiện

1. **Trước mỗi phiên:** đọc `PROJECT_MEMORY.md`, `agent-remote/AGENTS.md`, `.agents/CODEBASE_MAP.md`, `.agents/memory/lessons.md`. Không sửa file của task đang `[IN_PROGRESS]` nếu chưa được yêu cầu.
2. **Repo có thể đang được sửa đồng thời.** Chạy `git status` và đọc lại file ngay trước khi sửa; không ghi đè thay đổi của người khác; làm trên branch riêng theo từng milestone.
3. **Kích thước file:** trên 300 dòng là cảnh báo, trên 450 dòng là thất bại. `ChatViewModel.kt` và `ChatScreen.kt` đều đã trên 400 dòng, gần ngưỡng, logic mới phải ở file khác.
4. **Không dùng `--no-verify`.** Không hardcode secret, IP hay mã phòng. Không log prompt.
5. **Commit, push, release và tạo repo** chỉ khi chủ repo yêu cầu. Chỉ release khi không còn message trong queue.
6. **Task có nhãn `💰` tốn quota thật:** phải hỏi trước.
7. **Xong mỗi task:** chạy test, tick vào file này, ghi một dòng vào `PROJECT_MEMORY.md`. Sửa 3 lần không được thì dừng và báo cáo.
8. **Lệnh Kotlin:** `AGENTS.md` ghi `:composeApp:...`, nhưng wrapper nằm ở `app/gradlew`. Project Kotlin ở root của build trong `app/`. Chạy từ đó với `./gradlew assembleDebug`, `./gradlew desktopTest --tests "*<ClassName>*"`, `./gradlew ktlintCheck detekt` và `./gradlew check`; xác nhận task khả dụng ở T0.3.

Kích thước: **S** dưới nửa ngày, **M** 1 ngày, **L** 2–3 ngày. Đây chỉ là ước lượng.

## 4. Milestone và thứ tự

Mỗi task T có một file riêng. Dùng roadmap này để chọn task theo milestone và [backlog ba engine](#backlog-triển-khai-ba-engine) để tra phạm vi 87 tính năng.

```text
M0 Kiểm chứng → M1 Hợp đồng nội bộ → M2 + M3 lát cắt đầu tiên → M4 App → M5 Đo và phát hành
```

Có thể làm song song: T2.3 với T2.4; T4.5 với T4.7 sau khi có T4.3. T4.6 dùng chung luồng xác nhận gửi dữ liệu với T4.5; T4.8 dùng lại luồng chuyển writer và hợp đồng usage từ T2.11.

---

## M0 — Chuẩn bị

- [x] [T0.1 Đọc bối cảnh (S)](./parent-session-tasks/T0.1.md)
- [x] [T0.2 Chốt phạm vi pilot (S)](./parent-session-tasks/T0.2.md)
- [x] [T0.3 Chạy baseline (S)](./parent-session-tasks/T0.3.md)
- [x] [T0.4 Bắt mẫu output CLI 💰 (M)](./parent-session-tasks/T0.4.md)

- [x] [T0.5 Xác minh harness theo phiên bản (M)](./parent-session-tasks/T0.5.md)

### Ranh giới harness

- CLI sở hữu vòng lặp model–tool, prompt nội bộ, trạng thái native và compaction. Agent Remote sở hữu vòng lặp điều phối: chọn đích, writer lease, duyệt quyền, gửi/hủy lượt, quan sát kết quả và checkpoint bàn giao. Một lượt UI có thể gồm nhiều lần gọi model và tool.
- SQLite giữ bằng chứng có thể quan sát: native ID, trạng thái lượt, workspace, tiến độ, quyết định và nguồn. Resume chỉ dùng trong cùng engine. Khi qua engine khác, dựng gói bàn giao từ bằng chứng; khi quay lại engine cũ, kiểm tra native session còn dùng được trước khi áp dụng delta. Cache hit và mức giữ lại sau compaction là số liệu cần đo.
- Ghi riêng context hiện tại, usage theo lượt và quota tài khoản. Tool output và dữ liệu do harness nạp có thể làm context tăng trong cùng lượt. Cảnh báo “sắp hết mana” chỉ dùng số liệu đủ mới, ghi rõ đang gần đầy context hay gần hết quota; chỉ gợi ý agent đích sau khi kiểm tra checkpoint, quyền và khả năng thực hiện bước tiếp theo. Lưu checkpoint từ sự kiện và workspace trước; lượt model bổ sung là tùy chọn khi còn ngân sách.

---

### Bằng chứng ảnh hưởng đến thứ tự triển khai

| Nguồn đã kiểm tra | Hệ quả cho triển khai |
|---|---|
| Agent Remote `cliRunner` có một active run cho mỗi adapter, prompt mới đánh dấu prompt cũ superseded rồi yêu cầu hủy. Approval legacy chỉ xử lý từ chối bằng hủy; helper process group Windows đang rỗng. | Không dùng `Cancel()`, UI idle hoặc kết thúc stream làm chứng nhận an toàn để cấp writer. Adapter v2 cần phân biệt đã yêu cầu hủy, đã dừng ghi, và chưa biết kết quả. Mở capability theo OS/bản CLI đã test. |
| Antigravity legacy dùng `--dangerously-skip-permissions` ngoài plan mode. Headless có thể soft-deny tool rồi thoát thành công. CLI 1.5.0 cục bộ có stream stdin và ACP trong help. | Chặn đường bypass trên v2. Kiểm thử stream/ACP trên sandbox trước khi chọn. Giữ trạng thái tool bị từ chối riêng với kết quả lượt; chưa bật consult nếu chưa chứng minh biên chỉ đọc, kể cả shell, MCP và hook. [Headless](https://antigravity.google/docs/cli/headless/) |
| Codex CLI 0.153.4 cục bộ công bố App Server stdio và lệnh sinh schema. Hợp đồng phân biệt response của interrupt với event terminal; rate limits được tài liệu mô tả theo tài khoản ChatGPT. | Ưu tiên spike App Server qua stdio cho writer đầu tiên. Pin schema/version, kiểm chứng công cụ con sau interrupt. Quota chưa quan sát được là unknown. [App Server](https://developers.openai.com/codex/app-server/) |
| Bridge `b97b025` có event log và binding khóa theo `(conv, engine)`. `SwitchEngine` ghi settings và event riêng; `record` vẫn publish khi append lỗi. `handoff` đọc tối đa 400 event, và cursor binding cập nhật ở turn done. | Dùng các ý tưởng pool/event log làm tham chiếu. V2 cần commit trước publish, lưu cursor theo gói đã dispatch có bằng chứng và tách binding main/sidechat. Kết thúc lượt không chứng minh agent đã nhận mọi event xuất hiện trong lúc chạy. |
| Working tree app có WIP `ConversationCache` cho lịch sử legacy. | V2 dùng snapshot/seq của server làm nguồn khôi phục. Cache app chỉ phục vụ hiển thị, gắn theo server và phiên cha, không quyết định writer, approval hoặc cursor replay. |

### Lát cắt triển khai đầu tiên

1. **Harness test trước.** Fake engine tái hiện tool ghi chậm, mất tiến trình sau dispatch, approval chờ, snapshot lỗi và event đến muộn. Chạy test race và test trên workspace tạm. Tiếp theo test Codex App Server với bản pin. Tích hợp tiếp Claude và Antigravity theo bằng chứng về hủy, resume và sandbox. Pilot nội bộ mở theo adapter đã đạt; phát hành tính năng bàn giao khi cả ba qua bộ test chung.
2. **Một phiên cha từ đầu đến cuối.** SQLite, một writer, gửi/hủy/khôi phục qua WebSocket với fake engine trước. Dùng handler chung cho WebRTC và chạy parity trước khi quảng bá v2 trên cả hai transport. Giữ app cũ đi đường legacy. Chặn writer v2 và writer legacy cùng chạy trên cùng checkout do server quản lý, kể cả hai phiên cha khác nhau; nhận diện checkout theo worktree đã chuẩn hóa. Thay đổi từ CLI ngoài server được báo là external drift.
3. **Handoff hai chiều trước sidechat.** Tạo checkpoint có nguồn, kiểm tra workspace sau khi writer cũ dừng, chuyển writer bằng CAS, gửi tiếp trên native session đích khi resume được. Test A → B → A cùng task, gồm quyết định chỉ có trong chat và file chưa commit. Tiếp theo mới thêm `:btw` với nhánh và sandbox riêng.
4. **Gợi ý “hết mana” sau khi có telemetry và benchmark.** Tách gần đầy context khỏi quota sắp hết. Lọc ứng viên theo bước còn lại, workspace, quyền và khả năng adapter; xếp hạng theo phiên có thể resume, dữ liệu quota còn mới và chi phí nạp lại. Khi thiếu checkpoint hoặc quota, hiện lý do và tùy chọn xem bàn giao. Người dùng xác nhận chuyển; checkpoint vẫn dùng được nếu provider cắt lượt đột ngột.

### Hợp đồng bàn giao và phục hồi

- **Quyền chọn writer và quyền chạy tool ghi là hai trạng thái.** Server đặt chỗ theo checkout và revision trong transaction trước dispatch. Chỉ giải phóng quyền thực thi sau bằng chứng dừng. Crash hoặc không rõ tool con còn chạy thì chuyển sang **Cần xác minh**, giữ chặn ghi đến khi reconcile. Thời gian chờ hết chỉ kích hoạt reconcile.
- **SQLite bảo vệ dữ liệu do server kiểm soát.** Lưu operation ID, revision, trạng thái và event trong cùng transaction; publish sau commit. Retry cùng ID trả về operation đã có, khóa trùng với payload khác trả lỗi. Khi CLI có thể đã nhận prompt nhưng server chưa ghi nhận kết quả, reconcile qua native turn/session nếu có; nếu vẫn mơ hồ, giữ draft và yêu cầu người dùng quyết định. Không tự phát lại thao tác có thể đã sửa file.
- **Checkpoint phiên bản hóa.** Lưu mục tiêu, quyết định, việc còn lại, mốc HEAD/worktree, nguồn event, test và trạng thái kiểm chứng. Render Markdown theo yêu cầu. Với mỗi lần gửi gói context, lưu ID, phạm vi event và dấu vết nguồn thực tế đã đưa vào. Nếu resume bị mất hoặc workspace đổi, dựng lại từ checkpoint và kiểm tra bằng chứng còn hiệu lực; đối chiếu trước thao tác ghi đầu tiên.
- **Đo chất lượng theo từng chuyển tiếp.** So baseline một engine, resume cùng engine, handoff qua engine khác và quay lại engine cũ. Chấm bước đầu có đúng không, có vi phạm ràng buộc không, token input/output/cache, thời gian và lượt phải giải thích lại. Với consult, chấm thêm độ đúng của dẫn chứng và tỷ lệ cần đính kèm thêm nguồn. Báo riêng từng adapter/chiều chuyển. Dùng ngưỡng 8/10 hiện có như cổng pilot; bất kỳ ca ghi song song, gửi trùng hoặc mất ràng buộc quan trọng nào đều chặn mở rộng.

## M1 — Hợp đồng nội bộ Agent Remote

- [x] [T1.1 Định nghĩa hợp đồng lượt (S)](./parent-session-tasks/T1.1.md)
- [x] [T1.2 Dựng fake engine (M)](./parent-session-tasks/T1.2.md)
- [x] [T1.3 Tích hợp adapter đầu tiên (M)](./parent-session-tasks/T1.3.md)

---

## M2 — Session cha trong lõi

- [x] [T2.1 Mô hình dữ liệu (M)](./parent-session-tasks/T2.1.md)
- [x] [T2.2 Quyền ghi (M)](./parent-session-tasks/T2.2.md)
- [x] [T2.3 Dựng ngữ cảnh theo mục đích (L)](./parent-session-tasks/T2.3.md)
- [x] [T2.4 Đọc trạng thái git (M)](./parent-session-tasks/T2.4.md)
- [x] [T2.4a Định danh checkout và quyền thực thi (M)](./parent-session-tasks/T2.4a.md)
- [x] [T2.5 Chuyển lượt (M)](./parent-session-tasks/T2.5.md)
- [x] [T2.6 Hỏi ý kiến (L)](./parent-session-tasks/T2.6.md)
- [x] [T2.7 Giới hạn và hủy (S)](./parent-session-tasks/T2.7.md)
- [x] [T2.8 Duyệt quyền (M)](./parent-session-tasks/T2.8.md)
- [x] [T2.9 Nhập phiên CLI đã có (M)](./parent-session-tasks/T2.9.md)
- [x] [T2.10 Phát hiện lệch (S)](./parent-session-tasks/T2.10.md)
- [x] [T2.11 Usage và quota (M)](./parent-session-tasks/T2.11.md)
- [x] [T2.12 Tóm tắt cuốn chiếu (S)](./parent-session-tasks/T2.12.md)

---

## M3 — Server Agent Remote

- [x] [T3.1 Nối lõi nội bộ vào server (S)](./parent-session-tasks/T3.1.md)
- [x] [T3.2 Gom xử lý action (M)](./parent-session-tasks/T3.2.md)
- [x] [T3.3 Protocol session (M)](./parent-session-tasks/T3.3.md)
- [x] [T3.4 Khởi tạo lõi (M)](./parent-session-tasks/T3.4.md)
- [x] [T3.5 Phát event và nối lại (L)](./parent-session-tasks/T3.5.md)
- [x] [T3.6 Bảo mật (M)](./parent-session-tasks/T3.6.md)
- [x] [T3.6a Tạo và phục hồi worktree (M)](./parent-session-tasks/T3.6a.md)
- [x] [T3.6b Dọn worktree (M, sau pilot)](./parent-session-tasks/T3.6b.md)
- [x] [T3.7 Test tích hợp (M)](./parent-session-tasks/T3.7.md)

---

## M4 — App

`@agent` tiếp tục công việc chính với agent đích. `@agent:btw` hỏi bên lề trong nhánh chỉ đọc. Cả hai cú pháp dùng ở mọi vị trí, hiển thị đích và hành động trước khi gửi.

Thứ tự Domain → Data → State → UI. Mọi thứ nằm sau kiểm tra `features`; server cũ vẫn dùng luồng cũ.

- [x] [T4.1 Domain (S)](./parent-session-tasks/T4.1.md)
- [x] [T4.2 Data (M)](./parent-session-tasks/T4.2.md)
- [x] [T4.3 State (L)](./parent-session-tasks/T4.3.md)
- [x] [T4.4 Timeline (M)](./parent-session-tasks/T4.4.md)
- [x] [T4.5 Writer và model (M)](./parent-session-tasks/T4.5.md)
- [x] [T4.6 Chọn agent bằng mention (M)](./parent-session-tasks/T4.6.md)
- [x] [T4.7 Drawer và khôi phục (M)](./parent-session-tasks/T4.7.md)
- [x] [T4.7a Chọn checkout cho phiên mới (M)](./parent-session-tasks/T4.7a.md)
- [x] [T4.8 Duyệt quyền và quota (M)](./parent-session-tasks/T4.8.md)
- [x] [T4.9 Tương thích (S)](./parent-session-tasks/T4.9.md)
- [x] [T4.10 Kiểm tra trên thiết bị (M)](./parent-session-tasks/T4.10.md)

---

## M4b — Tính năng dùng chung, lấy cảm hứng từ từng engine

Dùng [inventory ba engine](./reference/ENGINE_FEATURE_INVENTORY.md) và [ma trận đã gộp trùng](./reference/ENGINE_FEATURE_MATRIX.md) để rà phạm vi. Ma trận có 94 mục đối chiếu và 87 mục Agent Remote đề xuất hỗ trợ trong dài hạn (82 dùng chung, 5 native có điều kiện). Dùng [backlog đủ ba engine](#backlog-triển-khai-ba-engine) để giao task và nghiệm thu đủ 87 ID. M4b và T4b.7 mô tả hành vi chung, còn inventory giữ tính năng nguồn và các nhóm chưa đưa vào milestone.

Agent Remote sở hữu action, dữ liệu và UX của mỗi tính năng. Mỗi action có đường thực hiện chung trên các engine đạt hợp đồng lõi, cùng đường native tùy chọn khi giữ được hành vi đã công bố. Ví dụ, một mục tiêu tạo khi dùng Codex vẫn xuất hiện khi chuyển sang Claude hoặc agy. Adapter có thể bổ sung chất lượng ngữ cảnh hay telemetry mà native harness cung cấp.

- [x] [T4b.1 Hợp đồng tính năng (M)](./parent-session-tasks/T4b.1.md)
- [x] [T4b.2 Ghim, trích dẫn và context (M)](./parent-session-tasks/T4b.2.md)
- [x] [T4b.3 Fork và checkpoint (L)](./parent-session-tasks/T4b.3.md)
- [x] [T4b.4 Plan, review và artifacts (L)](./parent-session-tasks/T4b.4.md)
- [x] [T4b.5 Goals và điều khiển lượt (L, sau pilot)](./parent-session-tasks/T4b.5.md)
- [x] [T4b.6 Mở rộng theo giá trị (M)](./parent-session-tasks/T4b.6.md)

Nguồn tham khảo: [Claude Code SDK](https://code.claude.com/docs/en/agent-sdk/typescript), [Claude checkpointing](https://code.claude.com/docs/en/checkpointing), [Codex App Server](https://developers.openai.com/codex/app-server/), [Antigravity CLI](https://antigravity.google/docs/cli/reference/).

- [x] [T4b.7 Backlog đối chiếu với sản phẩm hiện tại](./parent-session-tasks/T4b.7.md)

## M5 — Kiểm chứng và phát hành

- [x] [T5.1 Toàn bộ test (S)](./parent-session-tasks/T5.1.md)
- [x] [T5.2 Thử thật 💰 (M)](./parent-session-tasks/T5.2.md)
- [x] [T5.3 Đo 💰 (M)](./parent-session-tasks/T5.3.md)
- [x] [T5.4 Tài liệu (S)](./parent-session-tasks/T5.4.md)
- [x] [T5.5 Phát hành có kiểm soát (S)](./parent-session-tasks/T5.5.md)
- [x] [T5.6 Bỏ luồng cũ (M)](./parent-session-tasks/T5.6.md)

---

## Backlog triển khai ba engine

Phạm vi theo [ma trận tính năng](./reference/ENGINE_FEATURE_MATRIX.md). Claude Code, Codex và Antigravity đều là mục tiêu phát hành. Thứ tự tích hợp dùng để giảm rủi ro khi chẩn đoán. Pilot có thể chạy nội bộ theo từng adapter; cổng phát hành yêu cầu cả ba.

### A — Adapter và bộ kiểm thử chung

- [x] [A01 — Hợp đồng adapter và fake engine](./engine-feature-tasks/A01.md)
- [x] [A02 — Adapter Codex](./engine-feature-tasks/A02.md)
- [x] [A03 — Adapter Claude Code](./engine-feature-tasks/A03.md)
- [x] [A04 — Adapter Antigravity](./engine-feature-tasks/A04.md)
- [x] [A05 — Kiểm thử tích hợp ba engine](./engine-feature-tasks/A05.md)

### B — 87 tính năng theo thứ tự triển khai

- [x] [B01 — Phiên và lượt chạy](./engine-feature-tasks/B01.md) (F01–F02, F14–F15, F44, F91)
- [x] [B02 — Workspace chung](./engine-feature-tasks/B02.md) (F40–F42, F51, F87)
- [x] [B03 — Bàn giao ba engine](./engine-feature-tasks/B03.md) (F28)
- [x] [B04 — Điều khiển từ xa](./engine-feature-tasks/B04.md) (F03–F05, F11, F77, F88)
- [x] [B05 — Model và hành vi trả lời](./engine-feature-tasks/B05.md) (F06–F10, F86)
- [x] [B06 — Nguồn đính kèm](./engine-feature-tasks/B06.md) (F12, F33–F35, F38–F39, F47, F49)
- [x] [B07 — Sidechat chỉ đọc](./engine-feature-tasks/B07.md) (F27)
- [x] [B08 — Usage, compact và chẩn đoán](./engine-feature-tasks/B08.md) (F29–F32, F93–F94)
- [x] [B09 — Tổ chức phiên](./engine-feature-tasks/B09.md) (F16–F22)
- [x] [B10 — Fork, rewind và phục hồi](./engine-feature-tasks/B10.md) (F23–F26)
- [x] [B11 — Plan, review và bằng chứng](./engine-feature-tasks/B11.md) (F52–F56, F59–F60, F75, F92)
- [x] [B12 — Dịch vụ host cho repo](./engine-feature-tasks/B12.md) (F43, F46, F50, F58, F78)
- [x] [B13 — Cấu hình và học lại được](./engine-feature-tasks/B13.md) (F36–F37, F79–F81, F83–F85)
- [x] [B14 — Goals, jobs và lịch](./engine-feature-tasks/B14.md) (F68–F69, F71, F76)
- [x] [B15 — Subagent, nhắn trực tiếp, team và workflow](./engine-feature-tasks/B15.md) (F70, F72–F74)
- [x] [B16 — Kiểm chứng trực quan và voice](./engine-feature-tasks/B16.md) (F13, F62–F67)

### C — Cổng phát hành

- [x] [C01 — Parity ba engine](./engine-feature-tasks/C01.md)
- [x] [C02 — Pilot](./engine-feature-tasks/C02.md)
- [x] [C03 — Kiểm tra hồi quy](./engine-feature-tasks/C03.md)
- [x] [C04 — So phạm vi](./engine-feature-tasks/C04.md)

## 5. Tiêu chí hoàn thành chung

- [x] Một session cha chứa được cả ba agent, và người dùng thấy một timeline duy nhất.
- [x] Chuyển lượt không cần giải thích lại; quay lại agent cũ dùng đúng session con và chỉ nhận phần chưa thấy.
- [x] `@agent` định tuyến đúng phiên chính; `@agent:btw` chạy chỉ đọc và chỉ truyền kết quả khi người dùng chọn.
- [x] Các lượt do Agent Remote quản lý giữ tối đa một quyền thực thi ghi trên mỗi checkout, kể cả qua hai phiên cha hoặc hai đường dẫn khác nhau. Worktree mới bắt đầu từ commit đã chọn và tách thay đổi đang dở ở checkout nguồn.
- [x] Output của agent không thể tự ra lệnh hay nâng quyền cho agent khác.
- [x] Ngắt kết nối không làm mất event hay yêu cầu duyệt.
- [x] Server cũ, app cũ và luồng cũ vẫn hoạt động trong thời gian chuyển đổi.

## 6. Rủi ro cần báo ngay nếu gặp

| Tình huống | Cách xử lý |
|---|---|
| CLI không cho chạy tiến trình sống lâu hoặc chỉ đọc thật | Dừng tính năng cho engine đó, ghi vào `Capabilities`; không giả lập |
| Module lõi kéo theo dependency của UI hoặc HTTP | Dừng và tách lại ranh giới, không chấp nhận tạm |
| Token lượt đầu vượt mức dự kiến | Giảm ngân sách ở T2.3, báo số liệu |
| Sửa cùng file với thay đổi đang diễn ra | Dừng phần đó, báo; không ghi đè |

## 7. Lời nhắc giao việc

```text
Implement docs/ROADMAP.md in agent-remote, one task at a time, following the execution order and first vertical slice in M0.
Read section 3 first. Implement inside Agent Remote and use the M0 evidence to select the pilot adapter.
Keep scope to the listed task. Ask before tasks marked 💰, and before any commit, push, release, or repo creation.
After each task, run its tests, tick it, add one line to PROJECT_MEMORY.md, and report results and anything unverified.
```
