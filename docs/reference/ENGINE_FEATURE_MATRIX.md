# Ma trận tính năng và phạm vi Agent Remote

Đối chiếu 2026-11-04 từ [inventory và nguồn chính thức](./ENGINE_FEATURE_INVENTORY.md). Đơn vị đếm là một hành vi người dùng hoặc một khả năng vận hành có thể nghiệm thu riêng. Alias, lệnh và RPC của cùng hành vi dùng chung một dòng. Ví dụ fork native theo turn và fork theo message cùng thuộc tính năng tạo nhánh, còn fork tạm trong RAM có vòng đời riêng.

**Phân loại.** Giống = cùng hành vi cốt lõi ở cả ba engine. Gần giống = có ít nhất hai engine hỗ trợ, với khác biệt về phạm vi, ngữ nghĩa hoặc bề mặt tích hợp. Khác = chỉ có một nguồn xác nhận trong inventory hiện tại. Chờ xác minh = chưa đủ bằng chứng để so khớp. Agent Remote bổ sung = yêu cầu riêng của sản phẩm.

**Cách đọc nguồn.** Ô 01 của cột Claude trỏ đến CL-01, tương tự CX và AG. Dấu — nghĩa là chưa ghi nhận trong nguồn đã rà. Dấu ? nghĩa là tính năng hoặc ngữ nghĩa cần xác minh; ví dụ khả năng phục hồi file của Antigravity cần kiểm tra riêng với rewind lịch sử. Mỗi cột dùng mã nguồn theo nhóm, chưa xác nhận từng bản CLI.

## Tổng số

| Phân loại | Agent Remote sở hữu | Host dùng chung | Native có điều kiện | Ngoài phạm vi | Tổng |
|---|---:|---:|---:|---:|---:|
| Giống | 6 | 0 | 0 | 0 | 6 |
| Gần giống | 43 | 15 | 2 | 6 | 66 |
| Khác | 10 | 4 | 3 | 1 | 18 |
| Chờ xác minh | 2 | 0 | 0 | 0 | 2 |
| Agent Remote bổ sung | 2 | 0 | 0 | 0 | 2 |
| **Tổng** | 63 | 19 | 5 | 7 | 94 |

**Phạm vi đề xuất gồm 87 tính năng**. Trong đó 82 dùng chung (63 do Agent Remote sở hữu, 19 do host cung cấp) và 5 phụ thuộc native. 7 mục nằm ngoài phạm vi đề xuất. Hai mục ghim/giữ nguồn được tính như hai hành vi Agent Remote có thể xây; quan hệ với Antigravity Stick message còn chờ xác minh. Tổng này là phạm vi sản phẩm dài hạn, bao gồm cả phần đã có và cần nâng cấp. MVP vẫn theo thứ tự trong plan phiên cha.

**Theo cột nguồn**, Claude có 75 mục ghi nhận, Codex có 67, Antigravity có 74 và 3 ô cần xác minh. Các số này chồng lấp; tổng phạm vi tính theo hàng sau khi gộp. Ba ô Antigravity gồm hai hành vi quanh Stick message và một đối chiếu phục hồi file. Phục hồi file vẫn được tính trong phạm vi vì Claude có bằng chứng cho hành vi này.

## Ma trận chi tiết

| ID | Tính năng | Claude | Codex | Antigravity | Phân loại | Đường thực hiện |
|---|---|---|---|---|---|---|
| F01 | Gửi prompt và stream kết quả | 01 | 01 | 01 | Giống | AR |
| F02 | Hủy lượt | 01 | 01 | 09 | Giống | AR |
| F03 | Xếp hàng prompt | 01 | 01 | — | Gần giống | AR |
| F04 | Điều chỉnh ngay trong lượt | — | 01 | — | Khác | N |
| F05 | Câu hỏi tương tác có lựa chọn | 01 | 01 | 06 | Gần giống | AR |
| F06 | Chọn model | 01 | 01 | 01 | Giống | AR |
| F07 | Chọn reasoning effort | 01 | 01 | 01 | Giống | AR |
| F08 | Chế độ ưu tiên tốc độ | 01 | 01 | 01 | Gần giống | N |
| F09 | Định dạng câu trả lời theo preset | 01 | — | — | Khác | AR |
| F10 | Gợi ý prompt tiếp theo | 01 | — | — | Khác | AR |
| F11 | Command palette và slash actions | 01 | 01 | 02 | Gần giống | AR |
| F12 | Đính kèm ảnh hoặc file | 01 | 01 | 01 | Gần giống | AR |
| F13 | Dictation trước khi gửi | 01 | — | 01 | Gần giống | H |
| F14 | Tạo và mở lại phiên native | 02 | 02 | 02 | Giống | AR |
| F15 | Liệt kê và phân trang lịch sử | 02 | 02 | 02 | Gần giống | AR |
| F16 | Đổi tên phiên | 02 | 02 | 02 | Giống | AR |
| F17 | Tag và tổ chức phiên | 02 | 02 | 02 | Gần giống | AR |
| F18 | Archive và khôi phục phiên | — | 02 | — | Khác | AR |
| F19 | Ghim phiên trong danh sách | — | 02 | — | Khác | AR |
| F20 | Tìm kiếm hội thoại | — | 02 | 02 | Gần giống | AR |
| F21 | Xuất transcript | 02 | — | — | Khác | AR |
| F22 | Deep link mở phiên | 02 | — | — | Khác | AR |
| F23 | Tạo nhánh từ mốc lịch sử | 02 | 02 | 02 | Gần giống | AR |
| F24 | Fork native chỉ tồn tại trong RAM | — | 02 | — | Khác | N |
| F25 | Cắt hoặc rewind lịch sử phiên | 04 | 02 | 02 | Gần giống | AR |
| F26 | Phục hồi file từ checkpoint | 04 | — | 02? | Gần giống | AR |
| F27 | Sidechat hoặc câu hỏi bên lề | 03 | 02 | 01 | Gần giống | AR |
| F28 | Bàn giao giữa các engine | — | — | — | Agent Remote bổ sung | AR |
| F29 | Xem dung lượng context | 03 | 03 | 02 | Gần giống | AR |
| F30 | Compact context | 03 | 03 | 02 | Gần giống | AR |
| F31 | Đo token và cache | 03 | 03 | 02 | Gần giống | AR |
| F32 | Quota và thời gian reset | 10 | 03 | 09 | Gần giống | AR |
| F33 | Tham chiếu file hoặc thư mục | 01 | 01 | 01 | Gần giống | AR |
| F34 | Trích đoạn chat, diff, log hoặc artifact | — | 02 | 01 | Gần giống | AR |
| F35 | Tham chiếu phiên trước | — | — | 01 | Khác | AR |
| F36 | Quy tắc theo repo và thư mục | 03 | 07 | 07 | Gần giống | AR |
| F37 | Bộ nhớ qua nhiều phiên | 03 | 07 | 07 | Gần giống | AR |
| F38 | Ghim tin để xem lại | — | — | 10? | Chờ xác minh | AR |
| F39 | Giữ nguồn đã chọn cho các lượt sau | — | — | 10? | Chờ xác minh | AR |
| F40 | Chọn repo, checkout và branch | 05 | 04 | 03 | Gần giống | AR |
| F41 | Tạo worktree riêng | 05 | 04 | 03 | Gần giống | AR |
| F42 | Thêm thư mục vào phạm vi làm việc | 10 | — | 03 | Gần giống | AR |
| F43 | Bootstrap môi trường theo checkout | 05 | 04 | — | Gần giống | H |
| F44 | Remote host và điều khiển từ thiết bị khác | 07 | 04 | 08 | Gần giống | AR |
| F45 | Hosted cloud execution | 08 | 04 | 08 | Gần giống | D |
| F46 | Terminal có input và output | 07 | 04 | 03 | Gần giống | H |
| F47 | Tìm file và tìm trong code | 01 | — | 03 | Gần giống | H |
| F48 | Editor và gợi ý inline | 07 | 08 | 03 | Gần giống | D |
| F49 | PDF viewer | — | — | 03 | Khác | H |
| F50 | Git status và thao tác stage/commit | 06 | 05 | 04 | Gần giống | H |
| F51 | Quyền thực thi ghi theo checkout | — | — | — | Agent Remote bổ sung | AR |
| F52 | Chế độ lập kế hoạch trước khi ghi | 04 | 01 | 04 | Gần giống | AR |
| F53 | Duyệt kế hoạch và phản hồi | 04 | 01 | 04 | Gần giống | AR |
| F54 | Review code theo nguồn cụ thể | 06 | 05 | 04 | Gần giống | AR |
| F55 | Xem diff theo lượt, branch hoặc checkout | 06 | 05 | 04 | Gần giống | AR |
| F56 | Bình luận neo vào diff hoặc artifact | 06 | 05 | 04 | Gần giống | AR |
| F57 | Review PR do dịch vụ hãng vận hành | 06 | 05 | — | Gần giống | D |
| F58 | Theo dõi PR và CI | 07 | 05 | — | Gần giống | H |
| F59 | Tạo artifact dùng lại qua các phiên | 07 | — | 04 | Gần giống | AR |
| F60 | Walkthrough kết quả có bằng chứng | — | — | 04 | Khác | AR |
| F61 | Generative UI tùy biến | — | — | 04 | Khác | D |
| F62 | App preview | 07 | — | 05 | Gần giống | H |
| F63 | Browser điều khiển bằng công cụ | 07 | — | 05 | Gần giống | H |
| F64 | Computer use cấp desktop | 07 | 08 | — | Gần giống | H |
| F65 | Chụp ảnh, recording và replay | 07 | 08 | 05 | Gần giống | H |
| F66 | Profile trình duyệt riêng và domain policy | — | — | 05 | Khác | H |
| F67 | Simulator thiết bị | 07 | — | — | Khác | H |
| F68 | Goals nhiều lượt | 08 | 06 | 06 | Gần giống | AR |
| F69 | Theo dõi task nền | 05 | 06 | 06 | Gần giống | AR |
| F70 | Subagent theo vai trò | 05 | 06 | 06 | Gần giống | AR |
| F71 | Dashboard nhiều phiên và agent | 05 | 06 | 06 | Gần giống | AR |
| F72 | Nhắn trực tiếp cho phiên hoặc subagent | 05 | — | 06 | Gần giống | AR |
| F73 | Team có task dùng chung | 05 | — | 06 | Gần giống | AR |
| F74 | Workflow nhiều bước hoặc nhiều agent | 05 | 06 | 06 | Gần giống | AR |
| F75 | Phỏng vấn yêu cầu có hướng dẫn | — | — | 06 | Khác | AR |
| F76 | Lịch và trigger chạy tự động | 08 | 06 | 06 | Gần giống | AR |
| F77 | Thông báo cần phản hồi | 07 | 06 | 08 | Gần giống | AR |
| F78 | Tích hợp CI và chat công việc | 08 | 07 | — | Gần giống | H |
| F79 | Skills và discovery theo checkout | 09 | 07 | 07 | Gần giống | AR |
| F80 | Lifecycle hooks | 09 | 07 | 07 | Gần giống | H |
| F81 | MCP discovery và tool access | 09 | 07 | 07 | Gần giống | H |
| F82 | Plugin và marketplace | 09 | 07 | 07 | Gần giống | D |
| F83 | Sidecar tích hợp công cụ | — | — | 07 | Khác | H |
| F84 | Learn và duyệt đề xuất rule hoặc skill | — | — | 07 | Khác | AR |
| F85 | Persona và cấu hình vai trò | 05 | 07 | 08 | Gần giống | AR |
| F86 | Model local qua engine | — | — | 08 | Khác | N |
| F87 | Sandbox và quyền truy cập workspace | 10 | 09 | 09 | Gần giống | H |
| F88 | Duyệt tool theo phạm vi | 10 | 09 | 09 | Gần giống | AR |
| F89 | Policy tổ chức và quản lý tài khoản | 10 | 09 | 09 | Gần giống | D |
| F90 | Billing, credits và nâng gói | 10 | 03 | 09 | Gần giống | D |
| F91 | Headless, SDK hoặc ACP | 10 | 09 | 08 | Gần giống | N |
| F92 | Output theo schema | 10 | 09 | 08 | Gần giống | AR |
| F93 | Chẩn đoán cấu hình và capability | 10 | 09 | 09 | Gần giống | AR |
| F94 | Tracing và telemetry | 10 | 03 | 09 | Gần giống | H |

**Đường thực hiện:** AR = Agent Remote sở hữu; H = dịch vụ host dùng chung; N = native có điều kiện; D = ngoài phạm vi đề xuất. Nhóm nền tảng theo hãng, chỉnh theme, phím tắt, lệnh trợ giúp và biến thể theo gói không tách thành tính năng Agent Remote. Các dòng điều phối nhiều agent là backlog sau pilot; quyền ghi và tự giao việc tiếp tục tuân theo phạm vi đã chốt trong plan.

## Điều kiện chốt số sau pilot

- Đối chiếu từng dòng với phiên bản và transport mục tiêu, ghi trạng thái đã có, cần xây, hoặc chưa kiểm chứng. Khi hai hành vi có cùng tên nhưng khác hiệu ứng, tách dòng nếu chúng cần tiêu chí nghiệm thu khác nhau.
- Dùng cùng UI và state cho AR/H. Khi engine có hỗ trợ native tương đương, ghi đường native như cách thực hiện của cùng tính năng. Native khác hành vi (ví dụ steer giữa lượt so với gửi lượt sau) giữ tiêu chí riêng.
- Báo lại tổng theo ID khi đổi phạm vi. Bảng là baseline theo inventory đã khảo sát, chưa phải kiểm kê tất cả cờ, RPC hay tính năng theo từng phiên bản của hãng.
