# Kiến trúc và bộ chấm

## Sơ đồ luồng

```text
JavaFX Client
  └─ SocketClient ── TCP / JSON line-delimited ──> SocketServer
                                                    └─ ClientHandler (một task/kết nối)
                                                        ├─ Auth / Problem / Academic / Proctor services
                                                        ├─ SubmissionService -> JudgeQueue (bounded)
                                                        └─ PostgreSQL

JudgeEngine -> JudgeWorker x N -> SecurityScanner -> SandboxRunner
                                                   ├─ DockerSandboxRunner (ưu tiên)
                                                   └─ ProcessBuilder local fallback
                                                -> OutputComparator -> SubmissionRepository
                                                -> ConnectionRegistry push notification
```

## Server networking

`SocketServer` lắng nghe cổng cấu hình (mặc định 9999) và dùng pool xử lý kết nối (mặc định 20). `ClientHandler` đọc bằng `BufferedReader.readLine()`, deserialize JSON, route theo `MessageType`, xác thực JWT cho phần lớn thao tác rồi phản hồi bằng `PrintWriter.println()`.

Kết nối có timeout mặc định 5 phút. `ConnectionRegistry` lưu writer/socket của người dùng đang online, thay phiên cũ bằng `FORCE_LOGOUT` khi cùng user đăng nhập ở nơi khác, đồng thời hỗ trợ push kết quả chấm và cảnh báo giáo viên.

Ở client, `SocketClient` có listener thread riêng, map `requestId -> callback`, timeout callback 30 giây, cùng callback riêng cho `JUDGE_NOTIFICATION`, `FORCE_LOGOUT`, `PROCTOR_ALERT` và disconnect. Callback UI phải quay lại JavaFX thread qua `Platform.runLater()`.

## Vòng đời bài nộp

1. `SubmissionService.submit()` kiểm tra ngôn ngữ, tạo row submission và lấy problem/test case.
2. `JudgeQueue` (capacity mặc định 100) nhận `SubmissionTask`; quá tải sẽ là lỗi nộp bài.
3. `JudgeWorker` lấy task, đánh dấu `JUDGING`.
4. `SecurityScanner` kiểm tra tĩnh theo ngôn ngữ. Nếu vi phạm, trả `SECURITY_VIOLATION`.
5. `SandboxRunner.compile()` ghi source vào thư mục tạm theo submission ID và biên dịch.
6. Nếu compile lỗi: `CE`; nếu thành công thì chạy lần lượt các test case.
7. Timeout: `TLE`; exit code khác 0: `RE`; output khác expected sau chuẩn hóa: `WA`; qua tất cả: `AC`.
8. Worker cập nhật DB, cleanup thư mục tạm, và gọi `ConnectionRegistry.sendJudgeNotification()`.

## Ngôn ngữ và lệnh chạy

| Ngôn ngữ | Tên source | Biên dịch | Thực thi |
| --- | --- | --- | --- |
| C | `solution.c` | `gcc -O2` | binary `solution` |
| C++ | `solution.cpp` | `g++ -O2` | binary `solution` |
| Python | `solution.py` | `python3 -m py_compile` | `python3 solution.py` |
| Java | `<MainClass>.java` | `javac -encoding UTF-8` | `java -cp <workDir> <MainClass>` |

## Bảo mật bộ chấm

Lớp 1, `SecurityScanner`, dùng regex để chặn gọi shell/process, xóa file, socket/network và API nguy hiểm theo C/C++, Python, Java. Đây là lọc tĩnh, không phải cơ chế an toàn hoàn chỉnh.

Lớp 2, Docker (image `ltm-judge-runner:latest`) khi có sẵn, chạy với `--network none`, RAM `256m`, CPU `1.0`, volume `/app` read-only và user `sandbox`. Dockerfile cài gcc/g++, Python 3, OpenJDK 21 trên Alpine.

Nếu Docker daemon/image không sẵn, code tự rơi về `ProcessBuilder` trên host, có timeout nhưng không có giới hạn memory hay cô lập mạnh. Xem rủi ro trong `06-known-issues.md`.
