# Bộ test Quản lý sách OOP

Lớp `Book` chỉ có hai thuộc tính `private String title` và `private double price`.
Đề yêu cầu hai constructor `Book()` và `Book(String, double)`, getter/setter, kiểm tra dữ liệu và `toString()`.

Đề đầy đủ nằm trong file cục bộ `problem/statement.md`.
Nội dung đề là văn bản thường, không chứa ký hiệu định dạng Markdown.

Đã cài vào DB của dự án với **ID 21**, giáo viên tạo có ID 1, trạng thái hiển thị.
Trong ứng dụng, làm mới kho bài tập và tìm **QUẢN LÝ SÁCH OOP - Constructor, Setter và toString**.

## Bốn bài nộp

Mỗi thư mục có `Main.java`, `Book.java` và một ZIP chứa trực tiếp hai file này ở gốc.
Chọn **đúng bài Quản lý sách OOP** trong ứng dụng, chọn Java và tải ZIP tương ứng.

| Thư mục | ZIP dùng để nộp | Kết quả | Lỗi cố ý |
| --- | --- | --- | --- |
| `ac/` | [submission_ac.zip](ac/submission_ac.zip) | AC, 100 điểm | Đúng toàn bộ yêu cầu |
| `missing_constructor/` | [submission.zip](missing_constructor/submission.zip) | CE, `[OOP_VIOLATION]` | Thiếu constructor không tham số; vẫn có constructor hai tham số nên Main biên dịch và chạy được |
| `invalid_setter/` | [submission.zip](invalid_setter/submission.zip) | CE, `[OOP_VIOLATION]` | `setPrice()` nhận giá âm hoặc vượt giới hạn; chữ ký phương thức vẫn đúng |
| `fake_to_string/` | [submission.zip](fake_to_string/submission.zip) | CE, `[OOP_VIOLATION]` | `toString()` trả chuỗi rỗng; Main dùng getter rồi tự in đúng định dạng để che lỗi |

Ba mẫu lỗi đều biên dịch Java bình thường và in đúng output của cả sáu test I/O.
Chúng bị từ chối bởi kiểm tra OOP trước khi chấm I/O, không phải vì lỗi cú pháp:

- Thiếu constructor: bị bắt ở bước kiểm tra cấu trúc Reflection.
- Setter sai: qua kiểm tra cấu trúc nhưng bị bắt khi gọi setter với giá `-1.0`.
- Hàm hiển thị sai: qua kiểm tra cấu trúc và setter, nhưng bị bắt khi bộ chấm gọi trực tiếp `toString()`.

Mẫu hàm hiển thị cố tình dùng chuỗi rỗng, là lỗi bộ chấm động hiện tại phát hiện được.
Không nên suy ra rằng bộ chấm đã kiểm tra đầy đủ nội dung của mọi `display()` hoặc mọi chuỗi không rỗng do `toString()` trả về.

## Dữ liệu đề bài

Thư mục `problem/` được giữ cục bộ và đã thêm vào `.gitignore`, không được đẩy lên Git.

- `problem/statement.md`: đề tiếng Việt, mô tả từng chữ ký phương thức, giá trị mặc định và input/output.
- `problem/oop_spec.json`: đặc tả OOP dùng khi chấm.
- `problem/test_cases.json`: 1 test mẫu và 5 test ẩn (danh sách rỗng, giá 0, biên trên, số thập phân, tên tiếng Việt).
- `problem/create_problem.json`: payload đầy đủ gồm metadata, đề, đặc tả và test case; không chứa thông tin đăng nhập.

Đề được thêm vào kho bài tập với loại `JAVA_OOP`, trạng thái hiển thị, độ khó 2, thời gian 2000 ms và bộ nhớ 256 MB.
Giáo viên có thể gán bài vào lớp hoặc chọn bài cho bài kiểm tra.

## Chạy kiểm thử tự động

Từ thư mục `server`, chạy:

```powershell
mvn '-Dtest=BookOopSamplesTest,OopReflectionGraderTest' test
```

`BookOopSamplesTest` mở các ZIP thật, kiểm tra chúng khớp mã nguồn, quét bảo mật,
biên dịch bằng `SandboxRunner`, chạy Reflection và kiểm thử động, rồi xác minh output của cả bốn mẫu.
Kiểm thử này không ghi bài nộp vào DB và không làm thay đổi lịch sử của sinh viên.
Trên máy clone mới chưa có dữ liệu `problem/`, bốn kiểm thử mẫu Book sẽ được bỏ qua;
các kiểm thử lõi `OopReflectionGraderTest` vẫn chạy bình thường.

## Thêm đề vào một DB khác

Từ thư mục gốc dự án, sau khi build server:
Các lệnh cài, cập nhật mô tả và đối chiếu yêu cầu đã khôi phục dữ liệu cục bộ trong `problem/`.

```powershell
java '-Dfile.encoding=UTF-8' -cp server/target/code-grading-server-1.0.0.jar test_codes/java/book_oop/InstallProblem.java --inspect
java '-Dfile.encoding=UTF-8' -cp server/target/code-grading-server-1.0.0.jar test_codes/java/book_oop/InstallProblem.java --install
java '-Dfile.encoding=UTF-8' -cp server/target/code-grading-server-1.0.0.jar test_codes/java/book_oop/InstallProblem.java --verify
```

Để đồng bộ riêng nội dung mô tả sau khi chỉnh đề, dùng `--update-description` thay cho `--install`.

Trình cài lấy `DB_URL`, `DB_USER`, `DB_PASSWORD` từ biến môi trường; nếu chưa đặt thì đọc cấu hình cục bộ trong `server/run-server.bat`.
Trình cài chọn giáo viên đang sở hữu một bài OOP, hoặc giáo viên đầu tiên nếu chưa có bài OOP.
Toàn bộ đề và test case được thêm trong một transaction. Nếu đã có bài cùng tiêu đề, trình cài bỏ qua và không ghi đè.
Thông tin đăng nhập không được ghi vào dữ liệu mẫu hoặc log.
