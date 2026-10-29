Create a clean, professional Sequence Diagram in Mermaid syntax with Vietnamese labels representing "Biểu đồ tuần tự trao đổi giao thức mạng giữa máy khách và máy chủ qua TCP Socket":

Participants (Lifelines):
- Client as "Máy khách (JavaFX Client)"
- Server as "Máy chủ mạng (Server Network Thread)"
- Queue as "Hàng đợi (LinkedBlockingQueue)"
- Worker as "Công nhân chấm (JudgeWorker)"
- Storage as "Lưu trữ (PostgreSQL & MinIO)"

Flow sequence:

1. [Giai đoạn 1: Bắt tay mạng & Xác thực]:
   - Client -> Server: "1. Thiết lập kết nối TCP Socket (Cổng 9999)"
   - Client -> Server: "2. Gói tin AUTH_LOGIN (username, password)"
   - Server -> Storage: "3. Truy vấn kiểm tra mật khẩu băm"
   - Storage --> Server: "4. Thông tin tài khoản hợp lệ"
   - Server -> Server: "5. Sinh Session Token & Lưu bảng phiên"
   - Server -> Client: "6. Phản hồi AUTH_SUCCESS (token: uuid-xxxx)"

2. [Giai đoạn 2: Nộp bài bất đồng bộ]:
   - Client -> Server: "7. Gói tin SUBMIT_CODE (token, problemId, sourceCode)"
   - Server -> Queue: "8. Đẩy tác vụ vào hàng đợi: offer(SubmissionTask #101)"
   - Server -> Client: "9. Phản hồi tức thì SUBMIT_ACK (submissionId: 101, status: QUEUED)"
   - Note over Client: "Giao diện chuyển trạng thái 'Đang chờ chấm', luồng mạng giải phóng ngay"

3. [Giai đoạn 3: Thực thi chấm bài ngầm & Thông báo đẩy]:
   - Worker -> Queue: "10. Lấy tác vụ: take(SubmissionTask #101)"
   - Worker -> Worker: "11. Biên dịch & Chạy các ca kiểm thử Sandbox/OOP"
   - Worker -> Storage: "12. Ghi nhận điểm số, phán quyết & Lưu mã nguồn"
   - Worker -> Server: "13. Bàn giao kết quả chấm bài #101"
   - Server -> Client: "14. Chủ động gửi thông báo đẩy: PUSH_RESULT (verdict: ACCEPTED, score: 10.0)"
   - Note over Client: "Giao diện tự động cập nhật kết quả phán quyết mà không cần thăm dò (No Polling)"
