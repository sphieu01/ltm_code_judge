# Điểm cần lưu ý trước khi phát triển hoặc demo

Các mục dưới đây là quan sát từ mã hiện có; chưa thực hiện sửa chữa trong lần rà soát này.

## Mức ưu tiên cao

1. **Thông tin kết nối database xuất hiện trực tiếp trong `server/run-server.bat`.** Không sao chép secret vào tài liệu, commit hoặc bản phát hành. Hãy thu hồi/đổi mật khẩu nếu đó là credential thật, chuyển sang biến môi trường hoặc secret manager, và thêm file local chứa secret vào `.gitignore`.
2. **JWT có fallback secret hard-code trong `ServerConfig`.** Luôn đặt `JWT_SECRET` ngẫu nhiên, đủ dài ở môi trường chạy thật; không dùng fallback.
3. **Fallback local sandbox chạy mã nộp trên host.** Regex + timeout không cô lập được file system, memory, CPU hoặc các kỹ thuật né lọc. Chỉ demo mã tin cậy khi Docker không bật; production phải bắt buộc Docker/sandbox mạnh hơn.
4. **Script server tự tìm và `taskkill /F` tiến trình đang giữ port 9999.** Hành vi này có thể dừng nhầm tiến trình; nên bỏ/đòi hỏi xác nhận trước khi dùng ở môi trường chung.

## Nhất quán dữ liệu/tài liệu

1. `schema.sql` chỉ tạo `users`, `problems`, `test_cases`, `submissions`, trong khi `seed.sql` dùng `courses`/`classes`; `AcademicRepository` lại tự tạo/migrate các bảng học vụ. Cần hợp nhất các câu lệnh này vào migration/schema chính để bootstrap database mới có thể tái lập hoàn toàn.
2. `README.md` ở root là mẫu báo cáo chưa được điền (và hiển thị lỗi encoding trong PowerShell hiện tại); thông tin thiết kế thực tế nằm nhiều hơn ở `architecture.md` và source. Nên thay README gốc bằng hướng dẫn chạy chuẩn sau khi xác minh.
3. Các script phụ thuộc đường dẫn Java/Maven dành riêng máy cá nhân. Nên ưu tiên `JAVA_HOME`, Maven Wrapper hoặc hướng dẫn Maven cài đặt chuẩn.
4. Độ đo memory được model/schema hỗ trợ nhưng sandbox runner hiện thường ghi `0`; không nên báo cáo như số liệu bộ nhớ thực.

## Kiểm thử nên thực hiện trước mốc quan trọng

- Chạy `mvn test` trong `server`, kiểm thử tích hợp judge với PostgreSQL được cấu hình an toàn.
- Chạy đủ bốn ngôn ngữ và đủ AC/WA/TLE/CE/RE bằng `test_codes`.
- Xác nhận Docker image thực sự được dùng (log `DOCKER_SANDBOX`) và kiểm thử network/memory/timeout của container.
- Kiểm thử nhiều client nộp đồng thời để đánh giá queue capacity, worker count, notification và reconnect.
- Kiểm thử quyền `STUDENT`/`TEACHER`, token hết hạn, force logout, và tất cả API quản trị.
- Kiểm tra encoding UTF-8 của toàn bộ Markdown/SQL/batch trong terminal/IDE mục tiêu.
