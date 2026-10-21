# Giao thức TCP

## Khung message

TCP là stream nên giao thức dùng **một JSON UTF-8 trên mỗi dòng**, kết thúc bằng newline. Không được để payload mã nguồn tạo thành message nhiều dòng ngoài JSON-escaping do Gson thực hiện.

```json
{
  "type": "LOGIN",
  "requestId": "uuid-client",
  "token": null,
  "status": null,
  "message": null,
  "data": { "username": "student01", "password": "..." }
}
```

Trường chung: `type`, `requestId`, `token`, `status`, `message`, `data`. Client sinh `requestId`; server echo lại trong response. Response thành công dùng `status: "SUCCESS"`; lỗi chung dùng `type: "ERROR"`, `status: "FAIL"`. JWT bắt buộc sau login, trừ login/register/control cơ bản.

## Nhóm message

| Nhóm | Request | Response / push |
| --- | --- | --- |
| Xác thực | `LOGIN`, `REGISTER`, `CHANGE_PASSWORD` | các `*_RESPONSE` tương ứng |
| Bài tập | `GET_PROBLEMS`, `GET_PROBLEM_DETAIL`, `CREATE_PROBLEM`, `UPDATE_PROBLEM`, `TOGGLE_PROBLEM_ACTIVE` | các `*_RESPONSE` tương ứng |
| Học vụ | `GET_COURSES`, `CREATE_COURSE`, `DELETE_COURSE`, `TOGGLE_COURSE_ACTIVE`, `GET_CLASSES`, `CREATE_CLASS`, `UPDATE_CLASS`, `DELETE_CLASS`, `GET_STUDENTS`, `IMPORT_STUDENTS`, `UPDATE_STUDENT`, `DELETE_STUDENT`, `GET_MY_CLASSES` | các `*_RESPONSE` tương ứng |
| Chấm bài | `SUBMIT`, `GET_RESULT`, `GET_HISTORY` | `SUBMIT_RESPONSE`, `GET_RESULT_RESPONSE`, `GET_HISTORY_RESPONSE`, push `JUDGE_NOTIFICATION` |
| Giám sát | `PROCTOR_EVENT`, `GET_PROCTOR_LOGS` | `PROCTOR_EVENT_RESPONSE`, `GET_PROCTOR_LOGS_RESPONSE`, push `PROCTOR_ALERT` |
| Điều khiển | `PING`, `DISCONNECT` | `PONG`, `ERROR`, push `FORCE_LOGOUT` |

## Thông điệp quan trọng

- `LOGIN`: data chứa username/password. Response thành công trả token cùng thông tin user/role.
- `SUBMIT`: data có `problemId`, `sourceCode`, `language`; phản hồi sớm với submission đang pending.
- `JUDGE_NOTIFICATION`: server chủ động gửi khi worker hoàn tất, chứa thông tin kết quả submission.
- `PROCTOR_EVENT`: client gửi `problemId`, `problemTitle`, `eventType`, `detail`; server lưu/cộng vi phạm, phản hồi cảnh báo cho sinh viên và broadcast tới giáo viên online.
- `FORCE_LOGOUT`: thông báo một phiên cũ khi cùng tài khoản được đăng ký lại trong registry.

## Xử lý lỗi/cạnh tranh

- Request không hợp lệ hoặc exception trong handler trả `ERROR`.
- JWT thiếu/hết hạn/sai trả unauthorized.
- Client tự fail callback chưa nhận phản hồi sau 30 giây.
- Disconnect dọn registry, socket và stream; việc server push chỉ thành công khi user còn online.

> Nguồn chuẩn về enum là `server/.../protocol/MessageType.java`; phía client có enum tương ứng và cần được giữ đồng bộ khi thêm API.
