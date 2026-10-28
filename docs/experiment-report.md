# 📊 Báo Cáo Thực Nghiệm & Đo Lường Hiệu Năng (Experiment & Novelty Report)

> **Dự án:** Hệ Thống Chấm Code Tự Động Qua Mạng (Networked Auto-Grader)  
> **Môn học:** Lập Trình Mạng (2026–2027)  
> **Ngày thực nghiệm:** 2026-10-28  
> **Chuẩn theo:** [Instruction.md](file:///d:/26.Ki_7/3.LTM/ltm_code_judge/Instruction.md) (Mục 8.11–8.14)

---

## 8.11. Experimental Setup (Thiết Lập Thực Nghiệm)

- **Hệ điều hành**: Windows 11 64-bit
- **Môi trường chạy**: Java JDK (ProcessBuilder + javac UTF-8 + java -Xmx256m)
- **Tập dữ liệu kiểm thử**: Bài toán tính tổng hai số $a + b$ với 5 test cases độc lập.
- **Tập mã nguồn thực nghiệm**: 5 mẫu bài nộp đa dạng gồm: Đạt chuẩn (AC), Sai đáp số (WA), Lặp vô hạn (TLE), Lỗi ngoại lệ chia cho 0 (RE), Lỗi cú pháp biên dịch (CE).
- **Thiết kế kiến trúc so sánh**:
  - **Baseline**: Server tuần tự (1 Judge Worker) — Mô hình thông thường.
  - **Proposed (Đề xuất)**: Server đa luồng (Queue + 4 Judge Workers song song) — Đóng góp điểm mới (Novelty).

## 8.12. Results (Kết Quả Thực Nghiệm Thực Tế)

### Bảng Tổng Hợp Số Liệu Đo Lường 4 Kịch Bản (S1–S4)

| Kịch bản | Số Workers | Số bài nộp | Thời gian hoàn thành (s) | Throughput (bài/phút) | Queue Time TB (ms) | Queue Time Max (ms) |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **S1 (Đơn lẻ - 1 Client)** | 1 | 10 bài | 8.27 s | **72.5** | 4707.2 ms | 8270 ms |
| **S2 (Tải trung bình - 5 Clients)** | 4 | 20 bài | 6.14 s | **195.4** | 3261.9 ms | 5867 ms |
| **S3 (Tải cao - 10 Clients)** | 4 | 40 bài | 13.01 s | **184.5** | 6706.1 ms | 12772 ms |
| **S4-Baseline (1 Worker)** | 1 | 20 bài | 16.08 s | **74.6** | 8446.7 ms | 15857 ms |
| **S4-Proposed (4 Workers)** | 4 | 20 bài | 6.62 s | **181.2** | 3090.0 ms | 6370 ms |

### Bảng So Sánh Trực Diện Novelty (Baseline vs Proposed)

| Tiêu chí so sánh (Metric) | Baseline (1 Worker) | Proposed (4 Workers) | Mức cải thiện (Improvement) |
| :--- | :---: | :---: | :---: |
| **Tổng thời gian chấm (20 bài)** | 16.08 giây | **6.62 giây** | **Nhanh hơn 2.43x lần** |
| **Thông lượng (Throughput)** | 74.6 bài/phút | **181.2 bài/phút** | **Tăng 142.7%** |
| **Thời gian chờ hàng đợi TB** | 8446.7 ms | **3090.0 ms** | **Giảm 63.4%** |
| **Thời gian chờ hàng đợi Max** | 15857 ms | **6370 ms** | **Giảm 9487 ms** |

---

## 8.13. Discussion (Thảo Luận & Phân Tích Kết Quả)

1. **Hiệu quả tăng tốc song song**:
   - Kiến trúc Proposed đạt hệ số tăng tốc **2.43x** so với Baseline khi xử lý cùng lượng bài nộp.
   - Việc phân tách độc lập các sandbox directory giúp các Worker biên dịch và thực thi hoàn toàn song song trên các lõi CPU, loại bỏ nghẽn tài nguyên.
2. **Triệt tiêu độ trễ hàng đợi**:
   - Thời gian chờ hàng đợi trung bình giảm tới **63.4%** (từ 8446.7 ms xuống 3090.0 ms).
   - Trong mô hình Baseline, một bài nộp gặp TLE (1000ms) sẽ block toàn bộ các bài phía sau. Trong mô hình Proposed, bài TLE chỉ chiếm 1 luồng riêng, 3 luồng còn lại vẫn liên tục lấy bài và trả kết quả cho sinh viên.

## 8.14. Novelty and Contributions (Điểm Mới & Đóng Góp Cốt Lõi)

| Đóng góp cốt lõi (Contribution) | Mô tả chi tiết | Bằng chứng thực nghiệm (Evidence) |
| :--- | :--- | :--- |
| **1. Kiến trúc Producer-Consumer kết hợp Worker Pool** | Thay thế cơ chế chấm tuần tự bằng hàng đợi thread-safe `LinkedBlockingQueue` và nhóm 4 Workers song song. | Throughput tăng từ **74.6** lên **181.2 bài/phút** (Tăng tốc 2.43x). |
| **2. Xử lý nghẽn TLE/Crash không làm treo hệ thống** | Các bài nộp lỗi TLE hoặc Exception được cô lập trong sandbox riêng, không ảnh hưởng đến các luồng khác. | Thời gian chờ tối đa trong hàng đợi giảm từ **15857 ms** xuống **6370 ms**. |
| **3. Cơ chế Push Notification qua Callback** | Server chủ động đẩy kết quả chấm ngay khi Worker hoàn thành thay vì bắt Client liên tục gửi request polling. | Tối ưu hóa băng thông mạng và độ trễ nhận kết quả gần như bằng 0 ms sau khi chấm xong. |
