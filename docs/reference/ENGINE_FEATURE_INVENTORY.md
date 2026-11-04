# Inventory tính năng Claude Code, Codex và Antigravity

Khảo sát 2026-11-04. Phạm vi là tính năng công khai của sản phẩm coding trên CLI, app, IDE và giao diện tích hợp được công bố. Các mục dưới đây ghi nhận khả năng ở cấp tính năng. Phiên bản, gói tài khoản, hệ điều hành và bề mặt điều khiển có thể khác nhau. Việc có trong tài liệu chưa chứng minh adapter Agent Remote gọi được.

## Claude Code

| ID | Nhóm | Tính năng công khai cần đối chiếu |
|---|---|---|
| CL-01 | Lượt và composer | Prompt văn bản/ảnh, tham chiếu file, lệnh slash và skills, nhập giọng nói, shell trực tiếp, hàng đợi prompt, ngắt lượt, câu hỏi tương tác, đề xuất prompt, chỉnh model/effort, fast mode và output style. |
| CL-02 | Phiên | Danh sách, resume, fork, resume tại UUID, rename/tag, export, mở từ deep link, phiên terminal, desktop, IDE và web. |
| CL-03 | Context | Context breakdown, tự compact và compact thủ công, side question, project instructions, scoped rules, auto memory, prompt caching và usage. |
| CL-04 | Plan và khôi phục | Plan mode, duyệt hướng thực hiện, checkpoints theo prompt, rewind lịch sử, phục hồi file từ snapshot còn giữ, tóm tắt một phần hội thoại. |
| CL-05 | Công việc song song | Background tasks, custom subagents, agent view, agent teams, task list, nhắn giữa phiên, dynamic workflows, Git worktrees và cô lập subagent. |
| CL-06 | Review và Git | Review local theo branch/PR/path, ultrareview, GitHub PR review và comment, Desktop diff, PR monitoring, GitHub Enterprise. |
| CL-07 | Desktop và thiết bị | Integrated terminal/editor, nhiều pane, app preview, computer use, Chrome integration, iOS Simulator, side chat, Remote Control và Dispatch. |
| CL-08 | Tự động hóa | Goal, loop, nhắc một lần, lịch trong phiên, lịch Desktop, cloud routines, GitHub Actions, GitLab CI/CD và tích hợp Slack. |
| CL-09 | Mở rộng | CLAUDE.md/AGENTS.md, memory, skills, custom commands, hooks, MCP, plugins/marketplaces, code intelligence, output styles. |
| CL-10 | Điều khiển và vận hành | Permission modes và allow/ask/deny, sandbox, policy tổ chức, kiểm tra cấu hình, usage/rate limits, Agent SDK streaming, approvals qua callback, structured output, OpenTelemetry và session storage. |

Nguồn: [Mục lục Claude Code](https://code.claude.com/docs/llms.txt) · [Lệnh](https://code.claude.com/docs/en/commands) · [SDK](https://code.claude.com/docs/en/agent-sdk/typescript) · [Checkpoint](https://code.claude.com/docs/en/checkpointing).

## Codex

| ID | Nhóm | Tính năng công khai cần đối chiếu |
|---|---|---|
| CX-01 | Lượt và composer | Chat/stream, file/ảnh, queue, steer giữa lượt, interrupt, plan, câu hỏi tương tác, model/effort, fast mode, slash commands. |
| CX-02 | Thread | Tạo, list/read phân trang, resume, fork theo lượt và ephemeral fork, rename, archive/unarchive, side chat từ nội dung chọn, pin/organize chat, revert lịch sử. |
| CX-03 | Context và usage | Context indicator, compact thủ công, thread token usage, model/provider capabilities, rate-limit snapshot, reset và account usage theo đường xác thực. |
| CX-04 | Workspace | Local và remote host, cloud tasks, worktrees, chọn branch, environment setup, integrated terminal, chạy lệnh có sandbox và theo dõi output. |
| CX-05 | Review | Review riêng, diff local/branch, nhận xét inline, review GitHub và theo dõi kết quả sửa. |
| CX-06 | Điều phối | Goals có ngân sách và lifecycle, subagents, automations, remote monitoring và notifications. |
| CX-07 | Mở rộng | AGENTS.md, rules, skills discovery/invocation, hooks, MCP, plugins/marketplaces, apps/connectors và memories. |
| CX-08 | Giao diện và thao tác máy | CLI, IDE, desktop và mobile remote, computer use và record/replay theo bề mặt; quyền máy và account policy cần kiểm tra riêng. |
| CX-09 | An toàn và API | Permission profiles, sandbox, approval theo scope, App Server thread/turn/item và SDK, model/provider discovery, cấu hình và experimental flags. |

Nguồn: [Codex Features](https://developers.openai.com/codex/app/features) · [Developer commands](https://developers.openai.com/codex/cli/slash-commands) · [App Server](https://developers.openai.com/codex/app-server/) · [Remote workflows](https://developers.openai.com/blog/mastering-remote-engineering-work-from-your-phone).

## Google Antigravity

| ID | Nhóm | Tính năng công khai cần đối chiếu |
|---|---|---|
| AG-01 | Lượt và composer | Plan/fast, model/effort, đổi model cho một prompt, voice/transcription, attachment, mention file/folder/rule/conversation, quote chat/file/diff/artifact/terminal, câu hỏi bên lề và copy kết quả. |
| AG-02 | Phiên | Project, chat ngoài project, resume/import từ App, rename, search và command palette, fork trong hoặc sang project khác, rewind, context checkpoints và usage/quota. |
| AG-03 | Workspace và editor | Local checkout/worktree, thêm thư mục, integrated terminal, PDF viewer, VCS panel, tab completion, agent panel/overview, task groups, code search và Vim mode. |
| AG-04 | Plan và review | Implementation plan, walkthrough, screenshots, artifact feedback/approval, diff theo lượt/commit/workspace, inline comments, commit từ App và Generative UI. |
| AG-05 | Trình duyệt | Built-in browser, browser subagent, DevTools, screenshot và recording, domain policy và Chrome profile riêng. |
| AG-06 | Điều phối | Custom subagents, Agent Manager, nhắn subagent, task monitor, Teamwork, Boost, Goal, Grill Me, schedule và App automations. |
| AG-07 | Học và mở rộng | Learn đề xuất rule/skill, rules và skills, lifecycle hooks, MCP, plugins/marketplace, sidecars, IDE workflows và customization discovery. |
| AG-08 | Điều khiển và tích hợp | Remote Control, Remote Agents, headless single/stream input, ACP, SDK với personas, local models, tools, structured output, policies và hooks. |
| AG-09 | Vận hành | Execution modes, tool permissions, terminal sandbox, statusline/title, quota/credits và cấu hình CLI. |
| AG-10 | Chờ xác minh | Stick message. Cần tái hiện trên đúng phiên bản và phân biệt ghim hiển thị, giữ context hay hành vi khác. |

Nguồn: [Antigravity Features](https://antigravity.google/docs/features) · [CLI commands](https://antigravity.google/docs/cli/reference/) · [Mentions](https://antigravity.google/docs/at-mentions) · [Headless](https://antigravity.google/docs/cli/headless/).

## Chuyển inventory thành công việc của Agent Remote

| Đường thực hiện | Nhóm ứng viên |
|---|---|
| Agent Remote sở hữu dữ liệu và UX | Tổ chức phiên, queue, quote/đính kèm, sidechat, fork dựng lại từ nguồn, plan và review, artifact, mục tiêu, thông báo, học thành rule/skill, lịch và dashboard công việc. |
| Dùng dịch vụ host chung và xác thực riêng | Worktrees, bootstrap, Git diff/commit, terminal, browser, recording, kiểm chứng trên emulator, lưu artifact và tích hợp CI. |
| Adapter tăng chất lượng khi có capability | Native resume/fork, steer thật giữa lượt, compact native, checkpoint file, telemetry context/cache/quota, discovery tool và native subagent. |
| Tính năng gắn với dịch vụ của hãng | Managed cloud execution, billing, marketplace/catalog, GitHub review do hãng vận hành và chức năng editor chuyên biệt. Ghi rõ phụ thuộc và xác định workflow tương đương nếu có. |

Mỗi hạng mục đưa vào backlog cần có ID nguồn, bề mặt, phiên bản đã kiểm tra, điều kiện quyền, baseline Agent Remote, hành vi chung và phần native tùy chọn. Đợt này mới lập inventory theo nhóm; chưa lập đối chiếu từng lệnh, RPC và biến thể theo phiên bản, chưa kiểm thử runtime. Trước khi chốt phạm vi phát hành, lập bảng từng tính năng với trạng thái đã có, cần xây, phụ thuộc host/dịch vụ hoặc cần xác minh. Giữ ID ổn định để đối chiếu thay đổi từ changelog.
