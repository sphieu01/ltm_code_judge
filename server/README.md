# Code Grading Server — Thành Viên B

> **BTL Lập Trình Mạng** — Server Core: Network + Services + Database

## Cấu Trúc Source Code

```
server/
├── pom.xml                              # Maven dependencies
└── src/main/
    ├── java/com/grading/server/
    │   ├── ServerApp.java               ← Entry point, khởi tạo tất cả components
    │   ├── MockJudgeWorker.java         ← Dùng để test B độc lập (trả AC giả)
    │   │
    │   ├── config/
    │   │   ├── DatabaseConfig.java      ← HikariCP pool cho PostgreSQL/Neon.db
    │   │   └── ServerConfig.java        ← Port, pool sizes, JWT secret
    │   │
    │   ├── model/
    │   │   ├── User.java                ← Map bảng users
    │   │   ├── Problem.java             ← Map bảng problems
    │   │   ├── TestCase.java            ← Map bảng test_cases
    │   │   └── Submission.java          ← Map bảng submissions + verdict constants
    │   │
    │   ├── protocol/
    │   │   ├── Message.java             ← Base JSON message class
    │   │   ├── MessageType.java         ← Enum tất cả message types
    │   │   └── MessageSerializer.java   ← Gson serialize/deserialize
    │   │
    │   ├── repository/
    │   │   ├── UserRepository.java      ← JDBC CRUD cho bảng users
    │   │   ├── ProblemRepository.java   ← JDBC CRUD cho bảng problems + test_cases
    │   │   └── SubmissionRepository.java ← JDBC CRUD cho bảng submissions
    │   │
    │   ├── service/
    │   │   ├── AuthService.java         ← Login, register, JWT, password hashing
    │   │   ├── ProblemService.java      ← Business logic cho problems
    │   │   └── SubmissionService.java   ← Nhận bài nộp, enqueue
    │   │
    │   ├── judge/
    │   │   ├── JudgeQueue.java          ← LinkedBlockingQueue wrapper
    │   │   ├── JudgeWorker.java         ← Worker thread chấm bài
    │   │   ├── JudgeWorkerPool.java     ← ExecutorService quản lý workers
    │   │   ├── SandboxRunner.java       ← Compile + run code (ProcessBuilder)
    │   │   └── OutputComparator.java    ← So sánh output
    │   │
    │   └── network/
    │       ├── SocketServer.java        ← ServerSocket + ThreadPool accept loop
    │       ├── ClientHandler.java       ← Xử lý 1 connection (1 thread)
    │       └── ConnectionRegistry.java  ← ConcurrentHashMap<userId, writer> cho push
    │
    └── resources/
        ├── logback.xml                  ← Logging config
        ├── schema.sql                   ← Tạo DB tables
        └── seed.sql                     ← Dữ liệu mẫu
```

## Setup Nhanh

### 1. Cấu hình Neon.db

Mở [`DatabaseConfig.java`](src/main/java/com/grading/server/config/DatabaseConfig.java) và điền thông tin kết nối:

```java
private static final String DB_URL = "jdbc:postgresql://YOUR_NEON_HOST.neon.tech/YOUR_DB_NAME?sslmode=require";
private static final String DB_USER = "YOUR_NEON_USERNAME";
private static final String DB_PASSWORD = "YOUR_NEON_PASSWORD";
```

Hoặc dùng environment variables (khuyến nghị):
```bash
set DB_URL=jdbc:postgresql://xxx.neon.tech/mydb?sslmode=require
set DB_USER=myuser
set DB_PASSWORD=mypassword
```

### 2. Chạy Schema SQL

Mở DBeaver, kết nối đến Neon.db, rồi chạy:
```
src/main/resources/schema.sql
src/main/resources/seed.sql
```

### 3. Build và Chạy

```bash
cd server
mvn clean package -DskipTests
java -jar target/code-grading-server-1.0.0.jar
```

Server sẽ lắng nghe trên port `9999` (mặc định).

## Test Độc Lập (Không cần Client)

Dùng **Telnet** hoặc **netcat** để gửi JSON message thủ công:

```bash
# Windows PowerShell
Test-NetConnection localhost -Port 9999

# Hoặc dùng netcat (nc)
echo '{"type":"LOGIN","requestId":"req-001","token":null,"data":{"username":"student01","password":"password123"}}' | nc localhost 9999
```

## Protocol Reference

| Message Type | Direction | Data fields |
|---|---|---|
| `LOGIN` | C→S | `username`, `password` |
| `LOGIN_RESPONSE` | S→C | `token`, `userId`, `role` |
| `REGISTER` | C→S | `username`, `password`, `fullName`, `role` |
| `GET_PROBLEMS` | C→S | _(none)_ |
| `GET_PROBLEMS_RESPONSE` | S→C | `[{id, title, difficulty, ...}]` |
| `GET_PROBLEM_DETAIL` | C→S | `problemId` |
| `SUBMIT` | C→S | `problemId`, `sourceCode`, `language` |
| `SUBMIT_RESPONSE` | S→C | `submissionId`, `verdict:"PENDING"` |
| `JUDGE_NOTIFICATION` | S→C | `submissionId`, `verdict`, `score`, ... |
| `GET_RESULT` | C→S | `submissionId` |
| `GET_HISTORY` | C→S | `problemId` (optional) |
| `DISCONNECT` | C→S | _(none)_ |

## Mock Mode (Dev)

Để test B mà không cần C viết SandboxRunner xong, dùng `MockJudgeWorker`:

```java
// Trong ServerApp.java, thay JudgeWorkerPool bằng:
Thread mockWorker = new Thread(new MockJudgeWorker(judgeQueue, submissionRepository, connectionRegistry));
mockWorker.start();
```

MockJudgeWorker luôn trả **AC** sau **2 giây** cho mọi submission.
