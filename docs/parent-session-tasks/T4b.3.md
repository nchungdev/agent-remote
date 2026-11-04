# T4b.3 — Fork và checkpoint (L)

Mọi engine hỗ trợ **Tạo nhánh từ đây** từ event đã lưu. Phiên cha mới giữ liên kết nguồn, điểm cắt, checkpoint và lựa chọn checkout theo D9. Đường chung khởi tạo phiên native mới từ checkpoint và trích đoạn đến đúng điểm cắt, ghi rõ nguồn bị lược. Đường native chỉ dùng khi cùng engine và điểm neo phù hợp. Claude SDK có fork và resume theo UUID cần kiểm thử kết hợp; Codex App Server fork đến hết một turn; Antigravity có fork tương tác cần xác minh transport. UI hiển thị riêng độ đầy đủ của lịch sử và trạng thái file. Khôi phục file là thao tác riêng, cần snapshot hợp lệ, xem trước thay đổi và kiểm tra drift.

[Roadmap phiên cha](../ROADMAP.md) · [Backlog ba engine](../ROADMAP.md#backlog-triển-khai-ba-engine)
