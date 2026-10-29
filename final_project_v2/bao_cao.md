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

4. HIỆN THỰC HÓA VÀ ĐÁNH GIÁ THỰC NGHIỆM
4.1 Hiện thực hóa các thành phần phần mềm cốt lõi
a. Máy chủ socket đa luồng và cơ chế thông báo đẩy
Máy chủ cài đặt trên Java 17 LTS, lắng nghe cổng TCP 9999 qua ServerSocket tiêu chuẩn. Lớp SocketServer dùng nhóm luồng cố định ExecutorService quản lý kết nối từ các máy trạm, giới hạn số luồng đồng thời nhằm chống quá tải bộ nhớ. Mỗi kết nối do một luồng ClientHandler độc lập tiếp nhận, sử dụng BufferedReader đọc từng dòng JSON và PrintWriter gửi dữ liệu phản hồi.
Khi xác thực thành công, kênh socket được lưu vào bảng ánh xạ ConcurrentHashMap trong lớp ConnectionRegistry. Khi công nhân hoàn tất chấm bài, dịch vụ này tra cứu kênh socket theo tên người nộp và đẩy ngay gói tin kết quả qua kết nối mở sẵn, loại bỏ hoàn toàn cơ chế thăm dò trạng thái từ máy khách.
b. Ứng dụng khách JavaFX và giao diện tương tác thời gian thực
Ứng dụng khách xây dựng bằng JavaFX kết hợp thư viện AtlantaFX theo kiến trúc hướng sự kiện. Lớp SocketClient duy trì kênh TCP bền vững, điều hành luồng nền ListenerThread túc trực nhận gói tin mà không làm treo giao diện. Dữ liệu đẩy về được chuyển an toàn lên màn hình qua cơ chế Platform.runLater.
[Hình 4.1: Giao diện sinh viên nộp bài và nhận kết quả phán quyết thời gian thực qua socket TCP]
Giao diện ProblemView chia ba phân vùng: bên trái hiển thị đề bài và giới hạn tài nguyên; ở giữa là vùng soạn thảo mã nguồn kèm tính năng nộp tệp đơn hoặc gói ZIP; bên phải là bảng test case cập nhật trạng thái Accepted xanh lá ngay khi máy chủ đẩy dữ liệu về.
[Hình 4.2: Giao diện giảng viên giám sát phòng thi và cảnh báo vi phạm trực tiếp]
Giao diện TeacherAssessmentMonitorView cung cấp bảng theo dõi trực tiếp phòng thi: hiển thị danh sách thí sinh trực tuyến, điểm số lũy tiến từng câu và cảnh báo phát hiện gian lận khi sinh viên chuyển tab hoặc mất tiêu điểm cửa sổ.
c. Động cơ chấm OOP hai pha và cơ chế cô lập an toàn
Động cơ JudgeEngine sử dụng nhóm luồng JudgeWorker lấy bài nộp từ hàng đợi LinkedBlockingQueue. Với bài tập hướng đối tượng, lớp OopReflectionGrader thẩm định qua hai pha: Pha 1 dùng URLClassLoader nạp bytecode sau biên dịch để soi chiếu cấu trúc, kiểm tra tính bao gói của thuộc tính và đối chiếu chữ ký phương thức; Pha 2 khởi tạo đối tượng sống trong bộ nhớ và kích hoạt phương thức độc lập qua Java Reflection để kiểm tra tính đa hình và giá trị trả về mà không cần hàm main.
Hệ thống kích hoạt hai tầng phòng thủ: lớp SecurityScanner quét tĩnh chuỗi mã nguồn bằng biểu thức chính quy để chặn các lời gọi nguy hiểm như Runtime.getRuntime, ProcessBuilder hoặc System.exit trước khi biên dịch; tầng thực thi SandboxRunner khởi chạy tiến trình con độc lập qua ProcessBuilder với giới hạn hai giây và 256 megabyte bộ nhớ, đi kèm luồng giám sát ngắt tiến trình tự động nếu xảy ra lặp vô hạn.
4.2 Thiết lập môi trường và kịch bản thực nghiệm
a. Môi trường thực nghiệm và bộ chỉ số đo lường hiệu năng
Thực nghiệm triển khai trên máy trạm vi xử lý Intel Core đa nhân, bộ nhớ trong 16 gigabyte RAM, hệ điều hành Windows 11 64-bit và môi trường thực thi Java JDK 17. Bộ dữ liệu kiểm thử sử dụng bài toán tính toán với 5 ca kiểm thử độc lập và tập 5 mẫu mã nguồn đại diện cho các trường hợp: chạy đúng đạt điểm tối đa, sai kết quả, lặp vô hạn, lỗi ngoại lệ thời gian chạy và lỗi cú pháp biên dịch.
Hệ thống thu thập bốn chỉ số định lượng: thời gian hoàn thành toàn bộ bài nộp tính bằng giây; thông lượng xử lý tính bằng số bài trên phút; thời gian chờ trung bình trong hàng đợi tính bằng mili-giây; và thời gian chờ tối đa của bài nộp cuối cùng trước khi được xử lý.
b. Thiết kế hai bộ thực nghiệm kiểm soát biến độc lập
Quá trình đo lường do lớp ExperimentRunner điều phối tuân thủ nguyên tắc kiểm soát biến độc lập qua hai bộ thực nghiệm:
Bộ thực nghiệm 1 đánh giá khả năng mở rộng số luồng chấm. Cố định tải 20 bài nộp từ 5 máy khách đồng thời; biến thiên số công nhân máy chủ nhận các giá trị 1 công nhân cơ sở tuần tự, 2 công nhân, 4 công nhân mô hình đề xuất và 8 công nhân thăm dò bão hòa.
Bộ thực nghiệm 2 đánh giá năng lực chịu tải quy mô bài nộp dồn dập. Cố định cấu hình 4 công nhân đề xuất; biến thiên số bài nộp nhận các giá trị 10 bài tải nhẹ, 20 bài tải trung bình, 40 bài tải cao và 60 bài tải nặng kiểm tra sức chịu đựng hàng đợi.
4.3 Kết quả thực nghiệm và đo lường định lượng
a. Hiệu năng mở rộng theo số lượng luồng chấm
Kết quả đo đạc từ bộ thực nghiệm 1 chứng minh sự vượt trội của kiến trúc xử lý song song so với mô hình xử lý tuần tự khi cùng xử lý 20 bài nộp.
Bảng 4.1: Ảnh hưởng của số lượng công nhân đến hiệu năng xử lý khi cố định 20 bài nộp
| Kịch bản thực nghiệm | Số công nhân | Số bài nộp | Thời gian giây | Thông lượng bài trên phút | Thời gian chờ TB mili-giây | Thời gian chờ Max mili-giây | Hệ số tăng tốc |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| Kịch bản A1 cơ sở tuần tự | 1 | 20 | 31.36 | 38.3 | 16544.9 | 30983 | 1.00x |
| Kịch bản A2 | 2 | 20 | 13.98 | 85.8 | 7174.2 | 13601 | 2.24x |
| Kịch bản A3 mô hình đề xuất | 4 | 20 | 7.81 | 153.6 | 4227.2 | 7480 | 4.01x |
| Kịch bản A4 | 8 | 20 | 6.69 | 179.4 | 3965.8 | 6372 | 4.69x |
Số liệu Bảng 4.1 cho thấy khi tăng số công nhân từ 1 lên 4 theo mô hình đề xuất, thời gian hoàn thành giảm từ 31.36 giây xuống 7.81 giây, đạt hệ số tăng tốc 4.01 lần. Thông lượng xử lý tăng từ 38.3 lên 153.6 bài trên phút, tương đương mức tăng 301 phần trăm. Thời gian chờ trung bình trong hàng đợi giảm 74.5 phần trăm, từ 16544.9 mili-giây xuống 4227.2 mili-giây; thời gian chờ tối đa giảm từ 30983 mili-giây xuống 7480 mili-giây, cắt giảm hơn 23 giây nghẽn tắc. Nâng lên 8 công nhân, hiệu năng đạt 179.4 bài trên phút nhưng tốc độ tăng chậm lại do tiệm cận giới hạn lõi vật lý của vi xử lý.
b. Năng lực chịu tải theo quy mô bài nộp và độ ổn định hàng đợi
Kết quả đo đạc từ bộ thực nghiệm 2 khẳng định độ ổn định của hệ thống khi quy mô bài nộp dồn dập tăng gấp 6 lần trên cấu hình 4 công nhân.
Bảng 4.2: Ảnh hưởng của quy mô tải bài nộp khi cố định cấu hình máy chủ 4 công nhân
| Kịch bản thực nghiệm | Cấu hình máy chủ | Số bài nộp | Thời gian giây | Thông lượng bài trên phút | Thời gian chờ TB mili-giây | Thời gian chờ Max mili-giây |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| Kịch bản B1 tải nhẹ | 4 công nhân | 10 | 4.20 | 142.8 | 2399.4 | 3913 |
| Kịch bản B2 tải trung bình | 4 công nhân | 20 | 8.68 | 138.2 | 4882.8 | 8376 |
| Kịch bản B3 tải cao | 4 công nhân | 40 | 15.11 | 158.8 | 7993.3 | 14728 |
| Kịch bản B4 tải nặng | 4 công nhân | 60 | 23.66 | 152.1 | 11992.0 | 23356 |
Số liệu Bảng 4.2 chứng minh thông lượng xử lý duy trì ổn định trong khoảng 138 đến 158 bài trên phút dưới mọi mức tải từ 10 đến 60 bài. Thời gian xử lý tăng tuyến tính theo khối lượng công việc mà không xuất hiện nghẽn luồng. Hàng đợi LinkedBlockingQueue hấp thụ tải tốt: toàn bộ 60 bài nộp gửi đến gần như tức thời đều được tiếp nhận an toàn trong bộ nhớ chỉ sau vài mili-giây, giữ cho kênh socket luôn thông suốt và không từ chối phục vụ.
c. Đánh giá độ chính xác và khả năng xử lý bài nộp đặc thù
Thực nghiệm kiểm chứng xác nhận động cơ phân loại chính xác 100 phần trăm các trường hợp: bài chuẩn đạt điểm tối đa, bài sai logic nhận Wrong Answer, bài ném ngoại lệ nhận Runtime Error, và bài sai cú pháp nhận Compile Error kèm nhật ký chi tiết.
Đặc biệt, cơ chế cách ly luồng phát huy hiệu quả khi gặp bài nộp lặp vô hạn. Trong mô hình tuần tự, một bài nộp treo cứng làm tê liệt toàn bộ hệ thống suốt thời gian chờ. Trong kiến trúc đề xuất, bài nộp quá hạn chỉ chiếm một luồng công nhân và bị ngắt cưỡng chế sau hai giây; ba công nhân còn lại vẫn chấm và trả kết quả liên tục, loại bỏ hoàn toàn nguy cơ nghẽn dây chuyền. Với bài tập hướng đối tượng, bộ chấm phản xạ động bắt chính xác lỗi vi phạm đóng gói và sai lệch phương thức mà phương pháp so khớp chuỗi console không thể xử lý.
4.4 Phân tích và đánh đổi kỹ thuật
a. Đánh đổi giữa chi phí tài nguyên và mức độ cách ly an toàn
Bảo đảm an toàn thực thi mã nguồn sinh viên đặt ra bài toán đánh đổi giữa mức độ cách ly và độ trễ thực thi. Cách ly bằng bộ chứa Docker cung cấp môi trường độc lập hoàn toàn với hệ thống tệp và mạng máy chủ, nhưng tiêu hao tài nguyên khởi tạo và làm tăng độ trễ mỗi bài nộp lên hàng trăm mili-giây.
Hệ thống giải quyết vấn đề bằng chiến lược phòng thủ hai tầng phối hợp: bộ quét tĩnh SecurityScanner lọc mã độc ở cấp độ cú pháp trước khi biên dịch, đi kèm cơ chế cô lập tiến trình ProcessBuilder cục bộ có giới hạn tài nguyên nghiêm ngặt. Cách tiếp cận này loại bỏ chi phí khởi động bộ chứa, duy trì độ trễ khởi chạy ở mức vài chục mili-giây, đồng thời vẫn bảo vệ máy chủ an toàn trước các mã độc phá hoại trong môi trường học đường.
b. Đánh đổi giữa đơn giản hóa kiến trúc mạng và khả năng mở rộng phân tán
Hàng đợi nội bộ LinkedBlockingQueue mang lại lợi thế gọn nhẹ, loại bỏ độ trễ mạng phụ và không yêu cầu phần mềm trung gian trong phòng máy thực hành. Đổi lại, khả năng mở rộng bị giới hạn bởi tài nguyên phần cứng của một nút máy chủ vật lý duy nhất. Khi số lượng thí sinh vượt quá năng lực phục vụ của một máy chủ, hệ thống đòi hỏi cơ chế đồng bộ trạng thái socket phức tạp hơn.
Tuy nhiên, với quy mô phòng máy thực hành từ vài chục đến vài trăm máy trạm, việc tối ưu trên một máy chủ độc lập là sự đánh đổi hoàn toàn hợp lý. Thiết kế này giúp triển khai đơn giản, vận hành khép kín không phụ thuộc dịch vụ ngoài và khai thác tối đa năng lực phần cứng đa nhân sẵn có.
