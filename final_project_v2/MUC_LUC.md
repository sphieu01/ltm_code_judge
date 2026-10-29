CẤU TRÚC MỤC LỤC BÁO CÁO KỸ THUẬT
PHẦN MỞ ĐẦU
Nhận xét của giảng viên hướng dẫn
Mục lục
Danh mục hình ảnh
Danh mục bảng biểu
TÓM TẮT
TỪ KHÓA
1. TỔNG QUAN
1.1 Bối cảnh và động lực nghiên cứu
a. Thực trạng giảng dạy lập trình và nhu cầu chấm tự động trong môi trường mạng
b. Thách thức về tải đồng thời và an toàn thực thi mã nguồn
1.2 Phát biểu bài toán và phạm vi đề tài
a. Phát biểu bài toán
b. Phạm vi và giới hạn 
1.3 Mục tiêu đề tài
1.4 Đóng góp chính

2. CÁC NGHIÊN CỨU LIÊN QUAN VÀ CƠ SỞ LÝ THUYẾT
2.1 Cơ sở lý thuyết truyền thông mạng và xử lý đồng thời
2.2 Khảo sát các hệ thống chấm mã nguồn hiện nay
a. Các nền tảng quốc tế: Codeforces và LeetCode
b. Hệ thống thực tế tại Học viện: Code PTIT
2.3 Phân tích hạn chế và bất cập thực tế
a. Bế tắc của luồng nhập xuất chuẩn trước bài toán hướng đối tượng và gánh nặng chấm tay
b. Lãng phí tài nguyên và độ trễ từ cơ chế thăm dò HTTP
2.4 Định vị giải pháp đề xuất

3. PHƯƠNG PHÁP LUẬN VÀ THIẾT KẾ HỆ THỐNG
3.1 Định nghĩa bài toán và yêu cầu hệ thống
a. Dữ liệu đầu vào, đầu ra và các ràng buộc kỹ thuật
b. Yêu cầu chức năng của hệ thống
c. Yêu cầu phi chức năng về độ trễ, tính tin cậy và an toàn
3.2 Kiến trúc tổng thể hệ thống
a. Mô hình phân tầng: Trình khách, Máy chủ, Lưu trữ
b. Trách nhiệm kỹ thuật của các phân hệ thành phần
3.3 Thiết kế truyền thông mạng
a. Định dạng thông điệp và cơ chế phân tách khung dòng dữ liệu
b. Tập mã lệnh điều khiển và quy ước truyền nhận
c. Trình tự trao đổi thông điệp xác thực, nộp bài và nhận kết quả
d. Cơ chế duy trì phiên làm việc và xử lý bất thường mạng
3.4 Thiết kế phân hệ xử lý đồng thời và hàng đợi chấm bài
a. Kiến trúc In-memory BlockingQueue kết hợp Worker Pool
b. Luận cứ thiết kế hàng đợi nội bộ thay cho Message Broker cồng kềnh
c. Cơ chế điều phối dòng nộp bài và kiểm soát tràn bộ đệm
3.5 Thiết kế lưu trữ dữ liệu và an toàn hệ thống
a. Thiết kế cơ sở dữ liệu quan hệ lưu trữ thực thể nghiệp vụ
b. Tích hợp kho lưu trữ đối tượng phân tán MinIO cho tệp nguồn lớn
c. Thiết kế cơ chế phòng chống gian lận và xác thực chống giả mạo

4. HIỆN THỰC HÓA VÀ ĐÁNH GIÁ THỰC NGHIỆM
4.1 Hiện thực hóa các thành phần phần mềm cốt lõi
a. Phân hệ máy chủ mạng đa luồng và điều phối kết nối
b. Trình khách đồ họa JavaFX và xử lý phản hồi bất đồng bộ
c. Động cơ chấm bài OOP hai pha sử dụng phản xạ động
d. Cơ chế cách ly và giới hạn tài nguyên an toàn
4.2 Thiết lập môi trường và kịch bản thực nghiệm
a. Môi trường phần cứng, hệ điều hành và nền tảng thực thi
b. Bộ chỉ số đo lường hiệu năng mạng và xử lý
c. Xây dựng 4 kịch bản thực nghiệm từ S1 đến S4
4.3 Kết quả thực nghiệm và đo lường định lượng
a. So sánh thời gian phản hồi và thông lượng giữa mô hình tuần tự và hàng đợi đa luồng
b. Mức độ tiêu thụ tài nguyên CPU, bộ nhớ dưới áp lực tải đồng thời
c. Đánh giá tính chính xác và tỷ lệ bắt lỗi của động cơ chấm OOP
4.4 Phân tích, thảo luận và đánh đổi kỹ thuật
a. Đánh đổi giữa chi phí tài nguyên và mức độ cách ly an toàn
b. Đánh đổi giữa đơn giản hóa kiến trúc mạng và khả năng mở rộng phân tán
c. Phân tích nguyên nhân các trường hợp kiểm thử biên
5. KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN
5.1 Tổng kết kết quả đạt được
5.2 Bảng tổng hợp các đóng góp kỹ thuật và bằng chứng thực nghiệm
5.3 Những hạn chế tồn tại của hệ thống
5.4 Hướng nghiên cứu và phát triển tiếp theo
PHẦN KẾT THÚC
TÀI LIỆU THAM KHẢO
PHỤ LỤC
a. Phụ lục A: Bảng tra cứu chi tiết tập mã lệnh giao thức mạng
b. Phụ lục B: Lược đồ cơ sở dữ liệu quan hệ
c. Phụ lục C: Mẫu đặc tả bài toán OOP và cấu hình ca kiểm thử động
