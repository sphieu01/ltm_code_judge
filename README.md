# Code Judge — Hệ thống chấm code tự động

Ứng dụng phục vụ quản lý lớp tín chỉ, giao bài tập và tổ chức bài kiểm tra lập trình. Sinh viên sử dụng **client desktop JavaFX** để nộp bài C, C++, Java hoặc Python; server chấm theo test case và gửi kết quả về qua kết nối TCP. Hệ thống còn có phần kiểm tra cấu trúc Java OOP, lịch sử nộp bài và giám sát bài kiểm tra.

README này hướng dẫn dựng **môi trường phát triển local từ đầu** trên Windows, macOS và Linux. Nếu chỉ cần mở client để kết nối server có sẵn, xem [Chạy client với server của nhóm](#remote-client).

## Mục lục

1. [Các thành phần và yêu cầu](#requirements)
2. [Cài công cụ trên từng hệ điều hành](#tools)
3. [Lấy mã nguồn](#source)
4. [Chuẩn bị PostgreSQL và Docker sandbox](#infrastructure)
5. [Build và khởi tạo server lần đầu](#server)
6. [Chạy client](#client)
7. [Đăng nhập và kiểm tra toàn bộ luồng nộp bài](#first-run)
8. [Chạy client với server của nhóm, kết nối LAN](#remote-client)
9. [MinIO và Docker Compose hiện có](#minio)
10. [Tra cứu cấu hình](#configuration)
11. [Kiểm thử, đóng gói và sử dụng IDE](#development)
12. [Dừng và chạy lại dự án](#restart)
13. [Xử lý lỗi thường gặp](#troubleshooting)
14. [Cấu trúc thư mục và tài liệu](#structure)

<a id="requirements"></a>
## 1. Các thành phần và yêu cầu

```text
Client JavaFX -- TCP/JSON, cổng 9999 --> Server Java
                                          |
                              +-----------+-----------+
                              |                       |
                         PostgreSQL             Hàng đợi chấm
                                                      |
                                               Worker + sandbox
                                                      |
                                         gcc / g++ / Java / Python

MinIO: nơi lưu thêm file bài nộp, có thể bật sau.
```

| Thành phần | Công cụ / phiên bản dùng trong hướng dẫn | Có cần không? |
| --- | --- | --- |
| Build và chạy Java | **JDK 21**, bản 64 bit phù hợp CPU | Bắt buộc khi chạy từ mã nguồn |
| Build tool | Apache Maven **3.9.x hoặc 3.10.x** | Bắt buộc khi chạy từ mã nguồn |
| Mã nguồn | Git | Cần nếu clone repository |
| Database | PostgreSQL **16**, chạy bằng Docker trong hướng dẫn này | Bắt buộc với server |
| Container | Docker Desktop trên Windows/macOS; Docker Engine trên Linux | Dùng cho database local và sandbox trong hướng dẫn |
| Client UI | JavaFX **17.0.10**, AtlantaFX | Maven tự tải, không cần cài JavaFX SDK riêng |
| Object storage | MinIO | Tùy chọn |
| IDE | IntelliJ IDEA, VS Code hoặc IDE hỗ trợ Maven | Tùy chọn; có thể chạy hoàn toàn bằng terminal |

Hai module biên dịch với mức Java 17. **Hướng dẫn chọn JDK 21** vì image chấm bài trong `server/docker/Dockerfile` dùng OpenJDK 21; phần chấm OOP nạp các class đã biên dịch vào JVM của server, nên JVM cần đọc được bytecode đó. Nếu dùng JDK 17 cho server, cần đồng bộ lại Java trong image chấm bài trước khi thử OOP.

Nên có khoảng **8 GB RAM trở lên** và vài GB ổ đĩa trống để chạy IDE, Docker và nhiều worker; đây là gợi ý cho máy phát triển, chưa phải cấu hình tối thiểu được đo. Cần Internet ở lần đầu để Maven tải thư viện và Docker tải image. Client cần môi trường desktop có giao diện đồ họa.

**Quy ước trong tài liệu:**

- Windows dùng **PowerShell**; macOS/Linux dùng **Terminal, bash hoặc zsh**.
- Các lệnh được chạy tại **thư mục gốc repository**, nơi chứa `docker-compose.yml`, trừ khi có ghi khác.
- Giữ một terminal chạy server và mở terminal khác chạy client hoặc thực hiện lệnh SQL.
- Các mật khẩu ví dụ chỉ dành cho môi trường local mới tạo trong tài liệu.

<a id="tools"></a>
## 2. Cài công cụ trên từng hệ điều hành

### 2.1. Windows

**Bước 1 — Cài Git và JDK 21**

1. Tải và cài [Git for Windows](https://git-scm.com/install/windows), cho phép dùng Git từ command line.
2. Vào [trang tải Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21), chọn Windows, **JDK 21**, kiến trúc phù hợp; máy Intel/AMD thông thường dùng x64.
3. Chạy bộ cài. Với bản MSI, bật **Add to PATH** và **Set JAVA_HOME** theo [hướng dẫn của Adoptium](https://adoptium.net/installation/windows/).
4. Nếu đặt thủ công trong **Environment Variables**, `JAVA_HOME` phải trỏ tới thư mục JDK thực tế, chẳng hạn `C:\Program Files\Eclipse Adoptium\jdk-21...`, và `Path` có `%JAVA_HOME%\bin`. Không thêm `\bin` vào giá trị `JAVA_HOME`.

**Bước 2 — Cài Maven**

1. Tải **Binary zip archive** của Maven 3.9.x hoặc 3.10.x từ [Apache Maven](https://maven.apache.org/download.cgi).
2. Giải nén vào thư mục cố định, ví dụ `C:\Tools\apache-maven-3.9.16` nếu tải bản 3.9.16.
3. Thêm thư mục `bin` bên trong Maven vào biến `Path` của tài khoản Windows.
4. Đóng và mở lại PowerShell; nếu dùng terminal trong IDE, khởi động lại IDE để nhận biến môi trường mới. Xem thêm [hướng dẫn cài Maven](https://maven.apache.org/install).

**Bước 3 — Cài Docker Desktop**

1. Cài [Docker Desktop cho Windows](https://docs.docker.com/desktop/setup/install/windows-install/) theo yêu cầu hệ điều hành tại trang này.
2. Với backend WSL 2, bật ảo hóa trong BIOS/UEFI nếu máy chưa bật. Nếu chưa có WSL, mở PowerShell bằng quyền Administrator, chạy `wsl --install`, rồi khởi động lại khi được yêu cầu.
3. Mở Docker Desktop và sử dụng **Linux containers**. Chờ Docker Engine khởi động hoàn tất.

**Bước 4 — Kiểm tra trong PowerShell mới**

```powershell
git --version
java -version
javac -version
mvn -version
docker version
docker compose version
```

`java`, `javac` và dòng Java home trong `mvn -version` cần cùng sử dụng JDK 21. `docker version` phải hiển thị được cả **Client** và **Server**. Chỉ hiện phiên bản Docker CLI chưa đủ để xác nhận Docker Engine đang chạy.

Các bước tiếp theo chạy trực tiếp bằng PowerShell trên Windows. Nếu chọn làm việc hoàn toàn trong WSL, cài JDK/Maven bên trong WSL, bật Docker Desktop WSL Integration và làm theo nhánh Linux; giữ mã nguồn và các công cụ trong cùng môi trường đó.

### 2.2. macOS — Intel và Apple Silicon

**Bước 1 — Cài JDK 21**

Tải **JDK 21 dạng `.pkg`** từ [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21), chọn macOS:

- Apple Silicon, như M1/M2/M3/M4: chọn **aarch64 / ARM64**.
- Mac Intel: chọn **x64**.

Mở file `.pkg` và hoàn tất [cài đặt](https://adoptium.net/installation/macOS/). Sau đó chọn JDK cho terminal hiện tại:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

Để giữ cấu hình khi mở terminal mới, thêm hai dòng trên vào `~/.zshrc` nếu dùng zsh, hoặc file cấu hình tương ứng của shell đang dùng.

**Bước 2 — Cài Git và Maven**

Nếu chưa có Homebrew, cài theo [trang chính thức](https://brew.sh/) và thực hiện phần **Next steps** của bộ cài để thêm `brew` vào PATH. Sau đó chạy:

```bash
brew install git maven
```

Nếu Maven cài thêm JDK khác, vẫn giữ `JAVA_HOME` trỏ tới JDK 21 và kiểm tra lại bằng `mvn -version`.

**Bước 3 — Cài Docker Desktop**

Tải [Docker Desktop cho macOS](https://docs.docker.com/desktop/setup/install/mac-install/), chọn đúng bản **Apple Silicon** hoặc **Intel**. Mở `.dmg`, kéo Docker vào Applications, mở Docker và chờ Engine sẵn sàng.

**Bước 4 — Kiểm tra**

```bash
git --version
java -version
javac -version
mvn -version
docker version
docker compose version
```

Trên Apple Silicon, dùng JDK ARM64 và terminal chạy native để Maven lấy JavaFX đúng kiến trúc. Build client trên chính máy Mac; không dùng lại JAR client đã đóng gói trên Windows.

### 2.3. Linux — ví dụ Ubuntu 24.04 LTS

**Bước 1 — Cài Git, JDK và Maven**

```bash
sudo apt update
sudo apt install -y git openjdk-21-jdk maven ca-certificates curl
```

Nếu máy có nhiều JDK, chọn Java 21 cho cả hai lệnh:

```bash
sudo update-alternatives --config java
sudo update-alternatives --config javac
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
export PATH="$JAVA_HOME/bin:$PATH"
```

Có thể lưu hai dòng `export` vào `~/.bashrc` để dùng ở các terminal sau.

**Bước 2 — Cài Docker Engine và Compose plugin**

Với máy Ubuntu mới chưa cài Docker, thêm repository chính thức và cài các gói sau theo [Docker Docs](https://docs.docker.com/engine/install/ubuntu/):

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources > /dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

**Đăng xuất rồi đăng nhập lại** để áp dụng nhóm `docker`. Các lệnh chạy dự án sau đó dùng tài khoản thường, không chạy server bằng `sudo`. Nếu máy đã cài Docker từ nguồn khác, xử lý gói xung đột theo tài liệu Docker trước khi cài; Debian/Fedora và các bản phân phối khác dùng [hướng dẫn đúng hệ điều hành](https://docs.docker.com/engine/install/).

**Bước 3 — Kiểm tra**

```bash
java -version
javac -version
mvn -version
docker version
docker compose version
docker run --rm hello-world
```

Client JavaFX cần phiên desktop. Trên máy Linux chỉ có SSH hoặc CI không có display, chạy server ở đó và chạy client trên máy có giao diện. Nếu thiếu thư viện GTK khi mở client trên Ubuntu desktop:

```bash
sudo apt install -y libgtk-3-0
```

<a id="source"></a>
## 3. Lấy mã nguồn

Các lệnh này dùng được trong PowerShell và Terminal:

```text
git clone https://github.com/sphieu01/ltm_code_judge.git
cd ltm_code_judge
```

Nếu repository yêu cầu quyền truy cập, dùng tài khoản đã được cấp quyền. Cũng có thể tải ZIP, giải nén rồi mở terminal tại thư mục chứa `README.md`, `client/`, `server/` và `docker-compose.yml`.

Chọn thư mục mà tài khoản hiện tại được phép ghi và Docker được phép chia sẻ. Khi mới setup, nên dùng đường dẫn ngắn, ít ký tự đặc biệt để dễ xử lý lỗi mount.

**Repository không có `pom.xml` tổng ở root và không có Maven Wrapper.** Build từng module bằng `mvn -f server/pom.xml ...` hoặc `mvn -f client/pom.xml ...`; không chạy `mvn package` trực tiếp tại root mà thiếu `-f`.

<a id="infrastructure"></a>
## 4. Chuẩn bị PostgreSQL và Docker sandbox

### 4.1. Tạo PostgreSQL local riêng

Chạy tại root repository. Lệnh được viết trên một dòng để dùng được trên cả ba hệ điều hành:

```text
docker run -d --name ltm-postgres --restart unless-stopped -e POSTGRES_DB=ltm_code_judge -e POSTGRES_USER=ltm_user -e POSTGRES_PASSWORD=ltm_local_password -p 127.0.0.1:5432:5432 -v ltm_postgres_data:/var/lib/postgresql/data postgres:16
```

Đây là database mới dành riêng cho lần setup này. Dữ liệu được giữ trong Docker volume `ltm_postgres_data`; tên user, database và mật khẩu trong các bước sau phải khớp với lệnh trên. Cấu hình image tham khảo [PostgreSQL Docker Official Image](https://hub.docker.com/_/postgres).

Kiểm tra database đã sẵn sàng:

```text
docker exec ltm-postgres pg_isready -U ltm_user -d ltm_code_judge
```

Chỉ tiếp tục khi thấy `accepting connections`. Nếu chưa sẵn sàng, đợi vài giây rồi chạy lại. Xem log khi cần:

```text
docker logs ltm-postgres
```

Nếu máy đã dùng cổng 5432, đổi phần publish thành `127.0.0.1:5433:5432` và dùng **5433** trong `DB_URL` ở bước 5. Cổng bên trong container vẫn là 5432.

Nếu container `ltm-postgres` đã tồn tại từ lần setup trước, dùng `docker start ltm-postgres`; không tạo lại và không chạy lại schema trên database đã có dữ liệu.

### 4.2. Build image chấm bài

Image **server** và image **sandbox chấm bài** là hai image khác nhau. Server chỉ bật Docker sandbox khi tìm thấy đúng tên **`ltm-judge-runner:latest`** và gọi được Docker Engine.

**Windows — PowerShell:**

```powershell
docker build -t ltm-judge-runner:latest ./server/docker
docker image inspect ltm-judge-runner:latest
```

**macOS/Linux — Terminal:**

Build image cơ sở rồi đặt UID/GID trùng với tài khoản sẽ chạy server để container biên dịch ghi được vào thư mục bind mount:

```bash
docker build -t ltm-judge-runner:base ./server/docker
printf 'FROM ltm-judge-runner:base\nUSER %s:%s\n' "$(id -u)" "$(id -g)" | docker build -t ltm-judge-runner:latest -
docker image inspect ltm-judge-runner:latest
```

Thực hiện bằng tài khoản thường. Dockerfile gốc dùng user `sandbox`; trên filesystem có kiểm tra quyền Unix, user này có thể khác chủ sở hữu thư mục bài nộp. Image dẫn xuất phía trên giữ bộ công cụ biên dịch và đổi người dùng thực thi cho phù hợp máy local.

Kiểm tra công cụ trong image, dùng được trên cả ba hệ điều hành:

```text
docker run --rm ltm-judge-runner:latest javac -version
docker run --rm ltm-judge-runner:latest g++ --version
docker run --rm ltm-judge-runner:latest python3 --version
```

Lần build đầu có thể mất vài phút. Giữ Docker Engine chạy trong suốt thời gian chấm bài.

Khi không tìm thấy Docker hoặc image, server **tự chuyển sang chạy process trực tiếp trên máy chủ**. Khi đó máy chủ cần `java`, `javac`, `gcc`, `g++` và `python3` hoặc `python` trong PATH tùy ngôn ngữ cần chấm. Chỉ dùng fallback để thử mã tin cậy; nó không cung cấp mức cô lập tương đương container. Máy chỉ chạy client không cần các compiler này.

<a id="server"></a>
## 5. Build và khởi tạo server lần đầu

**Thứ tự cho database trống: `schema.sql` → chạy server lần đầu → dừng server → nạp `seed.sql` → chạy server lần hai.**

Thứ tự này cần thiết với mã hiện tại: `schema.sql` tạo các bảng nền tảng, còn repository Java tạo bảng học phần/lớp/bài kiểm tra khi khởi động. `seed.sql` sử dụng các bảng đó. Ngoài ra, `SubmissionRepository` được khởi tạo trước bảng `assessments`, nên cần lần chạy thứ hai để hoàn tất các cột phục vụ nộp bài.

### 5.1. Tạo schema nền tảng — chỉ làm một lần trên database trống

Tại root repository:

```text
docker cp server/src/main/resources/schema.sql ltm-postgres:/tmp/schema.sql
docker exec ltm-postgres psql -v ON_ERROR_STOP=1 -U ltm_user -d ltm_code_judge -f /tmp/schema.sql
```

Chỉ tiếp tục nếu lệnh SQL thành công. Cách copy file rồi chạy `psql -f` giữ nguyên UTF-8 và tránh khác biệt chuyển hướng input giữa PowerShell và bash.

`schema.sql` có các lệnh `CREATE TYPE`/`CREATE TABLE` không hỗ trợ chạy lặp toàn bộ. Các file trong `resources/migrations/` dành cho schema cũ có điều kiện riêng; **không chạy máy móc toàn bộ migration lên database mới**. Đặc biệt migration `001` tham chiếu các cột `class_id` của phiên bản cũ.

### 5.2. Build server

```text
mvn -f server/pom.xml clean package -DskipTests
```

Đợi `BUILD SUCCESS`. JAR thực thi được tạo tại:

```text
server/target/code-grading-server-1.0.0.jar
```

`-DskipTests` giúp setup ban đầu không phụ thuộc bộ kiểm thử compiler/GUI; đây là bước build, chưa phải xác nhận toàn bộ chức năng đã chạy đúng.

### 5.3. Đặt biến môi trường cho server

Chọn đúng khối lệnh cho hệ điều hành, chạy trong **terminal sẽ chạy server**.

**Windows — PowerShell:**

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/ltm_code_judge'
$env:DB_USER = 'ltm_user'
$env:DB_PASSWORD = 'ltm_local_password'
$env:SERVER_PORT = '9999'
$env:JUDGE_WORKER_COUNT = '4'
$env:JWT_SECRET = 'local-dev-only-change-this-secret-at-least-32-characters'
```

**macOS/Linux — Terminal:**

```bash
export DB_URL='jdbc:postgresql://localhost:5432/ltm_code_judge'
export DB_USER='ltm_user'
export DB_PASSWORD='ltm_local_password'
export SERVER_PORT='9999'
export JUDGE_WORKER_COUNT='4'
export JWT_SECRET='local-dev-only-change-this-secret-at-least-32-characters'
```

Các biến này chỉ có hiệu lực trong terminal hiện tại và process con của nó. Mở terminal mới thì cần đặt lại. Với môi trường dùng chung, thay JWT secret bằng giá trị ngẫu nhiên riêng có ít nhất 32 byte và dùng thông tin database được cấp.

Server Java **không tự đọc `.env`**. Việc tạo file `.env` không thay thế các lệnh `export`/`$env:` hoặc phần environment của Run Configuration trong IDE.

### 5.4. Chạy server lần đầu để khởi tạo các bảng còn lại

Trong terminal vừa đặt biến môi trường, tại root repository:

```text
java -jar server/target/code-grading-server-1.0.0.jar
```

Chờ các dòng log:

```text
Academic schema verified successfully.
Server started on port 9999 (max connections: 20)
Waiting for connections...
```

Với database mới, lần này có thể có thông báo thiếu `classes` trong bước khởi tạo problem hoặc thiếu `assessments` trong bước khởi tạo submission. Các repository phía sau tạo những bảng này; **chưa thử nộp bài ở lần chạy đầu**. Nếu xuất hiện lỗi kết nối database, phải sửa lỗi kết nối trước khi tiếp tục.

Nhấn **Ctrl+C** và chờ server dừng hoàn toàn. Giữ terminal này để dùng lại các biến môi trường.

### 5.5. Nạp dữ liệu mẫu — chỉ làm một lần

Trong terminal tại root, sau khi server đã dừng:

```text
docker cp server/src/main/resources/seed.sql ltm-postgres:/tmp/seed.sql
docker exec ltm-postgres psql -v ON_ERROR_STOP=1 -U ltm_user -d ltm_code_judge -f /tmp/seed.sql
```

Seed tạo các bài: **Hello World**, **Tổng Hai Số**, **Kiểm Tra Số Nguyên Tố**, **Sắp Xếp Mảng**, **Fibonacci Mod** và test case tương ứng. Học phần/lớp và tài khoản mặc định đã được server tạo ở bước trước.

**Không chạy lại seed ở mỗi lần mở dự án.** Phần bài tập không chống trùng; chạy lại có thể tạo bài trùng tên rồi gây lỗi truy vấn test case. Nếu lệnh seed dừng giữa chừng, kiểm tra dữ liệu trước khi nạp lại.

### 5.6. Chạy server lần hai và giữ server hoạt động

Trong terminal vẫn còn các biến ở bước 5.3:

```text
java -jar server/target/code-grading-server-1.0.0.jar
```

Lần này bảng `assessments` đã tồn tại để `SubmissionRepository` bổ sung `is_zip` và các index cần thiết. Kiểm tra log có **`Sandbox Execution Mode: DOCKER CONTAINER [Isolated]`** nếu đã build image và bật Docker.

Có thể mở terminal thứ hai tại root để xác nhận schema nộp bài:

```text
docker exec ltm-postgres psql -U ltm_user -d ltm_code_judge -c "SELECT column_name FROM information_schema.columns WHERE table_name='submissions' AND column_name IN ('assessment_id','is_zip');"
```

Kết quả cần có cả `assessment_id` và `is_zip`. Kiểm tra số lượng bài/test:

```text
docker exec ltm-postgres psql -U ltm_user -d ltm_code_judge -c "SELECT (SELECT count(*) FROM problems) AS problems, (SELECT count(*) FROM test_cases) AS test_cases;"
```

Với database mới chỉ nạp seed một lần, kỳ vọng **5 bài** và **18 test case**.

**Không dùng `run-server.bat` cho quy trình local này:** script hiện ghi đè thông tin database bằng cấu hình từ xa và tự `taskkill` tiến trình đang giữ cổng 9999. Lệnh Maven/Java phía trên cho phép dùng đúng database vừa tạo mà không cần sửa script.

<a id="client"></a>
## 6. Chạy client

Mở **terminal mới**, vào root repository. Tạo file **`client/config.properties`** với nội dung:

```properties
server.host=localhost
server.port=9999
```

Có file tham khảo `client/config.properties.example`, nhưng file đó hiện bật sẵn một endpoint ngrok. Nếu copy file mẫu, phải thay lại host/port như trên khi chạy local. `config.properties` đã được `.gitignore` bỏ qua.

Chạy client, cùng một lệnh trên Windows, macOS và Linux:

```text
mvn -f client/pom.xml clean compile javafx:run
```

Đợi cửa sổ **Auto Grader** hiện ra. Giữ terminal này mở khi dùng ứng dụng. Giao diện đăng nhập hiện chỉ hiển thị tài khoản/mật khẩu; cấu hình host/port lấy từ `ClientConfig`, nên cần sửa file hoặc biến môi trường **trước khi mở client**.

Các biến môi trường có thể ghi đè file properties. Nếu đã từng kết nối server khác, kiểm tra `SERVER_HOST`, `SERVER_PORT`, `LTM_SERVER_HOST`, `LTM_SERVER_PORT` trong terminal/IDE, hoặc đặt rõ:

**PowerShell:**

```powershell
$env:SERVER_HOST = 'localhost'
$env:SERVER_PORT = '9999'
mvn -f client/pom.xml clean compile javafx:run
```

**macOS/Linux:**

```bash
export SERVER_HOST='localhost'
export SERVER_PORT='9999'
mvn -f client/pom.xml clean compile javafx:run
```

Khi sửa mã nguồn hoặc cấu hình, đóng cửa sổ client và chạy lại lệnh. Có thể mở thêm client ở terminal khác để thử nhiều người dùng; dùng các tài khoản khác nhau vì server có cơ chế đăng nhập một phiên cho một tài khoản.

<a id="first-run"></a>
## 7. Đăng nhập và kiểm tra toàn bộ luồng nộp bài

### 7.1. Tài khoản giáo viên ban đầu

Với database mới đã làm đúng thứ tự bước 5, server tự tạo:

| Tên đăng nhập | Mật khẩu ban đầu | Vai trò |
| --- | --- | --- |
| `teacher01` | `password123` | Giáo viên |
| `admin` | `password123` | Giáo viên |

Nếu tài khoản đã tồn tại, server giữ mật khẩu hiện có; bảng trên không đặt lại mật khẩu của database cũ. Sau khi đăng nhập môi trường dùng chung, đổi mật khẩu qua menu tài khoản.

Đăng nhập bằng **`teacher01`** để thử dữ liệu mẫu. File seed còn chứa hash tài khoản có chú thích placeholder; việc khởi động server **trước khi seed** giúp tài khoản thật đã được tạo và được giữ nguyên bởi `ON CONFLICT DO NOTHING`.

### 7.2. Cấp tài khoản sinh viên và giao bài

1. Trong giao diện giáo viên, vào danh sách **Lớp tín chỉ** rồi mở lớp mẫu, ví dụ `CPP_01`.
2. Ở tab **Sinh viên**, chọn **Nhập file thông tin sinh viên** và chọn `sample-data/students-list-1.csv`. Kiểm tra lớp đích, danh sách xem trước rồi xác nhận import.
3. File CSV phải lưu UTF-8; hai cột mẫu là `username,fullName`. Tài khoản sinh viên mới được tạo với **mật khẩu ban đầu bằng chính username**. Ví dụ: `B23DCCN001` / `B23DCCN001`.
4. Ở tab **Bài tập của lớp**, chọn **Gán bài tập vào lớp**, chọn **Tổng Hai Số** và xác nhận. Nếu bài chưa bật, bật bài trong phần quản lý bài tập.
5. Đăng xuất tài khoản giáo viên hoặc mở client thứ hai; đăng nhập sinh viên vừa import.

Client hiện không có màn hình tự đăng ký sinh viên. Giáo viên cấp tài khoản bằng import danh sách. Seed tạo ngân hàng bài tập nhưng **không tự gán các bài này vào từng lớp**.

### 7.3. Nộp một bài để xác nhận setup thành công

1. Với sinh viên thuộc lớp C++, chọn bài **Tổng Hai Số**, chọn ngôn ngữ **C++**.
2. Tải file `test_codes/cpp/a_plus_b_ac.cpp`, kiểm tra nội dung rồi nhấn **Nộp bài**.
3. Theo dõi trạng thái `PENDING` → `JUDGING` → kết quả. Với đúng đề và đủ toolchain, kỳ vọng `AC`, **5/5 test** và **100 điểm**.
4. Mở **Lịch sử nộp bài** để xem lại kết quả, thời gian và mã nguồn.
5. Thử `test_codes/cpp/test_wa.cpp`, `test_tle.cpp`, `test_ce.cpp`, `test_re.cpp` để kiểm tra các kết quả WA/TLE/CE/RE.

Nếu thử Java hoặc Python, import sinh viên vào lớp `JAVA_01`/`PY_01`, gán bài cho lớp và chọn file tương ứng `test_codes/java/APlusB_AC.java` hoặc `test_codes/python/a_plus_b_ac.py`. Ngôn ngữ được phép phụ thuộc lớp/học phần.

Để thử kiểm tra Java OOP bằng nhiều file `.java` trong ZIP, xem [mẫu Student OOP](test_codes/java/student_oop/README.md) và [mẫu Book OOP](test_codes/java/book_oop/README.md); cần tạo bài có đặc tả OOP phù hợp, không nộp các ZIP này vào bài Tổng Hai Số.

Nếu thử bài kiểm tra có thời gian, tạo và công bố bài kiểm tra trong lớp, chọn đúng khoảng thời gian và danh sách đề. Sinh viên phải thuộc lớp và bắt đầu lượt thi. Đồng bộ giờ máy server/client khi kiểm tra lịch thi. Một số phần dò ứng dụng AI sử dụng công cụ/tiến trình Windows; không nên mặc định các chức năng giám sát hệ điều hành hoạt động tương đương trên macOS/Linux.

<a id="remote-client"></a>
## 8. Chạy client với server của nhóm, kết nối LAN

### 8.1. Máy chỉ chạy client

Chỉ cần Git, JDK và Maven để chạy client từ source, cùng môi trường desktop. **Không cần cài PostgreSQL, Docker hoặc MinIO trên máy client.**

1. Lấy host/IP và cổng TCP hiện tại từ người chạy server.
2. Clone source và đặt cấu hình trong `client/config.properties`, ví dụ:

```properties
server.host=192.168.1.10
server.port=9999
```

3. Tại root repository, chạy:

```text
mvn -f client/pom.xml clean compile javafx:run
```

Nhập host dạng tên miền/IP thuần, **không thêm `http://`, `https://` hoặc đường dẫn**. Đây là TCP socket, không phải website. Nếu dùng tunnel, cần endpoint **TCP**; endpoint ngrok ví dụ trong repository có thể đã thay đổi, hãy lấy địa chỉ đang hoạt động.

### 8.2. Kết nối giữa các máy trong cùng mạng

1. Máy server chạy PostgreSQL, sandbox và server như hướng dẫn local.
2. Xác định IP LAN của máy server: `ipconfig` trên Windows, phần Network Settings trên macOS hoặc `ip addr` trên Linux.
3. Cho phép inbound TCP cổng `9999` hoặc cổng đã đặt trong `SERVER_PORT` trên firewall máy server. Với Windows Firewall, cho phép Java trên mạng Private khi được hỏi.
4. Các máy client đặt `server.host` thành IP LAN đó và `server.port` khớp server. `localhost` trên máy client luôn trỏ về chính máy client.
5. Kiểm tra đường mạng trước khi đăng nhập:

**Windows:**

```powershell
Test-NetConnection 192.168.1.10 -Port 9999
```

**macOS/Linux, nếu đã có `nc`:**

```bash
nc -vz 192.168.1.10 9999
```

Thay IP ví dụ bằng IP thật. Chỉ cần client truy cập cổng TCP của server; database và MinIO được server truy cập trực tiếp.

<a id="minio"></a>
## 9. MinIO và Docker Compose hiện có

### 9.1. Bật MinIO tùy chọn

Hệ thống vẫn chấm và lưu bài vào PostgreSQL khi MinIO không khả dụng. Nếu muốn lưu thêm file bài nộp vào object storage, tại root repository chạy **chỉ service MinIO**:

```text
docker compose up -d --no-deps minio
docker compose ps minio
docker compose logs --tail 50 minio
```

Compose sử dụng image/tag MinIO được ghi trong `docker-compose.yml`. Nếu registry không tải được tag đó, có thể tiếp tục setup không có MinIO rồi xử lý storage sau.

Đợi service chạy, mở [MinIO Console local](http://localhost:9001), đăng nhập `minioadmin` / `minioadmin`. Console dùng cổng **9001**; endpoint API mà server cần là **`http://localhost:9000`**.

Nếu dùng giá trị mặc định, không cần đặt thêm biến. Muốn đặt rõ, thêm vào terminal server trước khi khởi động lại:

**PowerShell:**

```powershell
$env:MINIO_ENDPOINT = 'http://localhost:9000'
$env:MINIO_ACCESS_KEY = 'minioadmin'
$env:MINIO_SECRET_KEY = 'minioadmin'
$env:MINIO_BUCKET = 'ltm-submissions'
```

**macOS/Linux:**

```bash
export MINIO_ENDPOINT='http://localhost:9000'
export MINIO_ACCESS_KEY='minioadmin'
export MINIO_SECRET_KEY='minioadmin'
export MINIO_BUCKET='ltm-submissions'
```

Server tự tạo bucket nếu cần. Khi MinIO đã online, **khởi động lại server** để kết nối lại và kích hoạt đồng bộ bài cũ. Dùng thông tin đăng nhập riêng khi đưa storage lên môi trường dùng chung.

### 9.2. Vì sao không bắt đầu bằng `docker compose up -d --build`?

Compose hiện chỉ định nghĩa **server và MinIO**, không có PostgreSQL và không tự chạy schema/seed. Service server đang có cấu hình database từ xa ghi trực tiếp trong file; biến `DB_URL` đặt ở terminal cũng không tự thay thế giá trị cố định đó.

Ngoài ra, image `server/Dockerfile` chứa compiler để chấm bằng local process trong container server, nhưng không cài Docker CLI/kết nối Docker daemon để tạo các container sandbox riêng. Chỉ build `ltm-judge-runner` trên host không làm service server đó tự dùng được Docker sandbox.

Quy trình trong README vì vậy chạy **JVM server trên host + PostgreSQL trong Docker + sandbox riêng**, và chỉ dùng Compose để bật MinIO tùy chọn. Nếu chuyển toàn bộ server sang Compose, cần chuẩn bị database/schema riêng, cấu hình DB/JWT của bạn và xác định rõ chế độ sandbox trước khi chạy.

<a id="configuration"></a>
## 10. Tra cứu cấu hình

### 10.1. Server — biến môi trường

| Biến | Giá trị mặc định trong code | Ý nghĩa |
| --- | --- | --- |
| `DB_URL` | Placeholder Neon | JDBC URL của PostgreSQL; bắt buộc cấu hình thực tế |
| `DB_USER` | Placeholder | User có quyền đọc/ghi và tạo/sửa bảng trong database dự án |
| `DB_PASSWORD` | Placeholder | Mật khẩu database |
| `SERVER_PORT` | `9999` | Cổng TCP lắng nghe |
| `CONNECTION_POOL_SIZE` | `20` | Số thread xử lý kết nối client; không phải pool database |
| `SOCKET_TIMEOUT_MS` | `300000` | Socket read timeout, đơn vị ms |
| `JUDGE_WORKER_COUNT` | `4` | Số worker chấm song song |
| `JUDGE_QUEUE_CAPACITY` | `100` | Sức chứa hàng đợi chấm |
| `JWT_SECRET` | Có giá trị fallback trong source | Khóa ký JWT, đặt giá trị riêng đủ dài |
| `MINIO_ENDPOINT` | `http://localhost:9000` | API object storage |
| `MINIO_ACCESS_KEY` | `minioadmin` | Access key MinIO |
| `MINIO_SECRET_KEY` | `minioadmin` | Secret key MinIO |
| `MINIO_BUCKET` | `ltm-submissions` | Bucket lưu bài |

HikariCP database pool hiện được cấu hình tối đa **10 kết nối**, tối thiểu **2 idle** trong `DatabaseConfig`; không điều khiển bằng `CONNECTION_POOL_SIZE`.

Nếu dùng PostgreSQL/Neon có sẵn thay cho container local, tạo database/schema riêng rồi thực hiện cùng thứ tự khởi tạo ở bước 5 bằng SQL editor hoặc `psql` kết nối tới database đó. Ví dụ định dạng JDBC cho Neon là `jdbc:postgresql://<host>/<database>?sslmode=require`; thay user/password bằng thông tin của bạn. Không dùng database chung để thử nạp schema/seed mới.

### 10.2. Client — thứ tự ưu tiên cấu hình

1. Java system properties `-Dserver.host`, `-Dserver.port`.
2. Biến môi trường `SERVER_HOST`, `SERVER_PORT`; nếu không có, dùng `LTM_SERVER_HOST`, `LTM_SERVER_PORT`.
3. File properties, tìm theo thứ tự: `config.properties`, `client.properties`, `client/config.properties`, `client/client.properties`, tính từ thư mục làm việc.
4. Mặc định `localhost:9999`.

Ví dụ khi chạy client JAR trực tiếp từ root:

```text
java -Dserver.host=localhost -Dserver.port=9999 -jar client/target/grading-client-1.0-SNAPSHOT.jar
```

Lệnh trên cần build client JAR trước theo mục 11. Đối với `java -jar`, đặt `-D...` **trước** `-jar`.

<a id="development"></a>
## 11. Kiểm thử, đóng gói và sử dụng IDE

### 11.1. Chạy bộ kiểm thử

Sau khi chuẩn bị compiler/sandbox và môi trường desktop, chạy từng module từ root:

```text
mvn -f server/pom.xml test
mvn -f client/pom.xml test
```

Bộ test server có `ProductionJudgeIntegrationTest` thực sự biên dịch/chạy C, C++, Python, Java; cần toolchain trên host hoặc image sandbox hoạt động. Test OOP cũng có thể gọi `javac` trên host. Bộ test client có khởi tạo JavaFX nên cần display. Một số test Book OOP bỏ qua khi thiếu fixture local không được đưa vào repository; đọc dòng tổng kết `Tests run / Failures / Errors / Skipped`, không chỉ nhìn `BUILD SUCCESS`.

### 11.2. Tạo JAR để chạy trực tiếp

```text
mvn -f server/pom.xml clean package -DskipTests
mvn -f client/pom.xml clean package -DskipTests
```

Sau đó, ở hai terminal riêng và với cấu hình phù hợp:

```text
java -jar server/target/code-grading-server-1.0.0.jar
```

```text
java -jar client/target/grading-client-1.0-SNAPSHOT.jar
```

Hai JAR đã gộp dependency bằng Maven Shade. Server vẫn cần database, cấu hình và môi trường chấm bài. **Client JAR có thư viện native JavaFX theo OS/kiến trúc lúc build**; build riêng cho Windows, macOS Intel, macOS ARM64 hoặc Linux tương ứng.

Trên Windows có `package-client.bat` tạo `release/AutoGrader-Client.zip`. Trước khi chia sẻ bản này, kiểm tra cấu hình endpoint mà script tạo; không giả định địa chỉ tunnel có sẵn trong script là server local hoặc endpoint còn hoạt động.

`run-client.bat` ở root là tiện ích Windows chuyển vào module client để build và mở JAR. Khi cần xem lỗi khởi động, ưu tiên lệnh Maven/`java -jar` trong terminal; script mở bằng `javaw` nên không hiện log console của ứng dụng. Các file `.bat` không chạy trực tiếp trên macOS/Linux.

### 11.3. Mở bằng IDE

- Import **`server/pom.xml`** và **`client/pom.xml`** dưới dạng hai Maven project.
- Chọn Project SDK/Maven runner JDK 21, bật encoding UTF-8 và để Maven tải dependency.
- Server entry point: `com.grading.server.ServerApp`. Đặt biến DB/JWT trong Run Configuration và working directory tại root repository để log/temp dễ tìm.
- Client có thể chạy bằng Maven goal `compile javafx:run`; khi chạy JAR, manifest dùng `com.grading.client.Launcher`.
- Cấu hình environment của terminal không tự được chuyển sang mọi Run Configuration của IDE; đặt lại nếu chạy bằng nút Run.

<a id="restart"></a>
## 12. Dừng và chạy lại dự án

**Kết thúc phiên làm việc:** đóng các cửa sổ client, nhấn `Ctrl+C` tại terminal server và đợi shutdown hoàn tất. Nếu muốn dừng dịch vụ local:

```text
docker stop ltm-postgres
docker compose stop minio
```

**Những lần chạy sau:**

1. Mở Docker Desktop hoặc bảo đảm Docker Engine đang chạy.
2. Chạy `docker start ltm-postgres`, chờ `pg_isready` thành công.
3. Nếu dùng MinIO, chạy `docker compose up -d --no-deps minio`.
4. Đặt lại biến môi trường server trong terminal hiện tại.
5. Build lại nếu source đã thay đổi, rồi chạy `java -jar server/target/code-grading-server-1.0.0.jar`.
6. Mở terminal khác và chạy `mvn -f client/pom.xml clean compile javafx:run`.

**Không cần khởi tạo hai lần nữa và không chạy lại `schema.sql`/`seed.sql`** khi dùng database đã hoàn tất setup. Việc dừng container không xóa Docker volume chứa database hoặc MinIO. Không xóa volume nếu muốn giữ tài khoản, bài tập và lịch sử.

<a id="troubleshooting"></a>
## 13. Xử lý lỗi thường gặp

| Triệu chứng | Cách kiểm tra và xử lý |
| --- | --- |
| `mvn` / `java` không được nhận diện | Mở terminal mới sau khi cài; kiểm tra PATH, `JAVA_HOME`, `mvn -version`. `JAVA_HOME` trỏ tới JDK, không trỏ tới `bin` hoặc JRE. |
| `The goal ... requires a project ... no POM` | Đang đứng ở root nhưng thiếu `-f`; dùng `mvn -f server/pom.xml ...` hoặc `mvn -f client/pom.xml ...`. |
| `release version 17 not supported`, `invalid target release` | Maven đang dùng JDK cũ; kiểm tra Java home trong `mvn -version` và chọn JDK 21. |
| `UnsupportedClassVersionError` lúc chấm OOP | JVM server cũ hơn compiler trong image sandbox; dùng JDK 21 cho server với image OpenJDK 21 hiện có. |
| Docker chỉ hiện Client, báo không kết nối daemon | Mở Docker Desktop/chạy Docker Engine; kiểm tra `docker version`, đúng context và Linux containers. |
| Linux báo permission denied với Docker socket | Đăng nhập lại sau khi thêm nhóm `docker`; kiểm tra `docker info` bằng đúng tài khoản chạy server. |
| Container `ltm-postgres` đã tồn tại | Dùng `docker start ltm-postgres`; kiểm tra database cũ trước khi chạy SQL. |
| `Connection refused`, `Failed to initialize ... pool`, sai mật khẩu DB | Kiểm tra container, `pg_isready`, cổng publish và ba biến `DB_*` trong đúng terminal server. DatabaseConfig mặc định vẫn là placeholder nếu chưa đặt biến. |
| `relation "courses" does not exist` khi seed | Đã seed trước khi server tạo bảng; thực hiện bước khởi động đầu, kiểm tra trạng thái dữ liệu rồi mới hoàn tất seed. |
| Thiếu `assessments`/`is_zip`, lỗi nộp bài sau lần chạy đầu | Dừng và chạy server lần hai theo bước 5; truy vấn xác nhận cả hai cột `assessment_id`, `is_zip`. |
| `already exists` khi chạy schema hoặc bài tập bị trùng | Schema/seed đã từng chạy; không nạp tiếp một cách tự động. Kiểm tra database đang kết nối và dữ liệu đã có. |
| `Address already in use`, server không bind được 9999 | Dừng phiên server cũ nếu đó là process của bạn; hoặc đổi `SERVER_PORT` và cấu hình cổng client cho khớp. |
| Client không kết nối được server | Server phải còn chạy. Kiểm tra host/port, biến môi trường ghi đè properties, IP LAN và firewall. Không dùng URL HTTP cho TCP. |
| Không đăng nhập được `teacher01` | Với DB cũ, mật khẩu có thể đã đổi hoặc seed placeholder được nạp trước lúc server tạo tài khoản; không giả định `password123` luôn đúng. Với DB mới, làm đúng thứ tự bước 5. |
| Sinh viên không thấy lớp/bài hoặc không có ngôn ngữ cần chọn | Import đúng lớp, gán bài vào lớp, bật bài và kiểm tra ngôn ngữ của học phần/lớp; seed không tự giao bài. |
| Server báo `LOCAL PROCESS SANDBOX` | Docker hoặc image `ltm-judge-runner:latest` chưa sẵn sàng; kiểm tra `docker info`, `docker image inspect ...` và log server. |
| CE do không tìm thấy `gcc`/`g++`/`python` | Server đang chạy fallback và host thiếu toolchain. Bật Docker sandbox theo bước 4 hoặc cài công cụ tương ứng vào PATH rồi khởi động lại. |
| CE với `Permission denied`, không tạo được `.class`/`solution` trong Docker | Kiểm tra quyền thư mục mount và build image theo UID/GID ở macOS/Linux. Windows/macOS cần cho Docker quyền chia sẻ thư mục chứa repository; Linux có SELinux cần kiểm tra chính sách bind mount của hệ thống. |
| JavaFX báo `UnsatisfiedLinkError`, thiếu Glass/Prism/native library | Build lại client trên đúng OS/kiến trúc bằng JDK phù hợp; không dùng JAR Windows trên Mac/Linux. Kiểm tra thư viện GTK và phiên desktop trên Linux. |
| JavaFX báo `Unable to open DISPLAY` | Đang chạy client/test GUI ở môi trường không có display; chuyển client sang máy desktop hoặc cấu hình display cho môi trường đó. |
| Maven không tải được dependency | Kiểm tra Internet/proxy và cấu hình Maven; lần đầu cần tải thư viện. Có thể thử lại cùng lệnh với `-U` sau khi đã sửa lỗi mạng. |
| MinIO không khả dụng | Kiểm tra service, API cổng 9000 và credential; đây là thành phần tùy chọn. Bật MinIO rồi khởi động lại server để kết nối lại. |

### Chi tiết cách xử lý các lỗi thường gặp khi chạy local

#### 1. Lỗi cổng 9999 bị chiếm dụng (`Address already in use: bind`)
- **Nguyên nhân**: Một tiến trình Server cũ vẫn đang chạy ngầm trên máy.
- **Khắc phục**:
  - Trên Windows: Mở PowerShell và chạy:
    ```powershell
    Get-Process -Id (Get-NetTCPConnection -LocalPort 9999).OwningProcess | Stop-Process -Force
    ```
    *(Hoặc chạy lại `.\run-server.bat`, script sẽ tự động tìm và kill tiến trình giữ port).*
  - Trên Linux / macOS:
    ```bash
    fuser -k 9999/tcp || lsof -ti :9999 | xargs kill -9
    ```

#### 2. Lỗi kết nối Database (`Connection refused` hoặc `Failed to initialize pool`)
- **Nguyên nhân**:
  - Chưa đặt file `.env` vào đúng thư mục gốc dự án.
  - Máy tính chưa có kết nối Internet để gọi tới Cloud Database Neon.
  - Chuỗi kết nối hoặc mật khẩu trong `.env` bị thiếu ký tự.
- **Khắc phục**: Kiểm tra lại kết nối mạng và đối chiếu các thông số `DB_URL`, `DB_USER`, `DB_PASSWORD` trong `.env`.

#### 3. Lỗi khi chấm bài C++ hoặc Python báo `Compile Error` / `Runtime Error`
- **Nguyên nhân**: Máy host chưa cài đặt trình biên dịch `gcc`/`g++` hoặc `python` trong biến môi trường `PATH`.
- **Khắc phục**:
  - Đối với C/C++: Cài đặt MinGW-w64 (Windows) và thêm thư mục `bin` vào biến môi trường `Path`.
  - Đối với Python: Cài đặt Python 3 và tích chọn **"Add Python to PATH"** trong quá trình cài đặt.
  - Sau khi cài, khởi động lại Server để nhận PATH mới.
  - Hoặc bạn có thể thử nghiệm trước bằng bài nộp **Java**, vì máy đã có sẵn JDK nên luôn biên dịch và chạy được ngay.

#### 4. Client không kết nối được Server
- Hãy chắc chắn rằng **Server đã được khởi động trước** và đang ở trạng thái `Waiting for connections...`.
- Đảm bảo trong `client/config.properties` trỏ đúng `server.host=localhost` và `server.port=9999`.

Khi chạy server từ root như tài liệu, log nằm ở **`logs/server.log`**, log cũ quay vòng theo ngày và thư mục bài nộp tạm nằm ở **`temp/judge/`**. Nếu đổi working directory, log được tạo tương đối theo thư mục chạy ứng dụng. Khi báo lỗi cho thành viên khác, gửi lệnh đã chạy và đoạn log liên quan, bỏ mật khẩu/token trước khi chia sẻ.

<a id="structure"></a>
## 14. Cấu trúc thư mục và tài liệu

```text
ltm_code_judge/
├── client/                 JavaFX UI, TCP client, session và giám sát
│   ├── pom.xml
│   ├── config.properties.example
│   └── src/main/           Java, FXML và CSS
├── server/                 TCP server, service, repository và judge engine
│   ├── pom.xml
│   ├── Dockerfile          Image chạy server
│   ├── docker/Dockerfile   Image sandbox ltm-judge-runner
│   └── src/main/resources/
│       ├── schema.sql      Schema nền tảng cho database mới
│       ├── seed.sql        Dữ liệu mẫu, chạy sau khởi tạo bảng học vụ
│       └── migrations/     SQL chuyển đổi từ các schema cũ
├── sample-data/            CSV danh sách sinh viên để import
├── test_codes/             Mã nộp mẫu và ZIP Java OOP
├── docs/                   Kiến trúc, protocol, vận hành và các lưu ý
├── final-project/          Tài liệu báo cáo và yêu cầu học phần
├── docker-compose.yml      Service server và MinIO
├── architecture.md         Thiết kế kiến trúc chi tiết
└── task-division.md         Phân chia công việc
```

Tài liệu đọc tiếp: [Kiến trúc](architecture.md), [mục lục docs](docs/README.md), [giao thức TCP/JSON](docs/03-protocol.md), [bản đồ mã nguồn](docs/05-code-map.md), [mã nộp mẫu](test_codes/README.md) và [phân chia công việc](task-division.md). Một số tài liệu phụ mô tả phiên bản cũ; khi khác nhau về phiên bản/cấu hình, đối chiếu `pom.xml`, Dockerfile và source hiện tại, cùng quy trình setup ở README này.
