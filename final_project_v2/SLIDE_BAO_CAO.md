KỊCH BẢN VÀ NỘI DUNG SLIDE BÁO CÁO ĐỒ ÁN
ĐỀ TÀI: HỆ THỐNG CHẤM MÃ NGUỒN TỰ ĐỘNG PHÂN TÁN QUA MẠNG DỰA TRÊN KIẾN TRÚC HÀNG ĐỢI WORKER VÀ THÔNG BÁO ĐẨY QUA GIAO THỨC TCP
Môn học: Lập trình mạng
Quy mô trình bày: 15 Slide (Thời lượng báo cáo 10 - 15 phút)

================================================================================
SLIDE 1: TRANG TIÊU ĐỀ
================================================================================

Tiêu đề đồ án:
HỆ THỐNG CHẤM MÃ NGUỒN TỰ ĐỘNG PHÂN TÁN QUA MẠNG SOCKET TCP
Kiến trúc hàng đợi đa luồng và thông báo đẩy thời gian thực

Thông tin báo cáo:
- Môn học: Lập trình mạng
- Giảng viên hướng dẫn: Bộ môn Mạng máy thanh & Viễn thông
- Sinh viên thực hiện: Nhóm sinh viên thực hiện đồ án
- Nền tảng công nghệ: Java 17 LTS, TCP Socket, JavaFX, PostgreSQL, MinIO S3

Gợi ý thiết kế slide:
- Nền tối hoặc màu xanh đậm trang nhã, phong cách kỹ thuật hiện đại.
- Logo trường hoặc viện đặt ở góc trên bên phải.


================================================================================
SLIDE 2: ĐẶT VẤN ĐỀ VÀ BẤT CẬP THỰC TẾ
================================================================================

Tiêu đề: Thực trạng chấm code trong phòng máy và hai điểm nghẽn lớn

Nội dung chính:
- Bối cảnh: Phòng máy thực hành có hàng trăm sinh viên đồng thời nộp bài trong khung giờ cao điểm.
- Điểm nghẽn 1: Bất cập về giao tiếp mạng (HTTP Polling)
  + Các hệ thống web hiện nay như Code PTIT dùng giao thức HTTP một chiều.
  + Máy khách phải liên tục gửi gói tin thăm dò tiến độ chấm điểm, gây nghẽn băng thông và tăng tải vô ích cho máy chủ.
- Điểm nghẽn 2: Bất lực trước bài toán Hướng đối tượng (OOP)
  + Phương pháp truyền thống chỉ so khớp ký tự console (input/output chuẩn).
  + Sinh viên viết mã dồn hết vào hàm main, vi phạm bao gói và kế thừa nhưng vẫn được điểm tối đa.
  + Không chấm được bài tập Java OOP, buộc giảng viên phải chấm tay thủ công hàng chục giờ.

Hình ảnh / Sơ đồ đề xuất:
- Góc phải slide đặt hình minh họa đối chiếu:
  + Luồng HTTP Polling: Máy khách gửi lặp lại hàng chục yêu cầu hỏi "Đã xong chưa?".
  + Hạn chế console: Sinh viên khai báo public biến nhưng output in ra vẫn khớp mẫu.


================================================================================
SLIDE 3: MỤC TIÊU VÀ ĐÓNG GÓP KỸ THUẬT CỐT LÕI
================================================================================

Tiêu đề: Giải pháp đề xuất và ba trụ cột kỹ thuật then chốt

Nội dung chính:
- Mục tiêu tổng quát:
  Xây dựng hệ thống mạng chấm bài chuyên dụng theo mô hình Client-Server, hoạt động bền vững, phản hồi tức thì và tự động hóa toàn diện bài tập lập trình.
- Ba điểm đột phá kỹ thuật:
  1. Fit tuyệt đối với chuẩn kiến thức Lập trình mạng:
     Sử dụng kết nối socket TCP bền vững trên cổng 9999, giao thức Delimited JSON, hàng đợi Producer-Consumer và cơ chế Server Push hai chiều loại bỏ 100% polling.
  2. Động cơ chấm Java OOP hai pha:
     Soi chiếu cấu trúc nhị phân tĩnh và nạp động đối tượng kiểm thử hành vi phương thức, hoàn toàn không cần hàm main.
  3. Hệ thống giám sát phòng thi và an toàn đa tầng:
     Tự động phát hiện gian lận mất tiêu điểm cửa sổ, khóa IP với phiên làm việc và cô lập an toàn tiến trình chấm.


================================================================================
SLIDE 4: KIẾN TRÚC HỆ THỐNG PHÂN TẦNG (SYSTEM ARCHITECTURE)
================================================================================

Tiêu đề: Kiến trúc phân tầng tách biệt mạng và thực thi

Nội dung chính:
- Tầng Trình khách (Client Tier):
  Ứng dụng JavaFX độc lập trên máy trạm, duy trì kết nối socket nền bất đồng bộ qua luồng ListenerThread.
- Tầng Máy chủ dịch vụ (Server Tier):
  Tiến trình Java đa luồng lắng nghe cổng 9999, bộ định tuyến bóc tách mã lệnh opcode, hàng đợi chặn LinkedBlockingQueue đệm an toàn 500 bài và nhóm công nhân chấm song song.
- Tầng Lưu trữ phân tầng (Storage Tier):
  PostgreSQL quản lý dữ liệu nghiệp vụ quan hệ; MinIO tương thích S3 lưu trữ các gói bài nộp dự án ZIP lớn.
- Lợi ích kiến trúc:
  Tách biệt hoàn toàn tốc độ nhận mạng (vài mili-giây) khỏi thời gian chấm bài (vài giây), luồng mạng luôn được giải phóng lập tức.

Hình ảnh / Sơ đồ đề xuất:
- Trình bày trực tiếp [Hình 3.1: Sơ đồ kiến trúc phân tầng tổng thể hệ thống] (đã chuẩn bị trong file Hinh_3.1.md).
- Bố cục sơ đồ chiều ngang: Tầng Client -> Kênh TCP Port 9999 -> Tầng Server (Ingress, Queue, Workers) -> Tầng Storage.


================================================================================
SLIDE 5: THIẾT KẾ GIAO THỨC TRUYỀN THÔNG MẠNG (DELIMITED JSON)
================================================================================

Tiêu đề: Đóng khung thông điệp và giải quyết bài toán dòng byte TCP

Nội dung chính:
- Bản chất tầng giao vận:
  TCP là dòng byte liên tục, không có ranh giới gói tin tự nhiên; dễ phát sinh dính gói (packet coalescing) và phân mảnh trên đường truyền.
- Kỹ thuật đóng khung (Framing):
  Sử dụng ký tự ngắt dòng LF (\n) làm điểm phân cách kết hợp dữ liệu JSON chuẩn. Mỗi gói tin là một dòng văn bản hoàn chỉnh.
- Cấu trúc thông điệp chuẩn hóa gồm 3 trường:
  + opcode: Mã lệnh hành động nghiệp vụ (AUTH_LOGIN, SUBMIT_CODE, PUSH_RESULT...).
  + token: Chuỗi xác thực quyền phiên làm việc của người dùng.
  + data: Payload chi tiết dạng đối tượng hoặc danh sách JSON.
- Cơ chế kiểm soát tràn bộ đệm (Backpressure):
  Hàng đợi giới hạn an toàn tối đa 500 bài nộp. Nếu quá tải, máy chủ gửi ngay mã SERVER_BUSY để máy trạm tạm hoãn gửi, triệt tiêu nguy cơ tràn bộ nhớ heap.


================================================================================
SLIDE 6: CHU TRÌNH MẠNG VÀ CƠ CHẾ THÔNG BÁO ĐẨY HAI CHIỀU
================================================================================

Tiêu đề: Triệt tiêu Polling bằng cơ chế Server Push thời gian thực

Nội dung chính:
- Chu trình 1: Bắt tay xác thực phiên làm việc
  + Máy khách gửi AUTH_LOGIN -> Máy chủ kiểm tra mật khẩu băm, cấp token phiên và lưu kênh socket vào bảng an toàn luồng ConcurrentHashMap.
- Chu trình 2: Nộp bài bất đồng bộ không chặn (Non-blocking Submission)
  + Máy khách gửi SUBMIT_CODE.
  + Luồng mạng máy chủ tiếp nhận, đưa bài vào hàng đợi và phản hồi ngay SUBMIT_ACK trong dưới 5 mili-giây. Luồng mạng máy trạm lập tức giải phóng.
- Chu trình 3: Thông báo đẩy chủ động (Server-Initiated Push)
  + Công nhân chấm xong bài -> Dịch vụ thông báo tra cứu bảng kết nối theo mã người dùng.
  + Máy chủ chủ động đẩy gói tin PUSH_RESULT qua socket TCP mở sẵn.
  + Giao diện sinh viên tự động hiển thị kết quả phán quyết với độ trễ dưới 10 mili-giây mà không cần gửi bất kỳ gói tin thăm dò nào.

Hình ảnh / Sơ đồ đề xuất:
- Trình bày [Hình 3.3: Biểu đồ tuần tự trao đổi giao thức mạng giữa máy khách và máy chủ] (đã chuẩn bị trong file Hinh_3.3.md).
- Nhấn mạnh mũi tên Server Push chủ động từ Server sang Client ở cuối quy trình.


================================================================================
SLIDE 7: ĐỘNG CƠ CHẤM BÀI HƯỚNG ĐỐI TƯỢNG (OOP) HAI PHA
================================================================================

Tiêu đề: Đột phá thẩm định OOP chuyên sâu bằng phản xạ động (Java Reflection)

Nội dung chính:
- Thách thức: Làm thế nào chấm tự động tính bao gói, kế thừa và đa hình mà sinh viên không cần viết hàm main?
- Quy trình chấm hai pha độc lập:
  + Pha 1 - Thẩm định cấu trúc nhị phân tĩnh:
    Sử dụng URLClassLoader nạp bytecode tệp .class sau biên dịch; quét qua cây phản xạ để đối chiếu số lượng thuộc tính, phạm vi truy cập private, quy cách hàm khởi tạo và chữ ký phương thức.
  + Pha 2 - Kiểm thử hành vi đối tượng động:
    Khởi tạo đối tượng thực tế trong máy ảo Java cách ly; kích hoạt phương thức độc lập qua Java Reflection với các bộ tham số đầu vào và so sánh giá trị đối tượng trả về.
- Khả năng xử lý dự án lớn:
  Hỗ trợ sinh viên nộp gói ZIP dự án nhiều tệp liên kết; máy chủ tự giải nén, sắp xếp thứ tự biên dịch phụ thuộc và thực thi kiểm thử.

Hình ảnh / Sơ đồ đề xuất:
- Trình bày [Hình 3.2: Sơ đồ quy trình động cơ chấm lập trình hướng đối tượng hai pha] (đã chuẩn bị trong file Hinh_3.2.md).
- Minh họa rõ 2 hộp khối: Pha 1 kiểm tra cấu trúc class -> Pha 2 tạo đối tượng sống và gọi phương thức.


================================================================================
SLIDE 8: CƠ CHẾ GIÁM SÁT PHÒNG THI VÀ PHÁT HIỆN GIAN LẬN
================================================================================

Tiêu đề: Giám sát toàn vẹn kỳ thi và bảo vệ chống gian lận đa lớp

Nội dung chính:
- Tầng Trình khách (Client Proctoring):
  + Module giám sát tích hợp lắng nghe sự kiện mất tiêu điểm cửa sổ (Window Focus Listener).
  + Bắt quả tang hành vi sinh viên chuyển đổi ứng dụng, mở tab trình duyệt ngoài hoặc kích hoạt công cụ hỗ trợ AI; đóng gói cảnh báo gửi tức thì về máy chủ.
- Tầng Mạng (Network Security):
  + Token phiên làm việc được gắn chặt với địa chỉ IP của máy trạm tại thời điểm đăng nhập.
  + Phát hiện và thu hồi phiên ngay lập tức nếu một token bị dùng đồng thời từ hai địa chỉ IP khác nhau, ngăn chặn triệt để hành vi nộp bài hộ hoặc chia sẻ tài khoản.
- Tầng Nghiệp vụ và Dữ liệu:
  + Áp dụng chữ ký băm kiểm tra tính toàn vẹn tệp nguồn chống đánh tráo trên đường truyền.
  + Khóa cổng tiếp nhận đối với các bài nộp gửi ngoài khung giờ thi hoặc sinh viên không thuộc danh sách lớp tín chỉ.

Hình ảnh / Sơ đồ đề xuất:
- Đặt ảnh chụp [Hình 4.2: Giao diện giảng viên giám sát phòng thi và cảnh báo vi phạm trực tiếp] (theo mô tả trong Hinh_4.2.md).
- Chỉ rõ cột cảnh báo vi phạm (Proctoring Alert) tô màu đỏ hiển thị số lần thí sinh chuyển màn hình.


================================================================================
SLIDE 9: TẦNG PHÒNG THỦ AN TOÀN VÀ CÔ LẬP TIẾN TRÌNH CHẤM
================================================================================

Tiêu đề: Giải pháp an toàn thực thi mã nguồn sinh viên trên máy chủ

Nội dung chính:
- Rủi ro thực thi: Mã nguồn bài nộp có thể chứa lệnh phá hoại (xóa tệp, gọi lệnh hệ điều hành) hoặc lỗi giải thuật lặp vô hạn gây treo máy chủ.
- Tầng 1 - Bộ quét tĩnh mã độc (SecurityScanner):
  + Phân tích chuỗi mã nguồn bằng biểu thức chính quy trước khi biên dịch.
  + Chặn đứng các lời gọi hệ thống nguy hiểm: Runtime.getRuntime, ProcessBuilder, System.exit, tệp lệnh shell hoặc mở socket trái phép.
- Tầng 2 - Cô lập tiến trình thực thi (SandboxRunner):
  + Khởi chạy tiến trình con độc lập qua ProcessBuilder, giới hạn bộ nhớ 256MB.
  + Luồng hẹn giờ giám sát chặt chẽ: tự động cưỡng chế ngắt tiến trình sau 2 giây nếu chạy quá thời gian (Time Limit Exceeded).
  + Luồng công nhân bị bài lỗi chỉ ảnh hưởng duy nhất tiến trình con đó; các luồng công nhân khác vẫn liên tục chấm bài bình thường.


================================================================================
SLIDE 10: GIAO DIỆN NGƯỜI DÙNG VÀ TRẢI NGHIỆM THỜI GIAN THỰC
================================================================================

Tiêu đề: Ứng dụng khách JavaFX hiện đại và tối ưu thao tác

Nội dung chính:
- Thiết kế giao diện chuyên nghiệp:
  + Sử dụng bộ phong cách đồ họa AtlantaFX chuẩn Dark theme, tối ưu không gian cho người lập trình.
  + Ứng dụng mô hình hướng sự kiện: chuyển tiếp an toàn dữ liệu từ socket nền sang luồng đồ họa bằng Platform.runLater, bảo đảm giao diện không bao giờ bị đơ giật.
- Trải nghiệm sinh viên (ProblemView):
  + Bố cục ba phân vùng trực quan: đề bài và ràng buộc tài nguyên bên trái; trình soạn thảo mã nguồn tích hợp đánh số dòng ở giữa; bảng test case hiển thị kết quả phán quyết bên phải.
  + Hỗ trợ nộp mã nguồn trực tiếp hoặc chọn tệp ZIP dự án; trạng thái Accepted xanh lá hiển thị ngay khi máy chủ chấm xong.
- Trải nghiệm giảng viên (TeacherView):
  + Quản trị học phần, phân công bài tập vào lớp tín chỉ, cấu hình đặc tả OOP và theo dõi bảng điểm lũy tiến trực tiếp.

Hình ảnh / Sơ đồ đề xuất:
- Đặt ảnh chụp [Hình 4.1: Giao diện sinh viên nộp bài và nhận kết quả phán quyết thời gian thực qua socket TCP] (theo mô tả trong Hinh_4.1.md).


================================================================================
SLIDE 11: ĐÁNH GIÁ THỰC NGHIỆM: KHẢ NĂNG MỞ RỘNG VÀ THÔNG LƯỢNG
================================================================================

Tiêu đề: Kết quả đo lường định lượng và năng lực tăng tốc hệ thống

Nội dung chính:
- Phương pháp luận thực nghiệm:
  + Module ExperimentRunner tự động hóa 100% việc sinh tải từ 5 máy khách đồng thời; đo đạc theo nguyên tắc kiểm soát biến độc lập.
- Kết quả Bộ thực nghiệm 1 (Worker Scalability - Cố định 20 bài nộp):
  + Mô hình cơ sở 1 công nhân tuần tự: Hoàn thành mất 31.36 giây, thông lượng 38.3 bài/phút, thời gian chờ tối đa 30.9 giây.
  + Mô hình đề xuất 4 công nhân song song: Hoàn thành trong 7.81 giây, thông lượng đạt 153.6 bài/phút (tăng tốc 4.01 lần), thời gian chờ tối đa giảm xuống 7.48 giây (giải phóng 23 giây nghẽn tắc).
- Kết quả Bộ thực nghiệm 2 (Workload Scalability - Cố định 4 công nhân):
  + Khi tăng tải gấp 6 lần từ 10 lên 60 bài nộp dồn dập, thông lượng hệ thống duy trì ổn định quanh ngưỡng 138 đến 158 bài/phút.
  + Hàng đợi LinkedBlockingQueue tiếp nhận an toàn 100% bài nộp vào bộ nhớ trong vài mili-giây, không xảy ra hiện tượng từ chối dịch vụ.

Hình ảnh / Sơ đồ đề xuất:
- Trình bày Bảng 4.1 hoặc biểu đồ cột so sánh thông lượng và thời gian chờ giữa 1 Worker vs 4 Workers.


================================================================================
SLIDE 12: ĐÁNH GIÁ THỰC NGHIỆM: ĐỘ CHÍNH XÁC VÀ BẮT LỖI BIÊN
================================================================================

Tiêu đề: Tính chính xác của động cơ chấm và khả năng chống nghẽn

Nội dung chính:
- Độ chính xác đánh giá mã nguồn:
  Động cơ phân loại chính xác 100% trên tập mã nguồn đa dạng đại diện cho mọi tình huống:
  + Chạy đúng logic: Nhận phán quyết Accepted đạt điểm tối đa.
  + Sai đáp số: Nhận Wrong Answer kèm chỉ số test case lệch.
  + Lỗi chia cho 0: Nhận Runtime Error kèm nhật ký ngoại lệ.
  + Lỗi cú pháp: Nhận Compile Error kèm vị trí dòng lỗi.
- Khả năng xử lý bài nộp vòng lặp vô hạn (TLE):
  + Trong mô hình tuần tự, 1 bài lặp vô hạn làm đóng băng toàn bộ hệ thống suốt thời gian chờ.
  + Trong mô hình đề xuất, bài lặp vô hạn chỉ chiếm 1 luồng công nhân và bị ngắt sau 2 giây; 3 công nhân còn lại vẫn chấm và trả kết quả liên tục, loại bỏ hoàn toàn nguy cơ nghẽn dây chuyền.
- Đánh giá bài tập OOP:
  Bắt chính xác các trường hợp khai báo sai phạm vi truy cập biến hoặc sai kiểu dữ liệu phương thức mà máy chấm console truyền thống bỏ lọt.


================================================================================
SLIDE 13: TỔNG HỢP CÁC ĐÓNG GÓP KỸ THUẬT NỔI BẬT (NOVELTY)
================================================================================

Tiêu đề: Bảng tổng hợp 4 đóng góp kỹ thuật cốt lõi và minh chứng thực nghiệm

Nội dung chính:
- 1. Kiến trúc Producer-Consumer kết hợp Worker Pool:
  + Hiện thực: Hàng đợi chặn LinkedBlockingQueue đệm 500 bài và nhóm 4 công nhân xử lý song song.
  + Minh chứng: Thông lượng tăng từ 38.3 lên 153.6 bài/phút (hệ số tăng tốc 4.01x), thời gian chờ hàng đợi giảm 74.5%.
- 2. Cơ chế xử lý bài nộp lỗi không làm nghẽn hệ thống:
  + Hiện thực: Cô lập tiến trình ProcessBuilder, luồng hẹn giờ ngắt cưỡng chế sau 2 giây nếu gặp lặp vô hạn (TLE).
  + Minh chứng: Thời gian chờ tối đa trong hàng đợi giảm mạnh từ 30983 mili-giây xuống 7480 mili-giây.
- 3. Cơ chế thông báo đẩy thời gian thực hai chiều (Server Push):
  + Hiện thực: Bảng quản lý kết nối ConnectionRegistry phát gói tin PUSH_RESULT qua socket TCP mở sẵn.
  + Minh chứng: Triệt tiêu 100% gói tin thăm dò HTTP polling, thời gian nhận kết quả dưới 10 mili-giây.
- 4. Động cơ chấm Java OOP hai pha bằng phản xạ động (Java Reflection):
  + Hiện thực: Pha 1 soi chiếu bytecode nhị phân tĩnh; Pha 2 nạp động đối tượng sống kiểm thử đa hình.
  + Minh chứng: Tự động hóa đánh giá bao gói, kế thừa và gọi phương thức mà sinh viên không cần viết hàm main.

Hình ảnh / Sơ đồ đề xuất:
- Trình bày dạng bảng tổng hợp 4 dòng (Đóng góp kỹ thuật - Giải pháp hiện thực - Minh chứng định lượng) bám sát Bảng 5.1 trong báo cáo kỹ thuật.


================================================================================
SLIDE 14: ĐỐI CHIẾU KIẾN THỨC VỚI MÔN LẬP TRÌNH MẠNG
================================================================================

Tiêu đề: Vận dụng toàn diện các kiến thức trọng tâm môn Lập trình mạng

Nội dung chính:
- 1. Tầng giao vận (Transport Layer):
  + Cài đặt socket TCP hướng kết nối trên cổng 9999, bảo đảm tính toàn vẹn và thứ tự dữ liệu mã nguồn giải thuật.
- 2. Đóng khung dữ liệu tầng ứng dụng (Application Framing):
  + Thiết kế giao thức Delimited JSON dùng ký tự ngắt dòng LF (\n), giải quyết triệt để bài toán dính gói và phân mảnh trên dòng byte TCP.
- 3. Kiến trúc Máy chủ đa luồng (Multithreaded Server):
  + Kết hợp ServerSocket với ExecutorService ThreadPool quản lý nhóm luồng mạng, phục vụ đồng thời hàng trăm máy trạm phòng thi.
- 4. Xử lý bất đồng bộ và kiểm soát tải (Asynchronous & Backpressure):
  + Mô hình Producer-Consumer phân tách luồng nhận mạng và luồng chấm qua LinkedBlockingQueue.
  + Kích hoạt cơ chế Backpressure từ chối tải an toàn với mã SERVER_BUSY khi hàng đợi đầy 500 bài, ngừa sập bộ nhớ heap.
- 5. Truyền thông hai chiều thời gian thực (Server-Initiated Push):
  + Duy trì bảng ánh xạ an toàn luồng ConcurrentHashMap lưu kênh truyền, chủ động đẩy gói tin từ máy chủ về máy khách qua socket mở sẵn.
- 6. An toàn truyền thông và quản lý phiên (Network Security & Session):
  + Khóa chặt Token phiên với Địa chỉ IP máy trạm, tự động thu hồi phiên khi phát hiện sử dụng đồng thời từ hai máy hoặc có dấu hiệu mạo danh.


================================================================================
SLIDE 15: DEMO DỰ ÁN
================================================================================


================================================================================
SLIDE 16: TỔNG KẾT VÀ GIÁ TRỊ THỰC TIỄN
================================================================================

Tiêu đề: Kết luận đề tài và khả năng ứng dụng thực tế

Nội dung chính:
- Tổng kết thành quả đạt được:
  1. Đáp ứng toàn diện chuẩn môn Lập trình mạng:
     Xây dựng hoàn chỉnh hệ thống mạng socket TCP hai chiều, giao thức Delimited JSON, hàng đợi Producer-Consumer và Server Push thời gian thực loại bỏ hoàn toàn polling.
  2. Giải quyết bài toán thực tế của đào tạo:
     Tự động hóa hoàn toàn bài tập Java OOP bằng phản xạ động hai pha, giải phóng hàng chục giờ chấm tay của giảng viên.
  3. Môi trường thi cử nghiêm túc và an toàn:
     Giám sát gian lận chuyển ứng dụng tức thời, khóa token theo IP và cô lập tài nguyên máy chủ.
- Tính ứng dụng:
  Hệ thống chạy gọn nhẹ trên máy chủ độc lập không phụ thuộc phần mềm trung gian phức tạp, sẵn sàng triển khai ngay tại các phòng máy thực hành của Học viện.

Lời kết:
Chân thành cảm ơn Thầy/Cô trong Hội đồng đã lắng nghe!
Kính mời Thầy/Cô đặt câu hỏi và đóng góp ý kiến cho nhóm.
