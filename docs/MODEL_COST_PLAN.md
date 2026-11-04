# Kế hoạch tối ưu chi phí model cho Agent Remote

> **Trạng thái:** đề xuất, chưa triển khai. Khảo sát ngày 2026-11-04 trên commit `098cad7` cùng các thay đổi chưa commit, nên số dòng có thể lệch.

## 1. Kết luận

| # | Đề xuất ban đầu | Đánh giá | Quyết định |
|---|---|---|---|
| 1 | Không đổi model giữa chừng | Đúng, nhưng chưa đủ: nghỉ quá lâu cũng tốn tương tự, và app đang có thể **đổi model ngầm** khi resume | Làm sau khi có usage |
| 2 | Thêm `Effort` | Đúng và ít việc: cả ba CLI đều có cách đặt effort | Làm, nhưng không đổi mặc định khi chưa đo |
| 3 | Ghi usage theo task | Đúng và là điều kiện của mọi mục khác; hiện chỉ Antigravity có dữ liệu token | **Làm đầu tiên** |
| 4 | Worker rẻ → Judge → Worker mạnh | Khả thi nhưng tốn công và rủi ro nhất; bằng chứng còn trái chiều | **Chưa làm**, chỉ mở khi đạt cổng ở mục 7 |

**Thứ tự:** Tiền đề → Usage → Giữ cache → Effort → Xét cổng pipeline.

## 2. Bằng chứng đã đo

### 2.1. Cache trên Claude Code của máy này

Nguồn: transcript trong `~/.claude/projects`, chỉ luồng chính, gom theo `message.id` trong từng file. Mỗi dòng so một request với request liền trước.

| Tình huống | Số request | Cache ghi trung bình | Cache hit |
|---|---:|---:|---:|
| Cùng model, cách dưới 5 phút | 10.304 | 1.615 | 99,7% |
| Cùng model, cách 5 phút – 1 giờ | 224 | 7.134 | 98,5% |
| Cùng model, cách trên 1 giờ | 58 | 384.923 | 8,4% |
| **Đổi model** | 18 | 519.521 | 5,9% |

- 76 request đổi model hoặc nghỉ trên 1 giờ (khoảng 0,7%) tạo khoảng **63% lượng cache ghi**.
- Ước tính theo giá API Opus 5.5, giả định ghi cache $5 và đọc cache $0,20 cho mỗi 1 triệu token: một lần đổi model tốn khoảng **$2,6**, so với khoảng $0,11 nếu đọc được từ cache.
- Bản Claude Code đang dùng chọn TTL 1 giờ cho thuê bao, phù hợp với số đo trên. Tài khoản API và engine khác có thể khác.

**Giới hạn:** mẫu đổi model chỉ có 18 lần; có lần đổi model trùng với lúc nghỉ dài; đây là phiên Claude Code nói chung, chưa phải phiên chạy qua Agent Remote. Codex và Antigravity chưa được đo theo cách này.

### 2.2. Hiện trạng code

| Chủ đề | Hiện trạng | Vị trí |
|---|---|---|
| Effort | Không có trong protocol, `PromptOptions` và `sendPrompt` | `server/internal/protocol/types.go`, `server/internal/adapter/adapter.go`, `app/.../core/data/dto/GatewayActionDto.kt` |
| Danh sách effort | Chỉ catalog Codex điền `Efforts` | `server/internal/catalog/codex.go:76` |
| Usage Claude/Codex | Adapter không đọc usage; lịch sử chỉ trả `Messages` | `adapter/claude.go:42`, `adapter/codex.go:117`, `catalog/history.go:50-55` |
| Usage Antigravity | Đọc từ transcript, lấy số của lượt cuối chứ không cộng dồn | `catalog/history.go:80-123` |
| Giới hạn ngữ cảnh | Gán cứng Claude 200k, GPT 128k, còn lại 1M; sai với model Claude 1M | `catalog/history.go:207-219` |
| Tổng token | `TotalTokens` bỏ qua cache ghi | `catalog/history.go:220` |
| Đổi model | Đổi ngay, không cảnh báo | `ChatViewModel.kt:311` |
| **Đổi model ngầm** | Claude/Codex không trả `ActiveModel`, app giữ model đang chọn và server gửi `--model` khi resume | `catalog/history.go:50-55`, `ChatViewModel.kt:185` |

### 2.3. Khả năng của CLI

| Việc | Claude Code 2.1.317 | Codex 0.153.4 | Antigravity 1.5.0 |
|---|---|---|---|
| Đặt effort | `--effort` | `-c model_reasoning_effort=...` | `--effort`, và id model cũng có hậu tố effort |
| Output có cấu trúc | `--json-schema` | `--output-schema` | `--json-schema` |
| Trần chi phí một lần chạy | `--max-budget-usd` | Chưa thấy cờ tương ứng | Chưa thấy cờ tương ứng |
| Usage khi stream | Binary có `total_cost_usd`, `modelUsage`; **chưa bắt mẫu thật** | Mã nguồn có `Usage` trong sự kiện kết thúc lượt | Chưa xác nhận, đang đọc từ transcript |

## 3. Tiền đề

- [ ] **T0.1 — Chốt thay đổi đang dở.** Đang có thay đổi chưa commit ở `gateway/server.go`, `protocol/events.go`, `catalog/history.go`, `ChatViewModel.kt` và `GatewayEventDto.kt`, trùng với vùng sẽ sửa. Chủ sở hữu commit hoặc tách trước; không ghi đè.
- [ ] **T0.2 — Bắt mẫu output thật.** Với mỗi CLI, chạy một prompt ngắn rồi lưu dòng kết thúc vào fixture test, đã xóa nội dung nhạy cảm. Làm việc này trước khi viết parser.
- [ ] **T0.3 — Chọn branch riêng** cho từng giai đoạn; mỗi file giữ dưới 300 LOC theo quy tắc dự án. `ChatViewModel.kt` đã 450 LOC, nên logic mới phải nằm ngoài file này.

## 4. Giai đoạn 1 — Usage theo task

**Mục tiêu:** mỗi prompt có một bản ghi usage, và app xem được cho cả ba engine.

- [ ] **U1. Kiểu dữ liệu chung** trong `server/internal/usage/`:

  ```go
  type Record struct {
      TaskID, Engine, Model, Effort, ConversationID string
      InputTokens, CacheReadTokens, CacheWriteTokens int
      OutputTokens, ReasoningTokens int
      DurationMs int64
      CostUSD *float64 // chỉ khi CLI báo; nil nếu không có
      Outcome string   // completed | failed | cancelled
  }
  ```

- [ ] **U2. Parser theo engine:** thêm `onUsage` vào `cliRun`; Claude đọc sự kiện `result`, Codex đọc `turn.completed`, Antigravity đọc stream nếu T0.2 xác nhận có, không thì đọc transcript sau khi xong. Không có số thì để trống, không suy đoán.
- [ ] **U3. Lưu cục bộ:** append JSONL vào thư mục dữ liệu của server, kèm giới hạn kích thước và xoay file. Không lưu nội dung prompt.
- [ ] **U4. Gửi về app:** thêm usage tùy chọn vào `MessageEnd`. Trước đó kiểm tra app cũ vẫn chạy được với trường mới.
- [ ] **U5. Sửa số hiển thị:** thêm `CacheWriteTokens`; lấy giới hạn ngữ cảnh từ model thay vì đoán theo tên; trả usage cho lịch sử Claude/Codex; ghi rõ số là của lượt cuối hay cộng dồn.
- [ ] **U6. Lệnh tổng hợp** theo engine/model/effort/ngày, trả về tỷ lệ cache hit, tổng token và chi phí nếu có.

**Hoàn thành khi:**
- Có test bằng fixture thật cho cả ba engine, gồm trường hợp lỗi và hủy.
- Chạy thật mỗi engine một prompt, bản ghi khớp với log của CLI.
- Không có bảng giá gán cứng; số đô chỉ lấy từ CLI và luôn ghi là ước tính.

## 5. Giai đoạn 2 — Giữ cache

**Mục tiêu:** không tạo lại cache ngoài ý muốn, và người dùng biết trước khi một thao tác tốn kém.

- [ ] **C1. Bỏ đổi model ngầm (ưu tiên cao nhất).** Đọc model đã dùng từ transcript Claude/Codex rồi trả `ActiveModel`. Khi mở lại hội thoại, app chọn đúng model đó; nếu không đọc được thì không gửi `--model`, để CLI tự giữ. Thêm test: resume không chọn lại model thì args không đổi model.
- [ ] **C2. Cảnh báo khi đổi model giữa hội thoại.** Khi hội thoại có ngữ cảnh đáng kể, hiện hộp thoại nêu số token sẽ nạp lại, với hai lựa chọn "Đổi" và "Giữ". Hội thoại mới không cảnh báo. Đặt logic ngoài `ChatViewModel.kt`.
- [ ] **C3. Gợi ý khi cache có thể đã hết hạn.** Nếu nghỉ lâu hơn TTL của engine, hiện gợi ý nhẹ trước khi gửi, không chặn. TTL phải cấu hình được theo engine; số 1 giờ hiện chỉ đúng với số đo Claude trên máy này.
- [ ] **C4. Đo phiên qua Agent Remote.** Mỗi prompt là một tiến trình `--resume`, cần xác nhận cache hit tương đương phiên liền mạch, và đổi `--permission-mode` hay MCP có làm mất cache không. Nếu có, xử lý trước C2.
- [ ] **C5. Thử nén trước khi resume nguội.** Kiểm tra từng CLI có nén được ở chế độ không tương tác không. Chỉ thêm nút nếu giảm token rõ rệt ở lượt kế tiếp.

**Hoàn thành khi:**
- Mở lại hội thoại cũ không còn đổi model ngoài ý muốn.
- Có số đo trước/sau cho tỷ lệ cache hit và số token cache ghi mỗi lượt.
- Cảnh báo chỉ xuất hiện khi thật sự có ngữ cảnh đáng kể và không chặn việc gửi.

## 6. Giai đoạn 3 — Effort

**Mục tiêu:** chọn được effort theo từng prompt; mặc định vẫn là của CLI cho tới khi có số đo.

- [ ] **E1. Protocol:** thêm `Effort` vào `ClientMessage` và `PromptOptions`; `validateOptions` chỉ nhận `low|medium|high|xhigh|max`; rỗng thì không truyền cờ.
- [ ] **E2. Adapter:** Claude và Antigravity dùng `--effort`; Codex dùng `-c model_reasoning_effort=...`. Có test args cho cả hội thoại mới lẫn `resume`, vì vị trí cờ của Codex có thể khác.
- [ ] **E3. Catalog:** điền `Efforts` cho Claude và Antigravity. Thử xem cờ `--effort` hay hậu tố trong id model của Antigravity được ưu tiên, rồi chọn một cách duy nhất cho UI.
- [ ] **E4. App:** thêm bộ chọn bên cạnh model, chỉ hiện mức model hỗ trợ, lưu theo engine và model; truyền qua `sendPrompt`.
- [ ] **E5. Đo tác động lên cache.** Tài liệu API cho biết đổi effort ở cấp request có thể làm mất cache phần messages; chưa rõ CLI có xử lý riêng không. Nếu mất cache thật, áp dụng cảnh báo như C2.
- [ ] **E6. So sánh.** Với mỗi engine chính, chạy 10–20 task thật ở mức mặc định và một mức thấp hơn. Ghi token, thời gian, trạng thái hoàn thành, số lượt sửa thêm và kết quả test.

**Tiêu chí đổi mặc định:** giảm ít nhất 20% token hoặc chi phí trên mỗi task hoàn thành, tỷ lệ hoàn thành không giảm, và số lượt sửa thêm không tăng. Không đạt thì giữ mặc định.

## 7. Giai đoạn 4 — Pipeline tự chuyển cấp (có cổng)

### 7.1. Đánh giá

| Lợi | Hại |
|---|---|
| Chỉ tốn model mạnh khi cần | Hai lần chạy cùng làm trên một workspace, cần cách ly |
| Lệnh test có thể thay một phần Judge | Task không có test thì Judge khó đáng tin |
| Hợp với việc lớn, chia được | Task nhỏ hoặc nhiều bước phụ thuộc có thể chậm và tốn hơn |
| Cursor báo giảm mạnh chi phí ở task rất lớn | Benchmark của Akita thấy cặp model không hơn một model mạnh ở app nhỏ |

Antigravity có mã `model_router` theo mô hình này, nhưng **chưa có bằng chứng đang được bật hay hiệu quả**, nên chỉ dùng làm tham khảo thiết kế.

### 7.2. Điểm hòa vốn

Với `C` là chi phí, pipeline có lợi khi tỷ lệ phải chuyển cấp `p` nhỏ hơn:

```text
p* = 1 − (C_rẻ + C_judge) / C_mạnh
```

Ví dụ minh họa, Sonnet 5.5 so với Opus 5.5 với một task có 900k cache read, 70k input và 30k output ($0,62 so với $1,06):

| Chi phí Judge | Hòa vốn khi tỷ lệ chuyển cấp dưới |
|---:|---:|
| $0,05 | 37% |
| $0,10 | 32% |
| $0,20 | 23% |

Công thức này giả định lần chạy mạnh tốn như khi chạy một mình. Nếu Worker rẻ dùng nhiều token hơn, hoặc phải tạo cache mới khi chuyển cấp, ngưỡng thực tế còn thấp hơn.

### 7.3. Cổng mở

Chỉ làm khi **đạt đủ**:

1. Giai đoạn 1–3 đã xong và có ít nhất 2 tuần dữ liệu usage.
2. Có áp lực thật: dùng API key, hoặc thường xuyên chạm giới hạn gói. Nếu chỉ dùng thuê bao và chưa chạm giới hạn thì dừng ở đây.
3. Task chạy dài có lệnh kiểm tra tự động chiếm tỷ trọng đáng kể.
4. Thử nghiệm nhỏ ở 7.5 cho `p` thấp hơn `p*` rõ rệt.

### 7.4. Thiết kế tối thiểu

```text
Task + lệnh kiểm tra → Worker rẻ (worktree A) → Lệnh kiểm tra
  đạt     → người dùng xem diff
  không đạt → Judge chỉ đọc → lỗi môi trường: dừng và báo
                            → cần chuyển cấp: Worker mạnh (worktree B)
```

- **Bật riêng từng task,** không phải mặc định. Yêu cầu workspace là git repo sạch.
- **Tách worktree theo từng lần thử.** Không tự merge; luôn để người dùng xem diff.
- **Kiểm tra trước, Judge sau.** Judge chỉ đọc, xuất JSON theo schema, nhận task, diff, output test và log tool đã giới hạn. Thiếu bằng chứng thì chọn chuyển cấp.
- **Giữ nguyên văn dữ liệu chuyển giữa các bước,** để tránh mất thông tin do tóm tắt.
- **Giới hạn:** tối đa một lần chuyển cấp; đặt `--max-budget-usd` khi CLI hỗ trợ; không dùng `full-access`.
- **Code:** thêm `adapter/pipeline.go` dưới 300 LOC, bọc các adapter sẵn có và `Emitter`. Cần chạy tuần tự có kiểm soát vì `cliRunner.begin` sẽ thay thế lượt đang chạy. Hủy phải dừng mọi tiến trình con.
- **App:** thêm sự kiện giai đoạn (`worker`, `verify`, `judge`, `escalate`) và màn hình xem diff; yêu cầu duyệt quyền phải ghi rõ đến từ bước nào.
- **Xem xét trước khi tự viết:** dành tối đa 1 ngày thử `Enderfga/claw-orchestrator` bằng repo test. Nếu đủ dùng thì adapter chỉ cần gọi sang nó, nhưng phải chấp nhận phụ thuộc Node 22.

### 7.5. Thử nghiệm trước khi đưa vào app

So sánh ít nhất 20 task thật có lệnh kiểm tra, chạy bằng script ngoài app:

| Nhóm | Cấu hình |
|---|---|
| A | Một model mạnh, effort mặc định |
| B | Một model mạnh, effort thấp hơn |
| C | Pipeline |

Đo chi phí và thời gian trên mỗi task hoàn thành, tỷ lệ chuyển cấp, lần test đạt nhưng người xem vẫn từ chối. **Chỉ làm tiếp nếu C rẻ hơn cả A và B ít nhất 25% mà chất lượng không giảm.** Nếu B đã đạt mục tiêu thì dừng, không cần pipeline.

## 8. Lộ trình và rủi ro

Thời lượng là ước lượng cho một người, chưa tính thời gian chờ đo.

| Giai đoạn | Ước lượng | Kết quả |
|---|---:|---|
| Tiền đề | 0,5 ngày | Cây làm việc sạch, có fixture thật |
| 1. Usage | 2–3 ngày | Dữ liệu cho cả ba engine |
| 2. Giữ cache | 2–3 ngày | Hết đổi model ngầm, có cảnh báo |
| 3. Effort | 2 ngày, thêm thời gian so sánh | Chọn được effort, có số đo |
| Thử nghiệm pipeline | 2–3 ngày, nếu qua cổng | Quyết định làm hay dừng |
| 4. Pipeline | 1–2 tuần, nếu đạt | Chế độ bật riêng cho từng task |

| Rủi ro | Cách giảm |
|---|---|
| CLI đổi định dạng output hoặc cờ | Fixture theo phiên bản; thiếu usage thì bỏ qua, không làm hỏng lượt chat |
| Con số bị hiểu là hóa đơn thật, nhất là với thuê bao | Ghi rõ "ước tính"; ưu tiên hiển thị token |
| Cảnh báo gây phiền | Chỉ hiện khi vượt ngưỡng; không chặn gửi |
| Effort thấp làm giảm chất lượng | Không đổi mặc định khi chưa đạt E6 |
| Mẫu đo nhỏ dẫn tới kết luận sai | Báo kèm số mẫu; đo lại sau 2 tuần dữ liệu usage |

## 9. Cần quyết định

1. Đang dùng các CLI theo **gói thuê bao hay API key**, và có hay chạm giới hạn không? Câu trả lời quyết định cổng số 2 của pipeline.
2. **Ai chốt các thay đổi chưa commit** ở T0.1?
3. Có đồng ý dùng **quota thật** cho so sánh E6 và thử nghiệm 7.5 không?

## 10. Nguồn

- [Claude Code – Model configuration](https://code.claude.com/docs/en/model-config)
- [openai/codex](https://github.com/openai/codex)
- [Enderfga/claw-orchestrator](https://github.com/Enderfga/claw-orchestrator)
- [Cursor's agent swarm – The Decoder](https://the-decoder.com/cursors-agent-swarm-suggests-cheaper-models-can-handle-most-coding-when-frontier-models-plan-the-work/)
- [LLM Benchmarks: mixing 2 models – Akita on Rails](https://akitaonrails.com/en/2026/04/25/llm-benchmarks-vale-a-pena-misturar-2-modelos/)
- [How cheap models changed multi-agent economics – Arize](https://arize.com/blog/how-cheap-models-changed-multi-agent-economics/)
