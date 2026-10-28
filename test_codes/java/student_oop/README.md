# Quản lý sinh viên OOP

Hai mẫu cho bài **QUẢN LÝ SINH VIÊN OOP (1792910378)**:

| File nộp | Cấu trúc | Kỳ vọng |
| --- | --- | --- |
| `ac/Main.java` | `Student` có `private String name`, `private double gpa`, constructor `public Student(String, double)` và `public void display()` | AC nếu bộ test theo mô tả đề |
| `oop_violation/Main.java` | Cùng thuật toán và output, nhưng `name`, `gpa` là `public` | CE với `[OOP_VIOLATION]` khi cấu hình OOP yêu cầu hai thuộc tính `private` |

Chọn Java và tải **một file `Main.java`** tương ứng lên client. Mỗi file chứa cả lớp `Main` và `Student`, không cần nộp kèm file khác. Hai mẫu độc lập, không biên dịch chung vì trùng tên lớp.

Đề ghi đọc `n` sinh viên, nhưng ví dụ không có `n`. Cả hai mẫu hỗ trợ hai dạng đầu vào (tên sinh viên là một token, như ví dụ):

```text
NguyenVanA 3.5
```

Output:

```text
Student: NguyenVanA, GPA: 3.5
```

Hoặc đầu vào có `n`:

```text
2
NguyenVanA 3.5
TranThiB 4.0
```

Output:

```text
Student: NguyenVanA, GPA: 3.5
Student: TranThiB, GPA: 4.0
```

Mẫu sai vẫn biên dịch và chạy độc lập ra output đúng. Trên hệ thống chấm, cấu trúc OOP được kiểm tra trước khi chạy testcase; lỗi đóng gói khiến bài bị từ chối dù output đúng. Bài phải được cấu hình `oopSpec` yêu cầu các thuộc tính `private`; chỉ nội dung mô tả không tự tạo ràng buộc chấm.
