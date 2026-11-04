# T4b.1 — Hợp đồng tính năng (M)

Tách action của sản phẩm khỏi khả năng native của adapter. Service chọn đường thực hiện theo quyền, dữ liệu và ngữ nghĩa của action. Server quảng bá phạm vi, điều kiện thực hiện và hiệu ứng; UI dùng menu phiên, menu tin nhắn và composer chung. Dùng một nguồn trạng thái cho từng action. Nếu chạy native, xác định cách reconcile kết quả trước retry; tránh chạy cả native lẫn đường chung cho cùng thao tác.

[Roadmap phiên cha](../ROADMAP.md) · [Backlog ba engine](../ROADMAP.md#backlog-triển-khai-ba-engine)
