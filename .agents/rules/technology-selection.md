# Technology & Stack Selection Guardrail (Zero-Bias Pragmatism)

Luôn lựa chọn ngôn ngữ, công cụ và kiến trúc dựa trên sự phù hợp và hiệu quả kỹ thuật thực tế cho từng bài toán cụ thể, tuyệt đối không mặc định bất kỳ stack nào theo thói quen (Anti-Golden-Hammer):

1. **Tiêu chí Đánh giá Khách quan:**
   - **Mục tiêu triển khai & Phân phối:** Độc lập (Standalone binary)? Cần môi trường runtime (Interpreter)? Cross-compile có phức tạp không?
   - **Tài nguyên & Hiệu năng:** Yêu cầu RAM, thời gian khởi động (Cold Start), độ trễ I/O và tính đồng thời (Concurrency).
   - **Hệ sinh thái & Thư viện:** Công nghệ nào có thư viện chuyên dụng tốt nhất cho tác vụ đó (ví dụ: AI/ML, Network Tunneling, Mobile UI).

2. **Nguyên tắc Bất biến:**
   - Không thiên vị bất kỳ ngôn ngữ nào (Go, Python, Kotlin, Rust, v.v.). 
   - Khi một thành phần bộc lộ điểm nghẽn hoặc nhược điểm phân phối rõ rệt, phải chủ động so sánh các lựa chọn thay thế để chọn giải pháp tối ưu nhất cho người dùng.
