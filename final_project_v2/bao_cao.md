HỆ THỐNG CHẤM MÃ NGUỒN TỰ ĐỘNG PHÂN TÁN QUA MẠNG DỰA TRÊN KIẾN TRÚC HÀNG ĐỢI WORKER VÀ THÔNG BÁO ĐẨY QUA GIAO THỨC TCP
TÓM TẮT
Báo cáo trình bày thiết kế và cài đặt hệ thống chấm mã nguồn tự động phân tán theo mô hình máy khách - máy chủ trên nền socket TCP bền vững và giao thức Delimited JSON. Nhằm giải quyết điểm nghẽn của mô hình tuần tự và loại bỏ chi phí băng thông từ kỹ thuật thăm dò HTTP polling, hệ thống kết hợp hàng đợi chặn trong bộ nhớ với nhóm công nhân xử lý song song. Bên cạnh việc đánh giá thuật toán qua luồng nhập xuất chuẩn, hệ thống tích hợp động cơ chấm hướng đối tượng hai pha dựa trên phản xạ động để thẩm định trực tiếp cấu trúc lớp và kích hoạt phương thức độc lập. Tiến trình chấm được bảo vệ qua bộ quét mã độc và cơ chế cô lập tiến trình có giới hạn tài nguyên. Máy chủ chủ động đẩy kết quả về máy khách theo thời gian thực ngay khi hoàn tất. Thực nghiệm xác nhận hệ thống nâng thông lượng xử lý gấp hơn ba lần mô hình cơ sở, giữ độ trễ ổn định dưới tải cao và loại bỏ hoàn toàn lưu lượng mạng dư thừa.
TỪ KHÓA
Lập trình mạng, giao thức TCP, thông điệp JSON, hàng đợi worker, chấm mã nguồn tự động, kiểm thử hướng đối tượng.

1. TỔNG QUAN
1.1 Bối cảnh và động lực nghiên cứu
a. Thực trạng giảng dạy lập trình và nhu cầu chấm tự động trong môi trường mạng
Đào tạo lập trình đại học đòi hỏi việc đánh giá bài nộp phải chính xác và phản hồi tức thì. Trong các kỳ thi tại phòng máy, hàng trăm máy trạm đồng thời gửi mã nguồn khiến việc chấm thủ công bất khả thi. Các hệ thống web hiện nay dùng giao thức HTTP một chiều buộc máy khách liên tục gửi gói tin thăm dò tiến độ, gây lãng phí băng thông, tăng tải xử lý tiêu đề cho máy chủ và tạo độ trễ phản hồi cho người học.
b. Thách thức về tải đồng thời và an toàn thực thi mã nguồn
Khi lưu lượng kết nối tăng vọt, máy chủ xử lý tuần tự dễ nghẽn tắc nếu gặp bài nộp chứa vòng lặp vô hạn. Mặt khác, việc chạy trực tiếp mã nguồn sinh viên tiềm ẩn nguy cơ an ninh như lệnh xóa tệp hệ thống hoặc mở socket trái phép. Hệ thống chấm mạng do đó phải phân phối bài nộp bất đồng bộ mượt mà, đồng thời thiết lập cơ chế cô lập an toàn tiến trình chấm mà không lãng phí tài nguyên máy chủ.
1.2 Phát biểu bài toán và phạm vi đề tài
a. Phát biểu bài toán
Đề tài thiết kế và cài đặt hệ thống chấm mã nguồn tự động phân tán theo mô hình máy khách - máy chủ qua giao thức TCP. Hệ thống tiếp nhận bài nộp từ ứng dụng đồ họa, đưa vào hàng đợi điều phối, chấm độc lập và tự động đẩy kết quả về máy khách theo thời gian thực. Yêu cầu then chốt gồm đóng khung gói tin trên luồng byte TCP, tách biệt luồng mạng với luồng chấm, kiểm thử hướng đối tượng chuyên sâu và ngăn chặn mã độc phá hoại máy chủ.
b. Phạm vi và giới hạn
Hệ thống triển khai qua socket TCP trên cổng 9999, hỗ trợ bốn ngôn ngữ Java, C, C++ và Python với trọng tâm là Java OOP. Trình khách sử dụng đồ họa JavaFX, kết nối máy chủ đa luồng tích hợp cơ sở dữ liệu PostgreSQL và kho lưu trữ MinIO. Đề tài tối ưu kiến trúc socket nội bộ, không sử dụng message broker ngoài nhằm giữ hạ tầng phòng máy gọn nhẹ và độc lập.
1.3 Mục tiêu đề tài
Đề tài xác định ba mục tiêu kỹ thuật cụ thể. Thứ nhất, thiết kế giao thức mạng trên socket TCP bền vững dùng định dạng JSON phân dòng để truyền nhận hai chiều và đẩy kết quả thời gian thực, loại bỏ hoàn toàn thăm dò HTTP. Thứ hai, xây dựng kiến trúc xử lý đồng thời với hàng đợi chặn nội bộ và nhóm luồng công nhân song song, tích hợp cơ chế điều tiết dòng tải nhằm tối ưu thông lượng và ngừa tràn bộ nhớ. Thứ ba, phát triển động cơ chấm hướng đối tượng chuyên dụng bằng phản xạ động hai pha, đánh giá trực tiếp cấu trúc lớp và hành vi phương thức mà không phụ thuộc luồng nhập xuất chuẩn, đi kèm cơ chế cô lập tiến trình an toàn.
1.4 Đóng góp chính
Đề tài mang lại bốn đóng góp kỹ thuật thực tế cho hệ thống chấm mã nguồn tự động qua mạng:
- Thiết kế giao thức Delimited JSON trên socket TCP bền vững: Chuẩn hóa tập mã lệnh ngắn gọn, tích hợp xác thực phiên và cơ chế đẩy kết quả hai chiều thời gian thực, loại bỏ hoàn toàn chi phí thăm dò HTTP.
- Hiện thực hóa hàng đợi chặn nội bộ kết hợp công nhân chấm song song: Phân tách rạch ròi luồng mạng với luồng chấm giúp máy chủ phản hồi tức thì, triệt tiêu sự phụ thuộc vào các message broker ngoài.
- Phát triển động cơ chấm hướng đối tượng bằng phản xạ động hai pha: Thẩm định trực tiếp cấu trúc lớp và kích hoạt phương thức tại thời điểm chạy không phụ thuộc console, giải quyết trọn vẹn bài toán chấm tự động bài tập hướng đối tượng.
- Xây dựng giải pháp phòng thủ an toàn và kịch bản thực nghiệm định lượng: Kết hợp bộ lọc tĩnh mã độc với cơ chế giới hạn tài nguyên tiến trình, đi kèm kịch bản đo lường tự động cung cấp đầy đủ số liệu về thông lượng, độ trễ và mức tiêu thụ phần cứng.

2. CÁC NGHIÊN CỨU LIÊN QUAN VÀ CƠ SỞ LÝ THUYẾT
2.1 Cơ sở lý thuyết truyền thông mạng và xử lý đồng thời
Giao thức TCP cung cấp kênh truyền tin cậy, hướng kết nối và bảo toàn thứ tự dữ liệu trên nền IP. Do TCP truyền theo dòng byte liên tục, hiện tượng dính gói và phân mảnh luôn xảy ra khi gửi thông điệp liên tiếp. Kỹ thuật phân tách bằng ký tự ngắt dòng kết hợp định dạng JSON cho phép bên nhận bóc tách chính xác từng gói tin có cấu trúc.
Về xử lý đồng thời, việc tạo luồng mới cho mỗi kết nối dễ gây cạn kiệt bộ nhớ và quá tải chu kỳ CPU. Nhóm luồng định kích thước kết hợp hàng đợi chặn giúp đệm yêu cầu và phân phối tới công nhân rỗi, duy trì tài nguyên ổn định dưới áp lực tải cao.
2.2 Khảo sát các hệ thống chấm mã nguồn hiện nay
a. Các nền tảng quốc tế: Codeforces và LeetCode
Các nền tảng chấm trực tuyến quốc tế được tối ưu theo mục tiêu riêng. Codeforces phục vụ thi đấu, nạp dữ liệu vào đầu vào chuẩn và so khớp ký tự đầu ra với đáp án mẫu, chỉ hiệu quả cho giải thuật tuần tự mà không hỗ trợ kiểm thử đối tượng. LeetCode phục vụ luyện tập phỏng vấn qua các khung hàm gọi trực tiếp phương thức. Quá trình nộp bài trên web dùng giao thức HTTP, buộc máy khách liên tục thăm dò định kỳ để lấy trạng thái chấm.
b. Hệ thống thực tế tại Học viện: Code PTIT
Tại Học viện Công nghệ Bưu chính Viễn thông, Code PTIT là nền tảng chấm chính thức cho các môn lập trình cơ sở. Hệ thống vận hành theo nguyên lý so khớp luồng nhập xuất chuẩn: sinh viên viết hàm main tự đọc dữ liệu và in kết quả ra màn hình để đối chiếu với đáp án mẫu.
2.3 Phân tích hạn chế và bất cập thực tế
a. Bế tắc của luồng nhập xuất chuẩn trước bài toán hướng đối tượng và gánh nặng chấm tay
Mô hình so khớp luồng nhập xuất bất lực trước các bài toán hướng đối tượng vốn coi trọng tính bao gói, kế thừa và đa hình. Người học có thể viết theo lối thủ tục dồn vào hàm main, khai báo thuộc tính công khai hoặc dùng biến toàn cục nhưng vẫn in ra kết quả trùng khớp mẫu. Máy chấm nhập xuất sẽ cho điểm tuyệt đối dù bài làm vi phạm nguyên lý thiết kế. Việc kiểm tra hàm khởi tạo, quy tắc tự sinh mã hay ràng buộc thuộc tính cũng nằm ngoài khả năng so khớp chuỗi. Hệ quả là Code PTIT không thể chấm bài tập hướng đối tượng, buộc giảng viên phải chấm tay thủ công hàng chục giờ mỗi lớp, làm mất tính phản hồi tức thì.
b. Lãng phí tài nguyên và độ trễ từ cơ chế thăm dò HTTP
Hệ thống chấm web phụ thuộc mô hình hỏi đáp một chiều của HTTP. Trong thời gian bài làm xếp hàng chờ chấm, máy khách liên tục phát gói tin thăm dò trạng thái. Với phòng máy hàng trăm sinh viên, lượng yêu cầu HTTP gửi lên máy chủ lên tới hàng nghìn lượt mỗi phút nhưng hầu hết chỉ nhận về thông báo đang chờ. Sự dư thừa này gây nghẽn mạng nội bộ, tiêu hao năng lực xử lý tiêu đề máy chủ và tạo độ trễ nhận kết quả giữa các chu kỳ thăm dò.
2.4 Định vị giải pháp đề xuất
Hệ thống đề xuất giải quyết trọn vẹn hai điểm nghẽn kỹ thuật nêu trên. Về truyền thông mạng, hệ thống thay thế cơ chế thăm dò HTTP bằng kết nối socket TCP bền vững hai chiều, cho phép máy chủ chủ động đẩy kết quả về máy khách theo thời gian thực ngay khi chấm xong, triệt tiêu lưu lượng mạng vô ích. Về nghiệp vụ đánh giá, hệ thống phát triển động cơ phản xạ động hai pha cho ngôn ngữ Java. Hệ thống kiểm tra trực tiếp bytecode sau biên dịch: soi chiếu phạm vi truy cập, kiểm tra chữ ký phương thức và kích hoạt kiểm thử đối tượng sống trong bộ nhớ mà không cần hàm main, tự động hóa hoàn toàn bài tập hướng đối tượng và giải phóng công sức chấm tay của giảng viên.

3. PHƯƠNG PHÁP LUẬN VÀ THIẾT KẾ HỆ THỐNG
3.1 Định nghĩa bài toán và yêu cầu hệ thống
a. Dữ liệu đầu vào, đầu ra và ràng buộc kỹ thuật
Đầu vào hệ thống gồm thông tin xác thực, mã nguồn đơn hoặc gói ZIP, mã bài tập và cấu hình kiểm thử. Với bài tập hướng đối tượng, đầu vào bổ sung đặc tả JSON về thuộc tính, hàm khởi tạo và phương thức. Đầu ra là gói tin kết quả gồm phán quyết, điểm số, thời gian chạy, mức chiếm bộ nhớ và nhật ký chi tiết.
Hệ thống áp ba ràng buộc kỹ thuật: thời gian chạy tối đa mỗi ca kiểm thử hai giây; bộ nhớ cấp phát mỗi tiến trình con tối đa 256 megabyte; toàn bộ giao tiếp diễn ra trên cổng socket TCP 9999 duy nhất, không dùng máy chủ web trung gian.
b. Yêu cầu chức năng và phi chức năng
Về chức năng, giảng viên quản trị lớp tín chỉ, cấu hình bài tập thuật toán hoặc kiểm thử phản xạ OOP, theo dõi bảng điểm trực tiếp; sinh viên duyệt bài, nộp mã nguồn và nhận kết quả tức thì không cần tải lại trang.
Về phi chức năng, thời gian phản hồi xác nhận nộp bài dưới 200 mili-giây và độ trễ đẩy kết quả về máy khách dưới 100 mili-giây trong mạng nội bộ. Hệ thống tự khôi phục kết nối, ngăn mã độc can thiệp hệ điều hành và bảo toàn điểm số.
3.2 Kiến trúc tổng thể hệ thống
a. Mô hình phân tầng
Hệ thống tổ chức theo kiến trúc ba tầng độc lập nhằm phân tách giao diện người dùng, xử lý nghiệp vụ và lưu trữ dữ liệu.
[Hình 3.1: Sơ đồ kiến trúc phân tầng tổng thể hệ thống]
Tầng trình khách chạy giao diện JavaFX trên máy trạm, duy trì kết nối socket TCP nền. Tầng máy chủ là tiến trình Java độc lập trên cổng 9999, tiếp nhận mạng, điều phối hàng đợi nội bộ và điều hành nhóm công nhân chấm song song. Tầng lưu trữ kết hợp cơ sở dữ liệu PostgreSQL quản lý dữ liệu nghiệp vụ và kho MinIO lưu trữ các gói mã nguồn ZIP.
b. Trách nhiệm các phân hệ và quy trình chấm hai pha
Phân hệ mạng gán mỗi kết nối socket cho một luồng, bóc tách khung JSON và chuyển sang bộ định tuyến. Phân hệ điều phối đưa bài nộp vào hàng đợi chặn trong bộ nhớ rồi giải phóng ngay luồng mạng. Phân hệ động cơ chấm gồm các công nhân chạy ngầm, lấy bài từ hàng đợi, quét tĩnh mã độc và thực thi đánh giá.
[Hình 3.2: Sơ đồ quy trình động cơ chấm lập trình hướng đối tượng hai pha]
Quy trình chấm OOP diễn ra qua hai pha nối tiếp: Pha 1 kiểm tra cấu trúc nhị phân sau biên dịch bằng cơ chế phản xạ tĩnh; Pha 2 nạp động bytecode vào máy ảo Java cách ly để tạo đối tượng sống và kích hoạt kiểm thử hành vi phương thức. Khi hoàn tất, kết quả được lưu vào cơ sở dữ liệu và chuyển tới dịch vụ thông báo đẩy để trả về máy khách qua socket TCP mở sẵn.
3.3 Thiết kế truyền thông mạng
a. Đóng khung dữ liệu và cấu trúc thông điệp
Dữ liệu truyền qua TCP là dòng byte liên tục. Để chống dính gói và phân mảnh, hệ thống áp dụng kỹ thuật đóng khung dòng bằng ký tự ngắt dòng LF kết hợp định dạng JSON. Mỗi gói tin là một chuỗi JSON trọn vẹn trên một dòng đơn, kết thúc bằng LF. Cấu trúc gói chuẩn hóa gồm ba trường: opcode quy định loại lệnh nghiệp vụ, token xác thực quyền phiên làm việc và data chứa đối tượng payload chi tiết.
b. Tập mã lệnh điều khiển
Giao thức chuẩn hóa bốn nhóm mã lệnh chính: nhóm xác thực phiên tài khoản gồm AUTH_LOGIN, AUTH_LOGOUT và phản hồi AUTH_SUCCESS, AUTH_FAILURE; nhóm quản lý đào tạo gồm GET_CLASSES, GET_PROBLEMS; nhóm kỳ thi và nộp bài gồm GET_ASSESSMENTS, START_ASSESSMENT, SUBMIT_CODE và xác nhận SUBMIT_ACK; nhóm thông báo đẩy gồm PUSH_RESULT và cảnh báo quá tải SERVER_BUSY. Chi tiết cấu trúc payload từng mã lệnh được tra cứu tại Phụ lục A.
c. Trình tự trao đổi thông điệp
Giao thức mạng vận hành theo hai chu trình trao đổi cốt lõi trên biểu đồ tuần tự.
[Hình 3.3: Biểu đồ tuần tự trao đổi giao thức mạng giữa máy khách và máy chủ]
Chu trình xác thực mở kết nối TCP tới cổng 9999, gửi mã AUTH_LOGIN đối chiếu mật khẩu băm và nhận token phiên. Các yêu cầu nghiệp vụ sau đó bắt buộc đính kèm token này trong gói tin để vượt qua bộ lọc an toàn.
Chu trình nộp bài diễn ra bất đồng bộ và không chặn luồng mạng: máy khách gửi SUBMIT_CODE, máy chủ nhận bài đưa vào hàng đợi và phản hồi ngay SUBMIT_ACK. Luồng mạng máy khách lập tức giải phóng. Khi công nhân chấm xong, dịch vụ thông báo đẩy chủ động gửi PUSH_RESULT qua socket TCP mở sẵn, giao diện tự cập nhật kết quả mà không cần máy khách gửi gói thăm dò.
d. Duy trì phiên và xử lý đứt kết nối
Máy chủ quản lý phiên bằng bảng liên kết mã người dùng với kênh socket tương ứng trong bộ nhớ. Khi máy trạm mất kết nối đột ngột, luồng đọc mạng lập tức đóng kênh socket, hủy đăng ký phiên và ghi nhật ký. Token phiên được lưu có thời hạn trong cơ sở dữ liệu giúp máy khách tự động tái xác thực khi có mạng trở lại. Kết quả chấm hoàn thành trong lúc mất mạng vẫn được lưu bền vững và cập nhật ngay khi máy trạm kết nối lại.
3.4 Thiết kế phân hệ xử lý đồng thời và hàng đợi chấm bài
a. Kiến trúc hàng đợi LinkedBlockingQueue kết hợp Worker Pool
Nhằm tách biệt tốc độ nhận mạng tính bằng mili-giây với thời gian chấm bài kéo dài nhiều giây, máy chủ áp dụng mô hình Nhà sản xuất và Người tiêu dùng với hàng đợi chặn LinkedBlockingQueue trong bộ nhớ và nhóm luồng công nhân cố định. Luồng mạng đóng vai trò Nhà sản xuất, chỉ ghi nhận bài nộp, đóng gói và đưa vào hàng đợi. Luồng công nhân đóng vai trò Người tiêu dùng, liên tục lấy bài để thực thi chấm. Nếu hàng đợi rỗng, công nhân tự động ngủ đông không tiêu hao chu kỳ xử lý; khi có bài mới, công nhân lập tức được đánh thức. Số luồng công nhân được ấn định tương ứng số lõi vi xử lý vật lý nhằm triệt tiêu chi phí chuyển đổi ngữ cảnh luồng.
b. Luận cứ sử dụng hàng đợi nội bộ thay cho Message Broker
Các hệ thống quy mô lớn thường dùng message broker phân tán như Kafka hoặc RabbitMQ. Tuy nhiên, trong hạ tầng phòng máy thực hành, việc đưa vào các cụm trung gian này bộc lộ ba điểm bất hợp lý: phát sinh các dịch vụ điều phối phụ trợ phức tạp như Zookeeper gây khó bảo trì; tạo thêm hai chặng truyền thông nội bộ làm tăng độ trễ xử lý bài nộp; và tiêu tốn hàng gigabyte RAM duy trì dịch vụ nền trong khi bộ nhớ máy chủ cần ưu tiên cho biên dịch mã nguồn. Sử dụng LinkedBlockingQueue thuần túy trong bộ nhớ là giải pháp tối ưu: loại bỏ hoàn toàn độ trễ mạng phụ, giảm chiếm dụng RAM về mức tối thiểu và đảm bảo tính khép kín, tự vận hành độc lập của hệ thống.
c. Cơ chế kiểm soát tràn bộ đệm Backpressure
Khi hàng loạt máy trạm nộp bài dồn dập trong kỳ thi, tốc độ gửi bài có thể vượt xa tốc độ tiêu thụ của nhóm công nhân. Nếu hàng đợi không giới hạn dung lượng, bài nộp dồn ứ sẽ làm cạn kiệt bộ nhớ heap máy ảo Java. Hệ thống thiết lập cơ chế kiểm soát dòng nộp bài dựa trên nguyên lý Backpressure với ngưỡng an toàn tối đa 500 bài nộp. Khi hàng đợi chạm ngưỡng này, máy chủ từ chối nhận thêm và gửi ngay mã cảnh báo SERVER_BUSY. Máy khách nhận mã sẽ tạm khóa nút nộp bài vài giây trước khi cho thử lại, bảo vệ máy chủ an toàn tuyệt đối trước nguy cơ quá tải bộ nhớ.
3.5 Thiết kế lưu trữ dữ liệu và an toàn hệ thống
a. Thiết kế cơ sở dữ liệu quan hệ
Hệ thống sử dụng cơ sở dữ liệu PostgreSQL để quản lý tập trung và toàn vẹn các thực thể nghiệp vụ.
[Hình 3.4: Lược đồ quan hệ thực thể cơ sở dữ liệu hệ thống]
Mô hình dữ liệu chuẩn hóa gồm 13 bảng. Bảng users lưu danh tính và vai trò. Bảng courses và classes quản lý môn học và lớp tín chỉ. Để tránh nhân bản dữ liệu, hệ thống thiết kế các bảng trung gian cho quan hệ nhiều-nhiều: class_enrollments gắn sinh viên với lớp học; class_problems liên kết đề bài với lớp kèm thời gian đóng mở riêng; assessment_problems và assessment_attempts quản lý danh mục đề thi và phiên làm bài. Bảng test_cases lưu dữ liệu kiểm thử. Bảng submissions là thực thể nòng cốt ghi nhận lịch sử nộp bài, điểm số, phán quyết và cờ nén is_zip. Hai bảng proctor_logs và proctor_violation_counters lưu vết vi phạm độc lập với vòng đời tài khoản người dùng.
b. Phân tầng lưu trữ tệp nguồn qua kho đối tượng MinIO
Lưu trực tiếp mã nguồn lớn hoặc gói ZIP vào cơ sở dữ liệu quan hệ làm phình to bảng dữ liệu và nghẽn bộ đệm đọc ghi. Hệ thống áp dụng mô hình phân tầng: cơ sở dữ liệu chỉ lưu thông tin chỉ mục, điểm số và phán quyết; toàn bộ tệp nguồn và gói ZIP được đồng bộ bất đồng bộ sang kho đối tượng MinIO tương thích giao thức S3. Khóa định danh trên MinIO đặt theo quy tắc phân cấp mã bài tập và mã bài nộp. Thiết kế này giữ cơ sở dữ liệu nhẹ gọn, tăng tốc sao lưu và giúp máy chủ vận hành độc lập nếu MinIO tạm ngắt kết nối.
c. Cơ chế phòng chống gian lận và bảo vệ kỳ thi
Hệ thống thiết lập bảo vệ đa lớp cho các kỳ thi thực hành. Ở tầng mạng, mọi yêu cầu bắt buộc đính kèm token phiên hợp lệ gắn liền với địa chỉ IP máy trạm. Nếu phát hiện token dùng đồng thời từ hai IP khác nhau hoặc có dấu hiệu mạo danh máy thi, máy chủ lập tức hủy phiên và ghi nhận vi phạm. Ở tầng dữ liệu, hệ thống kiểm tra chữ ký băm tệp nguồn chống sửa đổi trên đường truyền, đồng thời từ chối mọi bài nộp gửi ngoài khung giờ thi hoặc không thuộc danh sách lớp tín chỉ.
