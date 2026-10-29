# THIẾT KẾ HÌNH 4.2: GIAO DIỆN GIẢNG VIÊN GIÁM SÁT PHÒNG THI VÀ PHÁT HIỆN CẢNH BÁO VI PHẠM

---

## 1. MÔ TẢ ẢNH CHỤP MÀN HÌNH (SCREENSHOT SPECIFICATION)

- **Tên hình trong báo cáo:** `[Hình 4.2: Giao diện giảng viên giám sát phòng thi và cảnh báo vi phạm trực tiếp]`
- **Màn hình giao diện tương ứng:** `com.grading.client.view.TeacherAssessmentMonitorView`
- **Chủ đề màu (Theme):** Dark theme chuyên nghiệp, đồng bộ với ứng dụng sinh viên.
- **Bố cục màn hình:**
  - **Phần đầu (Header Toolbar):** Tên kỳ thi đánh giá, thời gian làm bài còn lại đếm ngược, tổng số thí sinh đang trực tuyến kết nối qua socket TCP cổng 9999.
  - **Bảng dữ liệu trung tâm (TableView):**
    - Cột Mã sinh viên, Họ và tên thí sinh.
    - Cột Địa chỉ IP máy trạm (liên kết với token phiên làm việc).
    - Cột Tiến độ nộp bài (số bài đã nộp / tổng số bài thi).
    - Cột Điểm số tổng hợp được cập nhật trực tiếp theo thời gian thực mỗi khi có công nhân chấm xong bài.
    - Cột Cảnh báo giám sát phòng thi (Proctoring Alert): đếm số lần sinh viên mất tiêu điểm cửa sổ (mất focus), chuyển tab hoặc mở ứng dụng ngoài; các trường hợp vi phạm vượt ngưỡng được tô đỏ cảnh báo (`#E74C3C`).
  - **Thanh tác vụ bên dưới:** Nút xem chi tiết mã nguồn bài nộp của từng sinh viên (`SourceCodeViewerDialog`) và nút xuất báo cáo tổng kết bảng điểm.

---

## 2. HƯỚNG DẪN CHỤP ẢNH TỪ ỨNG DỤNG

1. Khởi chạy máy chủ: `server/run-server.bat`.
2. Khởi chạy ứng dụng khách: `client/run-client.bat`.
3. Đăng nhập bằng tài khoản giảng viên (ví dụ: `teacher1` / mật khẩu tài khoản).
4. Vào mục Quản lý kỳ thi, mở cửa sổ "Giám sát trực tiếp phòng thi".
5. Khi có các phiên kết nối và dữ liệu nộp bài hiển thị trên bảng, dùng phím tắt `Windows + Shift + S` chụp trọn vẹn cửa sổ và lưu vào tài liệu báo cáo.
