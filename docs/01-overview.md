# Tổng quan dự án

## Mục tiêu

Đồ án Lập trình mạng có chủ đề **Networked Auto-Grader**: sinh viên kết nối desktop client tới server qua TCP để xem bài, nộp mã nguồn và nhận kết quả chấm; giáo viên quản lý học phần/lớp/sinh viên/bài tập và nhận cảnh báo giám sát.

## Cấu trúc repository

```text
ltm_code_judge/
├─ client/                 JavaFX desktop client (Maven)
├─ server/                 TCP server, nghiệp vụ, database, judge (Maven)
│  ├─ docker/              Dockerfile và script build image sandbox
│  └─ src/main/resources/  schema.sql, seed.sql, logback.xml
├─ test_codes/             mã mẫu AC/WA/TLE/CE/RE cho Java/C++/Python
├─ docs/                   tài liệu ngữ cảnh này
├─ architecture.md         mô tả kiến trúc gốc khá chi tiết
├─ task-division.md        phân công thành viên
├─ Instruction.md          đề cương/yêu cầu báo cáo môn học
├─ Topics.md               danh sách đề tài
├─ run-server.bat          chuyển vào server và chạy script server
└─ run-client.bat          chuyển vào client và chạy script client
```

## Công nghệ

| Phần | Công nghệ đang dùng |
| --- | --- |
| Ngôn ngữ / runtime | Java 17 (cả Maven module server và client) |
| Client | JavaFX 21, FXML (màn login), CSS light/dark, Gson |
| Network | TCP socket lâu dài, JSON UTF-8, mỗi message một dòng (`\n`) |
| Server | Java, `ServerSocket`, executor cho kết nối, SLF4J/Logback |
| Database | PostgreSQL/Neon, JDBC + HikariCP |
| Xác thực | JWT (`jjwt`) và hash mật khẩu SHA-256 với salt |
| Judge | Queue + worker threads; gcc/g++, Python, javac/java |
| Cô lập | Docker Alpine, hoặc local process fallback |

## Vai trò và chức năng

**Sinh viên**: đăng ký/đăng nhập, chọn lớp và bài đang kích hoạt, xem chi tiết + sample test, soạn/tải/dán mã, chọn ngôn ngữ, nộp bài, theo dõi lịch sử/kết quả. Client cũng ghi nhận sự kiện giám sát như mất focus hoặc dấu hiệu dùng công cụ AI.

**Giáo viên**: đăng nhập vào `AdminView`, tạo/xóa học phần, quản lý lớp tín chỉ, import/sửa/xóa sinh viên, tạo/sửa/bật-tắt bài tập và toàn bộ test case, xem log giám sát và nhận `PROCTOR_ALERT` theo thời gian thực.

## Luồng người dùng chính

1. Client nhập host/port (mặc định `localhost:9999`), mở TCP socket và đăng nhập/đăng ký.
2. Server cấp JWT. Các yêu cầu sau gửi kèm token.
3. Sinh viên lấy lớp/bài phù hợp, lấy chi tiết bài và test case mẫu.
4. `SUBMIT` tạo submission `PENDING`, sau đó đẩy `SubmissionTask` vào hàng đợi.
5. Worker đổi trạng thái sang `JUDGING`, quét an toàn, biên dịch, chạy từng test, so sánh output và cập nhật kết quả.
6. Server push `JUDGE_NOTIFICATION` nếu người nộp còn trực tuyến; client cũng có thể gọi `GET_RESULT`/`GET_HISTORY`.

## Phân loại verdict

`PENDING`, `JUDGING`, `AC`, `WA`, `TLE`, `RE`, `CE`, `SECURITY_VIOLATION`.

Điểm, số test qua/tổng test, thời gian chạy lớn nhất, bộ nhớ (hiện local runner thường trả 0) và lỗi biên dịch được lưu cùng bài nộp.
