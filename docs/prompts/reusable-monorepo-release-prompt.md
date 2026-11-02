# Reusable Prompt: Tách độc lập CI/CD Release cho Monorepo (Client + Server)

> **Mục đích:** Sử dụng prompt này cho bất kỳ AI coding assistant nào khi bạn muốn thiết lập hệ thống release tự động cho dự án Monorepo gồm Client (Mobile/Desktop/Web) và Server (Python/Go/Node/v.v.), đảm bảo:
> 1. Release Client chỉ build Client.
> 2. Release Server chỉ build Server.
> 3. Tránh tốn kém runner minutes và rate limit CI/CD.
> 4. In-App Updater tự động nhận diện đúng bản cập nhật mà không bị release của phía kia ghi đè.

---

```markdown
Hãy thiết lập quy trình CI/CD Release tự động hóa độc lập cho dự án monorepo của tôi (gồm phần [CLIENT: Android/KMP/Flutter/iOS/Web] và phần [SERVER: Python/Go/Node.js]), tuân thủ các nguyên tắc sau:

### 1. Phân tách Release độc lập (Chống lãng phí CI/CD & Tránh Rate Limit)
- Tuyệt đối không build chung tất cả các nền tảng trong cùng một workflow khi publish.
- Tạo 2 file GitHub Actions workflow riêng biệt:
  * `.github/workflows/release-app.yml`: Chỉ kích hoạt khi push tag dạng `app-v*` (hoặc trigger thủ công qua `workflow_dispatch`). Chỉ build artifact cho Client (ví dụ: APK Release đã ký, IPA, hoặc Desktop binary).
  * `.github/workflows/release-server.yml`: Chỉ kích hoạt khi push tag dạng `server-v*` (hoặc trigger qua `workflow_dispatch`). Chỉ build artifact cho Server.

### 2. Yêu cầu đóng gói Server đa nền tảng
- **Standalone Binaries (Không yêu cầu máy host cài sẵn runtime):**
  * Dùng PyInstaller (hoặc công cụ đóng gói tương ứng) build ra single-file executable độc lập.
  * Chạy ma trận trên GitHub Actions:
    - macOS (Apple Silicon `macos-latest`): đóng gói thành bộ cài đặt `.dmg` (kéo thả vào `/Applications`) và `.tar.gz` (CLI)
    - Linux (`ubuntu-latest` x64): đóng gói thành `.tar.gz`
    - Windows (`windows-latest` x64): đóng gói thành `.zip` (chứa `.exe`)
  * Cung cấp kịch bản build local (ví dụ `server/build_binary.py`) để dev có thể test build ngay trên máy cá nhân.
- **Docker Container:**
  * Tạo `Dockerfile` nhẹ tối ưu và `docker-compose.yml`.
  * Tự động build multi-arch (`linux/amd64`, `linux/arm64`) và push lên GitHub Container Registry (`ghcr.io`).

### 3. Yêu cầu đóng gói Client (App)
- Thiết lập quy trình build artifact (ví dụ `./gradlew assembleRelease` cho Android).
- Cấu hình signing sẵn sàng để file đầu ra có thể cài đặt trực tiếp ngay sau khi tải về từ GitHub Releases.
- Tự động chuẩn hóa tên asset đính kèm theo version (ví dụ: `[app-name]-android-v1.0.0.apk`).

### 4. Khả năng chống xung đột cho tính năng In-App OTA Updater (Quan trọng)
- Nếu App có tính năng tự kiểm tra cập nhật qua GitHub Releases API:
  * Không chỉ gọi endpoint `/releases/latest` (vì nếu server vừa release một tag mới hơn, endpoint này sẽ trả về release của server và không có file cài của app).
  * Thay vào đó, gọi `/releases?per_page=10`, duyệt danh sách và lọc đúng bản release gần nhất có chứa file cài đặt của app (ví dụ đuôi `.apk`).
  * Xử lý cắt bỏ tiền tố tag (`app-v`, `v`) bằng Regex an toàn trước khi so sánh phiên bản (SemVer).

Hãy tạo đầy đủ các file workflow GitHub Actions, cấu hình build của Server/App và cập nhật `.gitignore` tương ứng.
```
