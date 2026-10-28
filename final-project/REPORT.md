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

Các hệ thống chấm mã nguồn trực tuyến phổ biến hiện nay đa số được xây dựng trên nền tảng ứng dụng web sử dụng giao thức HTTP. Do bản chất của HTTP là mô hình giao tiếp một chiều kích hoạt bởi máy khách, sau khi gửi mã nguồn thành công, máy khách buộc phải thực hiện kỹ thuật thăm dò định kỳ để liên tục kiểm tra trạng thái chấm điểm từ máy chủ. Khi có nhiều người dùng cùng nộp bài, hàng nghìn yêu cầu thăm dò lặp lại liên tục tạo ra tải xử lý tiêu đề gói tin không cần thiết cho máy chủ và chiếm dụng băng thông đường truyền mạng.

Đối với các ứng dụng chấm mã nguồn nội bộ quy mô nhỏ, nhiều hệ thống lựa chọn giải pháp xử lý tuần tự ngay trên tiến trình chính để giảm thiểu độ phức tạp trong lập trình. Khi một bài nộp chứa giải thuật có độ phức tạp thời gian lớn hoặc phát sinh lỗi chạy vô hạn, toàn bộ các bài nộp phía sau đều bị nghẽn lại trong hàng đợi chờ. Thời gian chờ đợi của người học bị tăng lũy tiến theo hàm tuyến tính, gây nên hiện tượng nghẽn cổ chai cục bộ. Những hạn chế này khẳng định sự cần thiết của việc xây dựng một hệ thống mạng sử dụng socket TCP bền vững, áp dụng cơ chế phân dòng thông điệp và xử lý chấm song song qua hàng đợi bất đồng bộ.

5. ĐỊNH NGHĨA BÀI TOÁN

5.1. Mô hình bài toán mạng

Hệ thống vận hành theo mô hình máy khách - máy chủ phân tán. Máy khách là ứng dụng đồ họa cài đặt độc lập trên máy tính cá nhân của người học và giảng viên, kết nối tới máy chủ trung tâm qua giao thức TCP trên cổng dịch vụ 9999 thông qua mạng nội bộ hoặc đường hầm mạng. Kênh truyền giữa mỗi máy cá nhân và máy chủ là một kết nối socket hai chiều bền vững, được duy trì trong suốt phiên làm việc. Bài toán kỹ thuật cốt lõi là điều phối đồng thời nhiều kết nối mạng song song, đồng thời phân tách luồng nhận dữ liệu mạng với luồng thực thi chấm bài để tránh tình trạng nghẽn đường truyền khi nhiều máy cá nhân cùng nộp bài một lúc.

5.2. Các yêu cầu chức năng mạng

Hệ thống đáp ứng ba nhóm chức năng mạng chính:

Thứ nhất, quản lý phiên và xác thực người dùng. Máy khách gửi thông tin đăng nhập qua socket TCP; máy chủ xác thực tài khoản, cấp mã JWT và lưu trữ kênh socket vào bảng đăng ký kết nối để phục vụ điều phối truyền thông hai chiều.

Thứ hai, tiếp nhận bài nộp và đẩy kết quả theo thời gian thực. Máy khách gửi mã nguồn qua kênh TCP; luồng mạng máy chủ lập tức trả về gói tin xác nhận kèm mã bài nộp và chuyển bài vào hàng đợi xử lý. Khi nhóm tiến trình chấm bài hoàn tất, máy chủ chủ động đẩy kết quả về kết nối socket của máy khách mà không cần máy khách gửi truy vấn thăm dò.

Thứ ba, đồng bộ dữ liệu học vụ và giám sát gian lận. Hệ thống truyền nhận danh mục bài tập, dữ liệu kiểm thử và kết quả học tập giữa máy chủ và máy cá nhân của người dùng. Máy khách đồng thời ghi nhận và gửi các nhật ký cảnh báo chuyển đổi cửa sổ ứng dụng về máy chủ để phục vụ công tác giám sát.

5.3. Các yêu cầu phi chức năng

Hệ thống tuân thủ ba yêu cầu phi chức năng kỹ thuật:

Thứ nhất, tính toàn vẹn và phân định ranh giới thông điệp. Thông điệp mạng được phân tách bằng ký tự xuống dòng trên dòng byte TCP, bảo đảm dữ liệu không bị phân mảnh hoặc dính gói.

Thứ hai, năng lực xử lý đồng thời. Máy chủ sử dụng nhóm luồng cố định tiếp nhận kết nối mạng kết hợp hàng đợi đệm và nhóm tiến trình chấm bài độc lập nhằm duy trì thông lượng ổn định dưới tải cao.

Thứ ba, an toàn thực thi và giới hạn tài nguyên. Mỗi bài nộp được kiểm soát chặt chẽ với giới hạn thời gian chạy tối đa mặc định 1000 mili-giây và bộ nhớ tối đa 512 megabyte, ngăn ngừa nguy cơ cạn kiệt tài nguyên hệ thống do mã nguồn độc hại hoặc vòng lặp vô hạn.

6. GIẢI PHÁP ĐỀ XUẤT

6.1. Tổng quan giải pháp và nguyên lý tiếp cận

Để giải quyết bài toán nghẽn cổ chai và chi phí thăm dò mạng, giải pháp đề xuất kết hợp kết nối TCP bền vững, hàng đợi chặn bất đồng bộ và cơ chế thông báo đẩy thời gian thực từ máy chủ. Thay vì để luồng tiếp nhận mạng trực tiếp biên dịch và chạy thử mã nguồn, máy chủ nhanh chóng gửi gói tin xác nhận cho máy khách rồi đưa yêu cầu vào hàng đợi nội bộ. Một nhóm tiến trình công nhân độc lập đảm nhiệm việc lấy bài nộp ra chấm điểm song song, sau đó truy vấn bảng đăng ký kết nối để chủ động đẩy kết quả về máy khách thông qua chính socket đang mở.

6.2. Mô hình kiến trúc phân tầng

Kiến trúc hệ thống được chia thành ba tầng tách biệt:

Tầng thứ nhất là ứng dụng máy khách đồ họa, tích hợp giao diện người dùng, bộ quản lý phiên làm việc, module giám sát thao tác chuyển đổi ứng dụng và một luồng máy khách chuyên trách duy trì kết nối TCP với máy chủ.

Tầng thứ hai là tầng tiếp nhận mạng tại máy chủ, bao gồm một cổng tiếp nhận socket lắng nghe trên cổng 9999, nhóm luồng xử lý yêu cầu mạng được cấu hình linh hoạt theo quy mô phòng thi cùng bảng đăng ký kết nối lưu trữ trạng thái phiên và kênh socket của từng người dùng đang hoạt động.

Tầng thứ ba là tầng chấm điểm và xử lý nghiệp vụ, bao gồm hàng đợi chặn có sức chứa 100 phần tử, nhóm 4 tiến trình công nhân chấm bài song song, bộ quét tĩnh mã nguồn, module chấm bài hướng đối tượng và môi trường cô lập thực thi có kiểm soát giới hạn tài nguyên.

6.3. Luồng xử lý dữ liệu và điều phối thông điệp

Quy trình điều phối thông điệp cho một bài nộp diễn ra qua các bước tuần tự:

Bước đầu tiên, máy khách chuyển đổi mã nguồn và thông tin bài nộp thành chuỗi JSON phân tách bằng ký tự xuống dòng và truyền qua socket TCP.

Bước thứ hai, luồng mạng tại máy chủ đọc dòng dữ liệu, xác thực mã phân quyền người dùng, thực hiện quét tĩnh kiểm tra mã độc sơ bộ, lưu trạng thái bài nộp ban đầu và gửi phản hồi xác nhận tiếp nhận thành công về máy khách trong thời gian mili-giây.

Bước thứ ba, luồng mạng đẩy bài nộp vào hàng đợi chặn. Tiến trình công nhân chấm bài rảnh rỗi sẽ lấy bài ra, kích hoạt quy trình biên dịch và chạy thử trên các bộ dữ liệu kiểm thử bên trong môi trường cách ly.

Bước thứ tư, sau khi hoàn tất so khớp kết quả hoặc kiểm tra đặc tả hướng đối tượng, tiến trình công nhân cập nhật kết quả vào cơ sở dữ liệu, tra cứu kênh socket tương ứng của người nộp trong bảng đăng ký kết nối và đẩy trực tiếp thông điệp kết quả chấm về máy khách.

6.4. So sánh với các phương pháp truyền thống

So với mô hình chấm tuần tự truyền thống, kiến trúc hàng đợi công nhân song song phân tách hoàn toàn thời gian phản hồi mạng khỏi thời gian chấm bài, ngăn chặn hiện tượng một bài nộp lỗi hoặc chạy lâu làm tắc nghẽn toàn bộ hệ thống. So với mô hình thăm dò HTTP định kỳ, việc duy trì socket TCP kết hợp cơ chế máy chủ chủ động đẩy kết quả giúp triệt tiêu hoàn toàn các gói tin thăm dò dư thừa, tiết kiệm băng thông và giảm thiểu độ trễ nhận kết quả cho người học trên máy tính cá nhân.

7. KIẾN TRÚC HỆ THỐNG

Hình 1: Sơ đồ kiến trúc phân tầng của hệ thống.

[ Client: JavaFX UI + SocketClient ] 
          | (Kênh TCP hai chiều, Cổng 9999, JSON phân dòng)
          v
[ Server: SocketServer (Port 9999) + ClientHandler Pool ]
          |--- Quản lý kết nối & đẩy kết quả: ConnectionRegistry
          |--- Bộ đệm bất đồng bộ: JudgeQueue (Sức chứa 100)
          v
[ Engine Chấm bài: 4 JudgeWorker ] ---> [ Database: PostgreSQL (HikariCP) ]
          |--- Tầng 1: SecurityScanner (Quét tĩnh mã độc)
          |--- Tầng 1.5: OopReflectionGrader (Kiểm tra phản chiếu OOP)
          |--- Tầng 2: SandboxRunner (Docker cách ly / Fallback tiến trình)

Kiến trúc hệ thống được thiết kế theo mô hình phân tầng hướng sự kiện, bảo đảm phân tách hoàn toàn giữa tầng tiếp nhận mạng và tầng thực thi tính toán. Phía máy khách là ứng dụng JavaFX độc lập trên máy tính cá nhân của người dùng, tích hợp luồng nền liên tục lắng nghe kênh socket để tiếp nhận dữ liệu đẩy từ máy chủ mà không làm đơ giao diện đồ họa.

Tại máy chủ, cổng tiếp nhận socket điều phối các kết nối mạng thông qua nhóm luồng xử lý yêu cầu song song được cấu hình linh hoạt theo quy mô phòng thi, mặc định 20 luồng trong kịch bản thử nghiệm và dễ dàng mở rộng lên hàng trăm luồng trong các kỳ thi thực tế. Nhằm tránh nghẽn đường truyền khi có nhiều bài nộp gửi lên cùng thời điểm, máy chủ sử dụng hàng đợi chặn đóng vai trò bộ đệm trung gian. Nhóm 4 tiến trình công nhân chấm bài độc lập lấy bài khỏi hàng đợi để thực thi biên dịch và so khớp kết quả trong môi trường cách ly có giới hạn 1000 mili-giây và 512 megabyte bộ nhớ.

Sau khi hoàn tất quy trình chấm, tiến trình công nhân cập nhật kết quả vào cơ sở dữ liệu PostgreSQL và tra cứu bảng đăng ký kết nối để chủ động đẩy gói tin kết quả tức thì về chính socket của máy khách đã gửi bài.

8. THIẾT KẾ TRUYỀN THÔNG MẠNG

8.1. Lựa chọn giao thức tầng giao vận và cổng dịch vụ

Hệ thống sử dụng giao thức TCP ở tầng giao vận và cổng dịch vụ 9999. Khác với các ứng dụng truyền phát đa phương tiện có thể chấp nhận tỷ lệ thất thoát gói tin nhất định, hệ thống chấm điểm tự động đòi hỏi độ tin cậy tuyệt đối về tính toàn vẹn dữ liệu. Quá trình truyền tải mã nguồn giải thuật từ máy cá nhân lên máy chủ chỉ cần xảy ra hiện tượng mất mát hoặc sai lệch một byte dữ liệu cũng dẫn đến lỗi biên dịch hoặc làm sai lệch kết quả thực thi. Cơ chế bắt tay ba bước, kiểm soát luồng, phát hiện lỗi và truyền lại của giao thức TCP bảo đảm mọi dòng mã nguồn và kết quả đánh giá được chuyển giao chính xác và nguyên vẹn. Cổng 9999 được ấn định làm cổng lắng nghe mặc định, thuộc dải cổng đăng ký của hệ thống, giúp người dùng dễ dàng cấu hình quy tắc tường lửa và chuyển tiếp cổng qua các công cụ tạo đường hầm mạng khi kết nối máy khách từ xa qua Internet.

8.2. Thiết kế giao thức tầng ứng dụng

Giao thức tầng ứng dụng được thiết kế theo định dạng văn bản JSON hai chiều, kết hợp hai mô hình tương tác cơ bản của lập trình mạng:

Mô hình yêu cầu và phản hồi đồng bộ được áp dụng cho các tác vụ mang tính tức thời như đăng nhập tài khoản, đăng ký người dùng, tra cứu danh mục bài tập, môn học và lớp tín chỉ. Máy khách gửi một gói tin yêu cầu và chờ nhận gói tin phản hồi tương ứng trên cùng luồng giao tiếp.

Mô hình hướng sự kiện bất đồng bộ được áp dụng cho luồng nộp và chấm mã nguồn. Khi máy khách gửi gói tin nộp bài, luồng mạng của máy chủ lập tức trả về mã xác nhận tiếp nhận thành công để giải phóng máy khách. Sau khi nhóm tiến trình công nhân chấm xong bài nộp trong nền, máy chủ chủ động khởi tạo thông điệp sự kiện chứa kết quả chi tiết và đẩy ngược về máy khách mà không cần máy khách gửi thêm bất kỳ yêu cầu nào.

8.3. Cơ chế phân định ranh giới thông điệp

Một trong những thách thức cốt lõi của lập trình mạng trên nền TCP là tính chất truyền theo dòng byte liên tục, không có khái niệm ranh giới gói tin ở tầng ứng dụng. Do giải thuật Nagle và bộ đệm tầng giao vận của hệ điều hành, nhiều thông điệp nhỏ gửi liên tiếp có thể bị gộp lại thành một khối dữ liệu lớn gây dính gói, hoặc một khối mã nguồn lớn có thể bị chia nhỏ thành nhiều phần gây phân mảnh gói tin.

Để giải quyết triệt để vấn đề này, hệ thống áp dụng kỹ thuật phân tách ranh giới bằng ký tự xuống dòng tiêu chuẩn. Mỗi thông điệp JSON khi gửi đi bắt buộc phải được đóng gói trên một dòng đơn và kết thúc bằng ký tự xuống dòng. Phía nhận sử dụng bộ đọc đệm với phương thức đọc theo từng dòng văn bản để tách biệt chính xác từng gói tin. Phương thức này bảo đảm bộ giải mã cú pháp JSON luôn nhận được chuỗi dữ liệu hoàn chỉnh, triệt tiêu hoàn toàn lỗi cú pháp do phân mảnh hoặc dính gói trên đường truyền mạng.

8.4. Định dạng và cấu trúc các gói tin JSON

Mọi gói tin giao tiếp trong hệ thống đều tuân thủ cấu trúc đối tượng JSON thống nhất:

Gói tin yêu cầu từ máy khách gửi lên máy chủ bao gồm ba trường dữ liệu chính: trường action xác định mã định danh hành vi nghiệp vụ cần thực hiện, trường token chứa mã phân quyền JWT nhằm bảo đảm an toàn truy cập, và trường data chứa toàn bộ tham số hoặc nội dung mã nguồn của bài nộp.

Gói tin phản hồi từ máy chủ trả về máy khách bao gồm ba trường dữ liệu chuẩn: trường status nhận giá trị thành công hoặc lỗi, trường message cung cấp thông tin giải thích chi tiết, và trường data chứa dữ liệu kết quả nghiệp vụ dạng đối tượng hoặc mảng danh sách.

8.5. Cơ chế quản lý kết nối và giữ kết nối

Kết nối socket giữa ứng dụng máy khách và máy chủ được duy trì liên tục trong suốt phiên làm việc của người dùng. Việc duy trì kết nối bền vững giúp loại bỏ hoàn toàn độ trễ bắt tay ba bước của TCP cho từng lượt yêu cầu, đồng thời tạo tiền đề cho kênh đẩy dữ liệu ngược từ máy chủ.

Máy chủ cài đặt cơ chế đóng kết nối an toàn để thu hồi tài nguyên kịp thời. Luồng tiếp nhận mạng liên tục lắng nghe dòng dữ liệu từ socket; khi phát hiện tín hiệu kết thúc dòng do máy khách chủ động thoát ứng dụng hoặc khi phát sinh ngoại lệ mạng do đứt kết nối vật lý, máy chủ tự động gỡ định danh người dùng khỏi bảng quản lý phiên, đóng các luồng xuất nhập và giải phóng socket để hoàn trả chỉ số tệp cho hệ điều hành.

8.6. Cơ chế máy chủ chủ động đẩy kết quả

Cơ chế đẩy kết quả thời gian thực là giải pháp loại bỏ hoàn toàn chi phí băng thông của kỹ thuật thăm dò định kỳ. Tại máy chủ, thành phần đăng ký kết nối lưu trữ một bảng ánh xạ an toàn luồng giữa tên tài khoản người dùng và kênh socket vật lý đang hoạt động.

Khi một tiến trình công nhân hoàn tất việc chấm bài, tiến trình này gọi trực tiếp hàm đẩy thông điệp của bảng đăng ký kết nối kèm theo tên người nộp bài. Bảng đăng ký tra cứu kênh truyền tương ứng và ghi chuỗi JSON kết quả xuống luồng xuất của socket. Tại máy khách, một luồng nền độc lập luôn túc trực lắng nghe socket sẽ tiếp nhận ngay thông điệp kết quả và thông báo cho luồng giao diện cập nhật trạng thái hiển thị tức thì cho người dùng.

9. HIỆN THỰC HÓA HỆ THỐNG

9.1. Môi trường phát triển và các module chính

Hệ thống được phát triển trên nền tảng Java 17 LTS, sử dụng Apache Maven để quản lý thư viện và tự động hóa quá trình đóng gói. Mã nguồn được chia thành bốn phân hệ chức năng: phân hệ truyền thông mạng phụ trách kênh truyền socket TCP; phân hệ quản trị học vụ phụ trách dữ liệu người dùng, lớp học và bài tập lưu trên cơ sở dữ liệu PostgreSQL; phân hệ động cơ chấm điểm đảm nhiệm cách ly và đánh giá mã nguồn; phân hệ giám sát theo dõi tính trung thực trên máy khách.

9.2. Các lớp mạng quan trọng và thành phần truyền thông

Phía máy chủ gồm ba lớp mạng trung tâm: lớp SocketServer quản lý cổng lắng nghe 9999 thông qua ServerSocket tiêu chuẩn; lớp ClientHandler thực thi giao diện Runnable để quản lý phiên trao đổi thông điệp độc lập với từng máy khách qua bộ đọc BufferedReader và bộ ghi PrintWriter; lớp ConnectionRegistry duy trì bảng định tuyến các socket đang hoạt động để phục vụ cơ chế đẩy thông báo.

Phía máy khách gồm lớp SocketClient quản lý vòng đời kết nối socket TCP. Lớp này khởi chạy luồng nền ListenerThread túc trực lắng nghe dòng dữ liệu đến, tách biệt hoàn toàn với luồng giao diện người dùng JavaFX.

9.3. Cấu trúc dữ liệu và giải thuật cốt lõi

Hệ thống ứng dụng các cấu trúc dữ liệu và giải thuật chuyên biệt để tối ưu hóa hiệu năng mạng:

Về cấu trúc dữ liệu, lớp JudgeQueue sử dụng cấu trúc hàng đợi chặn LinkedBlockingQueue có giới hạn để lưu trữ các bài nộp chờ chấm, bảo đảm tính toàn vẹn dữ liệu giữa các luồng. Lớp ConnectionRegistry sử dụng bảng băm ConcurrentHashMap để ánh xạ an toàn giữa tên tài khoản và kênh truyền socket. Phía máy khách, đối tượng CompletableFuture được sử dụng để quản lý trạng thái chờ dữ liệu của các yêu cầu hỏi đáp đồng bộ.

Về giải thuật, hệ thống cài đặt giải thuật phân tách thông điệp dựa trên ký tự phân cách xuống dòng, giải quyết triệt để bài toán dính gói trên luồng byte TCP. Giải thuật quét tĩnh SecurityScanner ứng dụng biểu thức chính quy để phân tích cú pháp mã nguồn nhằm chặn đứng các lời gọi hệ thống độc hại trước khi biên dịch. Giải thuật so khớp OutputComparator thực hiện chuẩn hóa ký tự xuống dòng và khoảng trắng dư thừa trước khi so sánh văn bản. Module OopReflectionGrader ứng dụng giải thuật duyệt phản chiếu của Java Reflection để kiểm tra tính hợp lệ của cây thừa kế, thuộc tính và phương thức đối với các bài tập hướng đối tượng.

9.4. Cơ chế xử lý đồng thời và bảo đảm an toàn luồng

Hệ thống kết hợp ba cơ chế xử lý đồng thời cốt lõi:

Thứ nhất, mô hình nhóm luồng cố định ExecutorService được sử dụng tại SocketServer để giới hạn số lượng luồng mạng tối đa theo quy mô phòng thi, ngăn chặn nguy cơ cạn kiệt bộ nhớ khi số lượng kết nối tăng cao.

Thứ hai, mô hình nhà sản xuất và người tiêu dùng kết nối luồng mạng với luồng chấm điểm. Luồng ClientHandler đóng vai trò nhà sản xuất nạp bài vào JudgeQueue, trong khi nhóm 4 luồng JudgeWorker đóng vai trò người tiêu dùng lấy bài ra chấm song song, bảo đảm luồng mạng luôn phản hồi trong vài mili-giây.

Thứ ba, an toàn dữ liệu đa luồng được bảo đảm bằng biến cờ volatile phục vụ quy trình đóng máy chủ an toàn, biến đếm nguyên tử AtomicInteger thu thập chỉ số kết nối, và cơ chế chuyển tiếp Platform.runLater đưa dữ liệu từ luồng nền socket về cập nhật luồng giao diện JavaFX mà không gây xung đột luồng.

9.5. Cơ chế cách ly thực thi và giám sát gian lận

Động cơ chấm bài tích hợp lớp SandboxRunner điều phối việc biên dịch và chạy thử mã nguồn. Bộ chạy ưu tiên kích hoạt bộ chứa Docker với các thông số ngắt kết nối mạng ngoài, giới hạn thời gian chạy 1000 mili-giây và bộ nhớ 512 megabyte. Trường hợp không có Docker, hệ thống kích hoạt lớp tiến trình cục bộ có luồng hẹn giờ giám sát chặt chẽ phương thức chờ tiến trình.

Phía máy khách cài đặt module giám sát AiToolDetector kết hợp trình lắng nghe mất tiêu điểm cửa sổ. Mọi hành vi chuyển đổi màn hình hoặc kích hoạt ứng dụng hỗ trợ trái phép được đóng gói thành gói tin sự kiện gửi qua socket TCP về máy chủ để lưu vết vào bảng nhật ký giám sát.

10. THIẾT LẬP THỰC NGHIỆM

10.1. Môi trường phần cứng và hệ điều hành

Môi trường thực nghiệm được triển khai trên hệ thống máy tính cá nhân sử dụng hệ điều hành Windows 11 64-bit, bộ vi xử lý đa nhân và bộ nhớ trong 16 gigabyte RAM. Môi trường thực thi sử dụng máy ảo Java phiên bản 17 LTS với các tham số phân bổ bộ nhớ tiêu chuẩn cho máy chủ và công cụ biên dịch dòng lệnh tích hợp sẵn.

10.2. Cấu hình mạng và tham số máy chủ

Máy chủ socket vận hành trên cổng dịch vụ TCP 9999. Hàng đợi bài nộp được cấu hình sức chứa tối đa 100 phần tử. Giới hạn thời gian thực thi cho mỗi bài nộp được thiết lập mặc định ở ngưỡng 1000 mili-giây và giới hạn bộ nhớ tối đa là 512 megabyte. Môi trường thực thi kích hoạt cơ chế cách ly tiến trình cục bộ có luồng hẹn giờ giám sát chặt chẽ.

10.3. Tập dữ liệu kiểm thử và các kịch bản thực nghiệm

Tập dữ liệu thực nghiệm bao gồm bài toán tính toán với 5 bộ kiểm thử độc lập. Tập mã nguồn thử nghiệm gồm 5 dạng bài nộp đại diện cho các hành vi thực tế của người học: bài làm chính xác đạt điểm tối đa, bài làm sai đáp số, bài làm rơi vào vòng lặp vô hạn gây quá thời gian, bài làm phát sinh lỗi ngoại lệ chia cho số 0, và bài làm mắc lỗi cú pháp biên dịch.

Hệ thống thiết lập 4 kịch bản thực nghiệm nhằm đánh giá toàn diện năng lực mạng:

Kịch bản S1 kiểm thử đơn lẻ với 1 máy khách nộp 10 bài liên tiếp để xác định độ trễ cơ sở của hệ thống.

Kịch bản S2 kiểm thử tải trung bình với 5 máy khách gửi đồng thời 4 bài nộp mỗi máy, tổng cộng 20 bài nộp lên máy chủ sử dụng 4 luồng công nhân.

Kịch bản S3 kiểm thử tải cao với 10 máy khách gửi đồng thời 4 bài nộp mỗi máy, tổng cộng 40 bài nộp lên máy chủ sử dụng 4 luồng công nhân.

Kịch bản S4 đối sánh trực diện giữa mô hình cơ sở vận hành 1 luồng chấm tuần tự và mô hình đề xuất vận hành 4 luồng chấm song song kết hợp hàng đợi đệm, cùng xử lý 20 bài nộp ngẫu nhiên gửi đến đồng thời.

11. KẾT QUẢ THỰC NGHIỆM

11.1. Bảng số liệu đo lường thực tế các kịch bản tải

Kết quả đo đạc thực nghiệm tự động ghi nhận các chỉ số hiệu năng thực tế được tổng hợp như sau:

Kịch bản S1 (Đơn lẻ, 1 công nhân, 10 bài nộp): thời gian hoàn thành 8.27 giây; thông lượng xử lý đạt 72.5 bài trên phút; thời gian chờ trong hàng đợi trung bình 4707.2 mili-giây; thời gian chờ tối đa 8270 mili-giây.

Kịch bản S2 (Tải trung bình, 4 công nhân, 20 bài nộp): thời gian hoàn thành 6.14 giây; thông lượng xử lý đạt 195.4 bài trên phút; thời gian chờ trong hàng đợi trung bình 3261.9 mili-giây; thời gian chờ tối đa 5867 mili-giây.

Kịch bản S3 (Tải cao, 4 công nhân, 40 bài nộp): thời gian hoàn thành 13.01 giây; thông lượng xử lý đạt 184.5 bài trên phút; thời gian chờ trong hàng đợi trung bình 6706.1 mili-giây; thời gian chờ tối đa 12772 mili-giây.

11.2. So sánh hiệu năng giữa mô hình cơ sở và mô hình đề xuất

Dưới cùng kịch bản tải 20 bài nộp đồng thời, sự khác biệt giữa mô hình cơ sở tuần tự và mô hình đề xuất đa công nhân được ghi nhận cụ thể:

Về tổng thời gian hoàn thành toàn bộ bài nộp: mô hình cơ sở mất 16.08 giây, trong khi mô hình đề xuất chỉ mất 6.62 giây, đạt tỷ lệ tăng tốc nhanh hơn 2.43 lần.

Về thông lượng xử lý hệ thống: mô hình cơ sở chỉ đạt 74.6 bài trên một phút, trong khi mô hình đề xuất đạt tới 181.2 bài trên một phút, tương ứng mức cải thiện thông lượng tăng 142.7 phần trăm.

Về thời gian chờ đợi trung bình trong hàng đợi: mô hình cơ sở ghi nhận mức chờ trung bình lên tới 8446.7 mili-giây, trong khi mô hình đề xuất giảm mạnh xuống còn 3090.0 mili-giây, tương đương mức cắt giảm 63.4 phần trăm thời gian chờ đợi của người học.

Về thời gian chờ đợi tối đa của bài nộp cuối cùng: mô hình cơ sở ghi nhận mức chờ cao nhất là 15857 mili-giây, trong khi mô hình đề xuất giới hạn mức chờ tối đa ở ngưỡng 6370 mili-giây, rút ngắn độ trễ đỉnh điểm được 9487 mili-giây.

11.3. Phân tích độ trễ hàng đợi và mức độ tiêu thụ băng thông

Số liệu thực nghiệm phản ánh rõ nét bản chất của hiện tượng nghẽn cổ chai trong mô hình chấm tuần tự: khi một bài nộp gặp sự cố vòng lặp vô hạn chiếm dụng toàn bộ 1000 mili-giây giới hạn, toàn bộ các bài nộp phía sau đều bị ứ đọng, đẩy thời gian chờ tối đa lên gần 16 giây. Ngược lại, kiến trúc hàng đợi đệm kết hợp 4 luồng công nhân độc lập giúp cô lập hoàn toàn bài nộp quá hạn trong 1 luồng riêng biệt, 3 luồng còn lại tiếp tục tiêu thụ các bài nộp khác trong hàng đợi mà không làm tê liệt hệ thống. Đồng thời, cơ chế kết nối socket bền vững và thông báo đẩy giúp hệ thống tiết kiệm hoàn toàn lưu lượng mạng dư thừa so với kỹ thuật thăm dò HTTP, giữ độ trễ truyền tin từ thời điểm chấm xong đến khi người học nhận được kết quả ở mức dưới 10 mili-giây.
