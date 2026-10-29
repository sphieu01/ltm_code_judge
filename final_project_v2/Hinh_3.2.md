Generate a wide HORIZONTAL flowchart (flowchart LR, strictly left-to-right landscape orientation, wide aspect ratio 16:9, do NOT stack vertically) with Vietnamese labels representing a "Two-Phase Dynamic OOP Reflection Grading Pipeline":

Layout constraint: Arrange all 5 stages horizontally as sequential columns from left to right across the page to ensure a wide panoramic landscape image.

1. [Đầu vào kiểm thử]:
   - "Mã nguồn Java sinh viên (.java)"
   - "Đặc tả cấu trúc OOP (JSON): fields, constructors, methods"

2. [Biên dịch & Nạp Class]:
   - "JavaCompiler biên dịch thành Bytecode (.class)"
   - "Custom ClassLoader nạp class vào bộ nhớ cách ly"

3. [Pha 1: Thẩm định cấu trúc tĩnh (OopReflectionGrader)]:
   - Sử dụng Java Reflection API kiểm tra:
     + "Kiểm tra thuộc tính: Bắt buộc modifier Private và đúng kiểu"
     + "Kiểm tra hàm tạo: Constructor mặc định, đủ tham số, tham số tùy chọn"
     + "Kiểm tra phương thức: Chữ ký hàm, toString(): String, Getters/Setters"
   - Quyết định rẽ nhánh: "Cấu trúc hợp lệ?"
     + Nếu Không hợp lệ -> "Phán quyết: FAILED_STRUCTURE (Báo lỗi vi phạm, dừng chấm ngay)"
     + Nếu Hợp lệ 100% -> Chuyển sang Pha 2

4. [Pha 2: Kiểm thử hành vi động (OopDynamicTester)]:
   - "1. Khởi tạo đối tượng trong Heap: constructor.newInstance(testParams)"
   - "2. Kích hoạt phương thức độc lập: method.invoke(instance, args)"
   - "3. So khớp kết quả trả về: assert actualValue == expectedValue"
   - "4. Giám sát an toàn: Timeout 2 giây & Bắt ngoại lệ Runtime"

5. [Tổng hợp kết quả]:
   - "Tính tổng điểm theo trọng số ca kiểm thử"
   - "Phán quyết cuối cùng: ACCEPTED hoặc WRONG_ANSWER"
