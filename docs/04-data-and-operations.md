# Dữ liệu, cấu hình và vận hành

## Database

`server/src/main/resources/schema.sql` định nghĩa:

| Bảng | Mục đích chính |
| --- | --- |
| `users` | username duy nhất, hash mật khẩu, họ tên, role `STUDENT`/`TEACHER` |
| `problems` | đề bài, giới hạn thời gian/bộ nhớ, độ khó, người tạo |
| `test_cases` | input/output, sample/hidden, thứ tự chạy |
| `submissions` | source, language, verdict, score, số test, runtime, compile output |

Các enum: `user_role`, `difficulty_level`, `verdict_type`. `seed.sql` tạo tài khoản giáo viên mẫu, học phần/lớp mẫu và 5 bài: Hello World, Tổng Hai Số, Kiểm Tra Số Nguyên Tố, Sắp Xếp Mảng, Fibonacci Mod. Test case mẫu nằm trong `test_codes/` cho Java/C++/Python, bao phủ AC/WA/TLE/CE/RE.

`AcademicRepository` có logic `CREATE TABLE IF NOT EXISTS`/migration cho `courses`, `classes`, quan hệ sinh viên-lớp và các cột active; do đó schema file chưa phản ánh đầy đủ tất cả bảng học vụ. Xem phần lưu ý.

## Biến môi trường server

| Biến | Mặc định |
| --- | --- |
| `SERVER_PORT` | `9999` |
| `CONNECTION_POOL_SIZE` | `20` |
| `SOCKET_TIMEOUT_MS` | `300000` |
| `JUDGE_WORKER_COUNT` | `4` |
| `JUDGE_QUEUE_CAPACITY` | `100` |
| `JWT_SECRET` | có fallback trong source, cần override khi deploy |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | placeholder Neon/PostgreSQL trong `DatabaseConfig` |

HikariCP dùng tối đa 10 kết nối, tối thiểu 2 idle. Client cho nhập host/port, mặc định `localhost:9999`.

## Cách chạy hiện có

1. Tạo database PostgreSQL, chạy `schema.sql`, sau đó `seed.sql` (và đảm bảo phần schema học vụ được repository khởi tạo).
2. Đặt `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` an toàn.
3. (Khuyến nghị) build Docker image bằng `server/docker/build-image.bat` hoặc `.sh` để bật sandbox container.
4. Chạy `run-server.bat` từ root, hoặc `server/run-server.bat`; script build shaded JAR rồi chạy nó.
5. Chạy `run-client.bat` từ root, hoặc `client/run-client.bat`; script chạy `mvn clean compile javafx:run`.

Maven server đóng gói `code-grading-server-1.0.0.jar`; client dùng JavaFX Maven plugin. Có integration test `ProductionJudgeIntegrationTest`, và các lớp demo/test judge trong package `server...judge`.

## Thao tác kiểm thử chức năng

- Khởi động server trước, sau đó client.
- Đăng nhập giáo viên để tạo/quản lý dữ liệu hoặc sử dụng seed.
- Đăng nhập sinh viên, chọn bài, tải/dán file trong `test_codes/<language>/`.
- Kỳ vọng các file `*_ac` nhận AC; `test_wa`, `test_tle`, `test_ce`, `test_re` lần lượt nhận WA/TLE/CE/RE.
- Kiểm tra notification khi nộp, lịch sử, và cảnh báo giám sát trong giao diện giáo viên.
