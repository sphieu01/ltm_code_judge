# Bản đồ mã nguồn

## Server

| Package / lớp | Trách nhiệm |
| --- | --- |
| `ServerApp` | khởi tạo repository/service/judge/socket server và shutdown hook |
| `config.ServerConfig`, `DatabaseConfig` | đọc biến môi trường, cấu hình socket/JWT/worker và HikariCP |
| `network.SocketServer` | accept TCP connection, executor xử lý handler |
| `network.ClientHandler` | route toàn bộ protocol request, auth middleware và response |
| `network.ConnectionRegistry` | phiên online, force logout, push judge/proctor notification |
| `protocol.Message`, `MessageType`, `MessageSerializer` | hợp đồng JSON server |
| `service.AuthService` | register/login, JWT, password, thao tác student liên quan auth |
| `service.ProblemService` | truy vấn/tạo/bật-tắt bài và test case |
| `service.SubmissionService` | tạo submission, xếp hàng chấm, tra kết quả/lịch sử |
| `service.AcademicService` | facade học phần/lớp/sinh viên |
| `service.ProctorService` | log và mức độ vi phạm giám sát |
| `repository.*` | JDBC cho user/problem/submission/academic; repository academic tự migration một phần |
| `model.*` | entity DB: User, Problem, TestCase, Submission, Course, CreditClass, ProctorLog |
| `judge.JudgeEngine/Queue/Worker/WorkerPool` | orchestration chấm bài song song và metrics |
| `judge.SandboxRunner`, `docker.DockerSandboxRunner` | compile/run, local fallback và Docker sandbox |
| `judge.security.SecurityScanner` | static regex security scan |
| `judge.OutputComparator` | chuẩn hóa/so sánh output |
| `judge.experiment.*` | thu metrics/chạy thí nghiệm |

## Client

| Package / lớp | Trách nhiệm |
| --- | --- |
| `ClientApp` | JavaFX entry point và chuyển màn hình login/dashboard/admin/problem |
| `network.SocketClient` | TCP persistent connection, callbacks, request correlation, push handling |
| `network.SessionManager` | giữ SocketClient, token và user hiện tại |
| `protocol.Request/Response/MessageType` | DTO giao thức client |
| `controller.LoginController` | kết nối và đăng nhập/đăng ký |
| `controller.DashboardController` | tải lớp/bài của sinh viên |
| `controller.ProblemController` | nộp mã, nhận judge notification, lịch sử và proctor events |
| `controller.AdminController` | thao tác học vụ/bài tập và cảnh báo giáo viên |
| `view.LoginView`, `DashboardView`, `ProblemView`, `AdminView` | màn hình chính JavaFX |
| `view.*Dialog`, `ProfilePopup`, `ThemeManager` | dialog CRUD/profile và giao diện sáng/tối |
| `proctor.AiToolDetector` | phát hiện tín hiệu công cụ AI ở phía client |
| `model.*` | DTO UI: User, Problem, Submission, Course, CreditClass, ProctorLog |

## Tài nguyên

- `client/src/main/resources/fxml/login.fxml`: FXML dùng cho login.
- `client/src/main/resources/css/style.css`, `light.css`, `dark.css`: stylesheet.
- `server/src/main/resources/logback.xml`: logging.
- `server/judge/member-b-judge-guide.md`: hướng dẫn nội bộ chi tiết phần worker/judge.
