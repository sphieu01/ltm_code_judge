# Tài liệu ngữ cảnh dự án

Thư mục này là bản tóm tắt kỹ thuật của mã nguồn hiện tại, được tạo ngày 21/10/2026 để dùng làm ngữ cảnh cho các lần làm việc tiếp theo. Nội dung mô tả những gì **đang có trong repository**, không khẳng định các tính năng đã được kiểm thử toàn diện.

## Mục lục

| Tài liệu | Nội dung |
| --- | --- |
| [01-overview.md](01-overview.md) | Mục tiêu, cấu trúc thư mục, công nghệ, chức năng và luồng người dùng |
| [02-architecture-and-judge.md](02-architecture-and-judge.md) | Kiến trúc client/server, TCP, hàng đợi và quy trình chấm |
| [03-protocol.md](03-protocol.md) | Định dạng gói tin và toàn bộ nhóm message hiện có |
| [04-data-and-operations.md](04-data-and-operations.md) | Database, seed data, cấu hình, cách chạy và kiểm thử |
| [05-code-map.md](05-code-map.md) | Bản đồ module/lớp nguồn để định vị nhanh khi sửa mã |
| [06-known-issues.md](06-known-issues.md) | Các rủi ro, sai khác và việc cần kiểm tra trước khi demo/triển khai |

## Đọc nhanh

- Đây là hệ thống chấm code tự động qua TCP socket, gồm JavaFX client và Java server.
- Server hỗ trợ C, C++, Java và Python; chấm bất đồng bộ bằng queue + worker pool.
- PostgreSQL lưu người dùng, bài tập, test case và bài nộp; các bảng học phần/lớp được tự tạo bổ sung trong `AcademicRepository`.
- Docker sandbox là chế độ ưu tiên. Nếu Docker/image không sẵn có, hệ thống chạy tiến trình trực tiếp trên máy chủ; chế độ fallback này không phù hợp để chạy mã không tin cậy trong môi trường thật.

## Phạm vi rà soát

Đã rà soát cấu trúc file, tài liệu gốc, Maven, tài nguyên, script chạy, schema/seed, và các lớp server/client/judge chính. Không thực hiện thay đổi mã ứng dụng hoặc chạy kết nối database/Docker; chỉ tạo tài liệu này.
