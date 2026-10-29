# THIẾT KẾ HÌNH 4.1: GIAO DIỆN SINH VIÊN NỘP BÀI VÀ NHẬN KẾT QUẢ PHÁN QUYẾT THỜI GIAN THỰC

---

## 1. MÔ TẢ ẢNH CHỤP MÀN HÌNH (SCREENSHOT SPECIFICATION)

- **Tên hình trong báo cáo:** `[Hình 4.1: Giao diện sinh viên nộp bài và nhận kết quả phán quyết thời gian thực qua socket TCP]`
- **Màn hình giao diện tương ứng:** `com.grading.client.view.ProblemView` (hoặc `DashboardView` khi làm bài).
- **Chủ đề màu (Theme):** Dark theme chuẩn IDE (`#1E1E1E` nền chính, `#252526` thẻ panel, viền `#333333`).
- **Bố cục màn hình (SplitPane 3 phân vùng):**
  - **Phân vùng trái (25%):** Thông tin đề bài, giới hạn thời gian (2s), giới hạn bộ nhớ (256MB), mô tả input/output và các ca kiểm thử ví dụ.
  - **Phân vùng giữa (50%):** Trình soạn thảo mã nguồn tích hợp đánh số dòng (`CodeArea`), hỗ trợ chọn ngôn ngữ lập trình (Java, C++, Python), nút chọn tệp ZIP dự án và nút hành động chính "Nộp bài" màu xanh lam (`#007ACC`).
  - **Phân vùng phải (25%):** Bảng kết quả phán quyết thời gian thực nhận qua socket TCP (không cần tải lại trang):
    - Nhãn trạng thái Accepted màu xanh lục (`#2ECC71`).
    - Bảng danh sách chi tiết các test case: Test 1 (Pass - 12ms), Test 2 (Pass - 15ms), Test 3 (Pass - 18ms)...
    - Thanh tiến độ ProgressBar hiển thị trạng thái hoàn thành 100%.

---

## 2. HƯỚNG DẪN CHỤP ẢNH TỪ ỨNG DỤNG

1. Khởi chạy máy chủ: `server/run-server.bat` (hoặc chạy lớp `ServerApp`).
2. Khởi chạy ứng dụng khách: `client/run-client.bat` (hoặc chạy lớp `Launcher`).
3. Đăng nhập bằng tài khoản sinh viên (ví dụ: `student1` / mật khẩu tài khoản).
4. Chọn một bài tập trong danh sách lớp, dán mã nguồn giải thuật đúng hoặc bấm nộp bài.
5. Khi máy chủ đẩy kết quả phán quyết Accepted xanh lá về màn hình, dùng phím tắt `Windows + Shift + S` chụp trọn vẹn cửa sổ ứng dụng và lưu ảnh vào thư mục báo cáo.
