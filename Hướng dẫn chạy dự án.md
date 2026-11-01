# Hướng dẫn chạy dự án Code Judge

---

## 1. Yêu cầu môi trường

Trước khi bắt đầu, hãy đảm bảo máy tính của bạn đã được cài đặt đầy đủ các công cụ sau:

| Công cụ | Phiên bản khuyến nghị | Mục đích sử dụng | Bắt buộc? |
| :--- | :--- | :--- | :--- |
| **Java JDK** | **JDK 21** (x64) | Biên dịch & chạy Server, Client JavaFX, chấm bài Java | **Bắt buộc** |
| **Apache Maven** | **3.9.x** hoặc mới hơn | Quản lý thư viện và build project | **Bắt buộc** |
| **GCC / G++** | MinGW-w64 / GCC 9+ | Biên dịch & chấm code C / C++ (Local Sandbox) | Khuyến nghị |
| **Python** | **Python 3.8+** | Kiểm tra cú pháp & chấm code Python (Local Sandbox) | Khuyến nghị |
| **Internet** | Ổn định | Tải thư viện Maven lần đầu và kết nối **Cloud Database** | **Bắt buộc** |
| **Hệ điều hành Desktop** | Windows 10/11, macOS, Ubuntu | Môi trường có giao diện đồ họa để mở Client JavaFX | **Bắt buộc** |


## 2. Các bước khởi chạy dự án

### Bước 1: Khởi chạy Server

Mở terminal tại thư mục gốc của dự án (`ltm_code_judge/`) và chọn một trong các cách sau:

Chỉ cần nhấp đúp chuột hoặc chạy file sau trong Command Prompt / PowerShell:
```cmd
.\run-server.bat
```

---

### Bước 2: Khởi chạy Client (Giao diện JavaFX)

Mở một **cửa sổ Terminal mới** tại thư mục gốc dự án (`ltm_code_judge/`).

#### 1. Kiểm tra cấu hình kết nối Client:
Mặc định Client sẽ tự động kết nối tới `localhost:9999`. 
Nếu muốn chỉ định rõ ràng, bạn có thể tạo file `client/config.properties` với nội dung:
```properties
server.host=localhost
server.port=9999
```

#### 2. Khởi chạy Client:

Chạy file script:
```cmd
.\run-client.bat
```
---

## 3. Tài khoản đăng nhập

Dữ liệu trên Cloud Database đã được chuẩn bị sẵn các tài khoản và danh sách bài tập mẫu:

### 3.1. Tài khoản mặc định

| Vai trò | Tên đăng nhập | Mật khẩu |
| :--- | :--- | :--- |
| **Giáo viên (Admin)** | `teacher01` | `password123` |
| **Sinh viên** | `B23DCCN001` | `B23DCCN001` |

### 3.2. Thử nghiệm tính năng nộp bài & chấm tự động

Dự án đã chuẩn bị sẵn bộ mã nguồn nộp mẫu trong thư mục `test_codes/`:

---


## 4. Các vấn đề khác 
Vui lòng xem trong file README.md
