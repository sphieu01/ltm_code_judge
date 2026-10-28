LẬP LUẬN BẢO VỆ KIẾN TRÚC HÀNG ĐỢI MÁY CHẤM: LINKEDBLOCKINGQUEUE SO VỚI RABBITMQ VÀ KAFKA

1. Đặt vấn đề và bối cảnh kiến trúc hệ thống

Trong thiết kế hệ thống chấm mã nguồn tự động qua mạng, hàng đợi chấm bài giữ vai trò cầu nối điều phối giữa tầng mạng tiếp nhận yêu cầu và tầng thực thi kiểm thử mã nguồn. Việc lựa chọn giải pháp hàng đợi phải cân nhắc kỹ lưỡng giữa độ trễ xử lý, chi phí tài nguyên phần cứng, độ phức tạp vận hành và mục tiêu cốt lõi của môn học Lập trình mạng.

Hệ thống hiện tại triển khai lớp JudgeQueue dựa trên cấu trúc dữ liệu LinkedBlockingQueue trong gói java.util.concurrent của Java Core. Quyết định kỹ thuật này được xây dựng trên các cơ sở thực nghiệm và lập luận khoa học rõ ràng, tránh hiện tượng lạm dụng công nghệ không cần thiết.

2. Phân tích ưu điểm kỹ thuật của giải pháp LinkedBlockingQueue

2.1. Hiệu năng tối đa và độ trễ tiệm cận 0

LinkedBlockingQueue hoạt động hoàn toàn trong không gian bộ nhớ tiến trình của máy ảo Java. Khi một bài nộp được tiếp nhận từ luồng xử lý mạng ClientHandler, bài nộp được đẩy vào hàng đợi với chi phí thời gian dưới 1 phần nghìn giây. Việc này loại bỏ hoàn toàn chi phí đóng gói gói tin mạng, bắt tay kết nối và độ trễ khứ hồi mạng vốn có khi phải gửi thông điệp sang một máy chủ trung gian độc lập.

Cấu trúc này áp dụng thuật toán khóa hai đầu độc lập gồm putLock và takeLock. Nhờ đó, thao tác ghi bài nộp của các luồng tiếp nhận mạng và thao tác lấy bài nộp của các luồng công nhân chấm thi diễn ra song song tuyệt đối, không gây tranh chấp khóa, đảm bảo thông lượng tối đa cho hệ thống.

2.2. Tối ưu hóa tài nguyên phần cứng

Mỗi phần tử trong LinkedBlockingQueue chỉ là một tham chiếu đối tượng Submission. Với kích thước mã nguồn trung bình của sinh viên từ 1 đến 5 kilobyte, một hàng đợi chứa 1000 bài nộp chỉ tiêu thụ khoảng 5 megabyte bộ nhớ RAM. Con số này là không đáng kể so với dung lượng bộ nhớ khả dụng của máy chủ.

Ngược lại, việc triển khai RabbitMQ đòi hỏi môi trường thực thi Erlang tiêu tốn tối thiểu 100 đến 200 megabyte bộ nhớ, còn Apache Kafka đòi hỏi nền tảng phân tán nặng nề từ 500 megabyte đến hơn 1 gigabyte bộ nhớ chỉ để khởi động các tiến trình nền. Đối với môi trường máy chủ thi cử tại phòng thực hành hoặc trường đại học, việc sử dụng các dịch vụ hàng đợi bên ngoài gây lãng phí tài nguyên máy tính nghiêm trọng.

2.3. Đảm bảo tính toàn vẹn dữ liệu thông qua cơ sở dữ liệu quan hệ

Một nhược điểm thông thường của hàng đợi trong bộ nhớ là rủi ro mất dữ liệu khi tiến trình máy chủ gặp sự cố sập nguồn. Tuy nhiên, hệ thống giải quyết triệt để rủi ro này bằng luồng nghiệp vụ lưu trữ kép. Mọi bài nộp đều được lưu trữ bền vững vào cơ sở dữ liệu quan hệ PostgreSQL với trạng thái chờ chấm trước khi được đưa vào JudgeQueue.

Khi máy chủ khởi động lại sau sự cố, hệ thống có thể truy vấn các bản ghi đang ở trạng thái chờ chấm trong cơ sở dữ liệu để tái thiết lập hàng đợi. Do đó, tính bền vững của dữ liệu được bảo đảm tuyệt đối mà không cần phụ thuộc vào cơ chế ghi đĩa phức tạp của RabbitMQ hay Kafka.

3. Ranh giới ứng dụng thực tế của RabbitMQ và Kafka

3.1. Trường hợp cần sử dụng RabbitMQ

RabbitMQ là giải pháp hàng đầu cho mô hình máy chấm phân tán trên nhiều cụm máy chủ vật lý độc lập. Giải pháp này chỉ thực sự cần thiết khi hệ thống phát triển lên quy mô lớn như các nền tảng lập trình trực tuyến toàn cầu. Khi đó, máy chủ tiếp nhận yêu cầu đặt tại một trung tâm dữ liệu và hàng chục máy chủ công nhân biên dịch mã nguồn đặt tại các địa điểm địa lý khác nhau. RabbitMQ đóng vai trò làm kênh phân phối tác vụ qua mạng giữa các máy chủ không chung không gian bộ nhớ.

RabbitMQ cũng phát huy hiệu quả khi hệ thống triển khai cơ chế tự động co giãn số lượng công nhân chấm bài dựa trên tải thời gian thực trong các kỳ thi quy mô hàng chục nghìn thí sinh.

3.2. Trường hợp cần sử dụng Apache Kafka

Apache Kafka được thiết kế chuyên biệt cho việc lưu trữ và xử lý luồng sự kiện phân tán với thông lượng hàng trăm nghìn thông điệp mỗi giây. Kafka phù hợp với các hệ thống phân tích dữ liệu lớn, nơi luồng nhật ký biên dịch, chỉ số tài nguyên và dữ liệu giám sát thi từ hàng vạn ứng dụng khách được thu thập liên tục để phục vụ các mô hình học máy phát hiện gian lận.

Áp dụng Kafka cho bài toán điều phối hàng đợi chấm mã nguồn thông thường là giải pháp dư thừa chức năng, làm gia tăng đáng kể độ phức tạp kiến trúc và chi phí bảo trì hệ thống.

4. Kết luận bảo vệ đồ án

Kiến trúc hàng đợi nội bộ LinkedBlockingQueue kết hợp cơ chế kiểm soát dung lượng chủ động và lưu trữ dự phòng qua cơ sở dữ liệu là giải pháp tối ưu nhất cho bài toán hệ thống chấm mã nguồn tự động trong khuôn khổ môn học Lập trình mạng. Thiết kế này tuân thủ chuẩn xác mô hình nhà sản xuất và người tiêu thụ, bảo đảm hiệu năng cao nhất, tiết kiệm tài nguyên và làm nổi bật năng lực làm chủ kỹ thuật lập trình mạng đa luồng cốt lõi.

Định hướng tích hợp RabbitMQ hoặc Kafka được xác định là hướng phát triển mở rộng trong tương lai khi bài toán đòi hỏi mở rộng sang kiến trúc cụm máy chấm phân tán đa cụm.
