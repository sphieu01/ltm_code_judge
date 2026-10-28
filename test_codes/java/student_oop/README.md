# Hướng Dẫn Bộ Test Mã Nguồn Java OOP

Bộ test cho bài toán **QUẢN LÝ SINH VIÊN OOP** bao gồm đầy đủ các kịch bản thực tế:

---

## 1. Mẫu Đạt Chuẩn (Accepted - AC)
Thư mục: `ac/`
- `Main.java`: Chỉ chứa duy nhất `public class Main` xử lý nhập/xuất và logic chạy.
- `Student.java`: Chứa `public class Student` được định nghĩa độc lập trong file riêng, thuộc tính `private` chuẩn đóng gói.
- `submission_ac.zip`: Gói nén chứa cả 2 file trên sẵn sàng nộp lên Client qua nút "Tải lên ZIP".

**Kỳ vọng khi chấm:** **AC (Accepted)**.

---

## 2. Mẫu Vi Phạm Cấu Trúc File Riêng (Single File Violation)
File: `oop_violation/Main.java`
- Chứa cả `class Main` và `class Student` trong cùng một file `Main.java`.
- Sinh viên nộp trực tiếp file này (hoặc paste code vào editor).

**Kỳ vọng khi chấm:** **CE (Compile Error)**.
**Thông báo trả về:**
```text
[OOP_VIOLATION] Class/Interface 'Student' phải được định nghĩa trong file riêng biệt 'Student.java'.
```
(Hoặc nếu nộp có file Main: `[OOP_VIOLATION] File 'Main.java' chỉ được chứa class 'Main'. Lớp/giao diện 'Student' phải được tách ra thành file riêng biệt 'Student.java'.)

---

## 3. Mẫu Vi Phạm Tính Đóng Gói (Encapsulation Violation)
Thư mục: `oop_violation/encapsulation_violation/`
- Đã tách ra 2 file `Main.java` và `Student.java` riêng biệt.
- Tuy nhiên trong `Student.java`, các thuộc tính `name` và `gpa` lại để `public` (vi phạm yêu cầu `private` trong `oop_spec`).
- Có sẵn file nén `submission_encapsulation_violation.zip` để nộp thử.

**Kỳ vọng khi chấm:** **CE (Compile Error)**.
**Thông báo trả về:**
```text
[OOP_VIOLATION] Thuộc tính 'name' trong class 'Student' phải có access modifier là 'private' (vi phạm tính đóng gói Encapsulation).
```
