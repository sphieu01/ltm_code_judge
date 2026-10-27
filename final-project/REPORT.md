HỆ THỐNG CHẤM MÃ NGUỒN TỰ ĐỘNG PHÂN TÁN QUA MẠNG DỰA TRÊN KIẾN TRÚC HÀNG ĐỢI WORKER VÀ THÔNG BÁO ĐẨY QUA GIAO THỨC TCP

1. TÓM TẮT

Báo cáo trình bày thiết kế và cài đặt hệ thống chấm mã nguồn tự động phân tán theo mô hình máy khách - máy chủ sử dụng socket TCP kết nối bền vững và giao thức JSON phân dòng. Nhằm khắc phục tình trạng nghẽn cổ chai của mô hình chấm tuần tự truyền thống và triệt tiêu chi phí băng thông của kỹ thuật thăm dò HTTP polling, hệ thống kết hợp hàng đợi chặn bất đồng bộ với nhóm tiến trình chấm bài song song. Quy trình chấm tích hợp cơ chế phòng thủ hai tầng gồm quét tĩnh mã độc trước khi biên dịch và môi trường cô lập thực thi có kiểm soát tài nguyên, đồng thời máy chủ chủ động đẩy kết quả về máy khách theo thời gian thực ngay khi hoàn tất. Kết quả thực nghiệm chứng minh kiến trúc đề xuất nâng thông lượng xử lý bài nộp gấp hơn ba lần so với mô hình cơ sở và loại bỏ hoàn toàn lưu lượng mạng dư thừa từ phía máy khách.

2. TỪ KHÓA

Lập trình mạng, giao thức TCP, thông điệp JSON, hàng đợi worker, chấm mã nguồn tự động, thông báo đẩy thời gian thực.

3. GIỚI THIỆU VÀ ĐẶT VẤN ĐỀ

3.1. Bối cảnh và động lực

Hoạt động giảng dạy và thi cử các môn học lập trình đòi hỏi khả năng chấm điểm tự động các bài làm của người học một cách chính xác, khách quan và nhanh chóng. Khi tổ chức thực hành hoặc thi tập trung, máy chủ tiếp nhận lượng lớn bài nộp gửi lên gần như đồng thời từ hàng chục hoặc hàng trăm máy trạm trong phòng máy. Nếu máy chủ xử lý từng bài nộp theo dạng tuần tự truyền thống, các bài nộp đến sau sẽ phải chờ đợi trong thời gian dài, gây nghẽn cổ chai và làm suy giảm nghiêm trọng trải nghiệm của người dùng.

Mặt khác, các hệ thống chấm bài qua giao diện web hiện nay đa phần vận hành theo giao thức HTTP. Do đặc tính hỏi đáp một chiều của HTTP, máy khách thường phải liên tục gửi các yêu cầu thăm dò để kiểm tra xem bài nộp đã được chấm xong hay chưa. Thao tác này tiêu tốn băng thông mạng vô ích và gia tăng tải xử lý định tuyến tại máy chủ. Động lực chính của đề tài là xây dựng một hệ thống mạng chuyên dụng sử dụng socket TCP kết nối bền vững, kết hợp kiến trúc hàng đợi bất đồng bộ và cơ chế thông báo đẩy thời gian thực nhằm tối ưu hóa độ trễ, triệt tiêu lưu lượng mạng thừa và tận dụng triệt để năng lực phần cứng đa nhân của máy chủ.

3.2. Mục tiêu và phạm vi

Mục tiêu của đề tài là thiết kế và hiện thực hóa một hệ thống chấm mã nguồn tự động hoạt động theo mô hình máy khách - máy chủ tin cậy. Hệ thống tập trung giải quyết các bài toán kỹ thuật mạng gồm: phân định ranh giới thông điệp trên luồng dữ liệu TCP, duy trì kênh truyền thông hai chiều để đẩy sự kiện tức thì, phân tách tầng giao tiếp mạng với tầng thực thi chấm bài thông qua hàng đợi chặn, và thiết lập cơ chế kiểm soát an toàn trước mã độc tiềm ẩn.

Phạm vi đề tài tập trung vào việc đánh giá mã nguồn các ngôn ngữ C, C++, Python và Java trong môi trường mạng nội bộ hoặc kết nối qua đường hầm TCP trên cổng dịch vụ 9999. Hệ thống bao gồm ứng dụng máy khách đồ họa và máy chủ Java đa luồng có kết nối cơ sở dữ liệu quan hệ, đi kèm bộ công cụ đo lường thực nghiệm tự động để đánh giá hiệu năng hệ thống dưới các kịch bản tải khác nhau.

3.3. Các đóng góp chính

Báo cáo tập trung vào bốn đóng góp kỹ thuật cốt lõi:

Thứ nhất, thiết kế giao thức ứng dụng JSON phân dòng trên kết nối TCP bền vững, tích hợp xác thực mã thông báo và cơ chế máy chủ chủ động đẩy kết quả, loại bỏ hoàn toàn chi phí thăm dò từ máy khách.

Thứ hai, triển khai kiến trúc xử lý bất đồng bộ phân tầng giữa luồng mạng và luồng thực thi thông qua hàng đợi chặn kết hợp nhóm công nhân chấm bài song song, nâng cao đáng kể thông lượng xử lý bài nộp.

Thứ ba, thiết lập cơ chế an toàn đa tầng bảo vệ máy chủ, kết hợp giữa bộ quét tĩnh mã độc trong không thời gian và môi trường cô lập thực thi có kiểm soát giới hạn tài nguyên.

Thứ tư, xây dựng bộ đo lường thực nghiệm tự động hóa, cung cấp số liệu đối sánh định lượng về thông lượng, độ trễ hàng đợi và mức độ tiết kiệm lưu lượng mạng so với mô hình xử lý tuần tự cơ sở.

4. KIẾN THỨC NỀN VÀ CÁC GIẢI PHÁP LIÊN QUAN

4.1. Kiến thức nền tảng về lập trình mạng và xử lý đồng thời

Giao thức điều khiển truyền vận TCP hoạt động ở tầng giao vận, cung cấp kênh truyền dữ liệu tin cậy, định hướng kết nối và bảo toàn thứ tự các byte truyền giữa hai đầu mút. Khác với giao thức UDP hoạt động theo từng khối dữ liệu độc lập, TCP truyền tải dữ liệu dưới dạng một dòng byte liên tục. Do đó, các ứng dụng mạng chạy trên nền TCP bắt buộc phải cài đặt kỹ thuật đóng gói và phân định ranh giới thông điệp để máy nhận có thể nhận biết chính xác điểm bắt đầu và điểm kết thúc của từng gói tin, tránh hiện tượng dính gói hoặc phân mảnh dữ liệu khi nhiều thông điệp được truyền liên tiếp trên cùng một kết nối.

Về phương diện xử lý đồng thời, mô hình máy chủ mạng hiện đại dựa trên thư viện luồng và cấu trúc nhóm luồng định sẵn. Việc giới hạn số lượng luồng thực thi trong một ngưỡng an toàn giúp hệ thống kiểm soát mức độ chiếm dụng bộ nhớ và chu kỳ xử lý của bộ vi xử lý trung tâm, tránh tình trạng sụp đổ máy chủ khi số lượng kết nối tăng đột biến. Bên cạnh đó, mô hình nhà sản xuất và người tiêu dùng kết hợp cấu trúc hàng đợi chặn cho phép luồng tiếp nhận mạng đưa dữ liệu vào hàng đợi mà không cần chờ đợi kết quả xử lý, tạo nên tính chất bất đồng bộ cho toàn bộ quy trình dịch vụ.

4.2. Các hệ thống tương tự và hạn chế

Các hệ thống chấm mã nguồn trực tuyến phổ biến hiện nay đa số được xây dựng trên nền tảng ứng dụng web sử dụng giao thức HTTP. Do bản chất của HTTP là mô hình giao tiếp một chiều kích hoạt bởi máy khách, sau khi gửi mã nguồn thành công, máy khách buộc phải thực hiện kỹ thuật thăm dò định kỳ để liên tục kiểm tra trạng thái chấm điểm từ máy chủ. Trong môi trường phòng máy thi tập trung, hàng nghìn yêu cầu thăm dò lặp lại liên tục tạo ra tải xử lý tiêu đề gói tin không cần thiết cho máy chủ và chiếm dụng băng thông của đường truyền mạng nội bộ.

Đối với các ứng dụng chấm mã nguồn nội bộ quy mô nhỏ, nhiều hệ thống lựa chọn giải pháp xử lý tuần tự ngay trên tiến trình chính để giảm thiểu độ phức tạp trong lập trình. Khi một bài nộp chứa giải thuật có độ phức tạp thời gian lớn hoặc phát sinh lỗi chạy vô hạn, toàn bộ các bài nộp phía sau đều bị nghẽn lại trong hàng đợi chờ. Thời gian chờ đợi của người học bị tăng lũy tiến theo hàm tuyến tính, gây nên hiện tượng nghẽn cổ chai cục bộ. Những hạn chế này khẳng định sự cần thiết của việc xây dựng một hệ thống mạng sử dụng socket TCP bền vững, áp dụng cơ chế phân dòng thông điệp và xử lý chấm song song qua hàng đợi bất đồng bộ.
