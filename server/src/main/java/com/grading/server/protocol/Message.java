package com.grading.server.protocol;

import com.google.gson.JsonElement;

/**
 * Base message class — dùng chung cho cả Request và Response.
 *
 * Mỗi message truyền qua TCP socket là 1 dòng JSON kết thúc bằng '\n'.
 * Ví dụ:
 * {"type":"LOGIN","requestId":"req-001","token":null,"data":{...}}\n
 */
public class Message {

    /** Loại message — PHẢI có trong mọi message */
    private MessageType type;

    /**
     * ID duy nhất của request — client tự sinh (UUID hoặc counter).
     * Server echo lại trong response để client biết response này
     * tương ứng với request nào.
     */
    private String requestId;

    /**
     * JWT token — bắt buộc với mọi request sau khi đăng nhập.
     * null với LOGIN và REGISTER.
     */
    private String token;

    /**
     * Trạng thái response: "SUCCESS" | "FAIL"
     * null trong request, được set trong response.
     */
    private String status;

    /**
     * Thông điệp con người đọc được.
     * VD: "Login successful", "Invalid token", ...
     */
    private String message;

    /**
     * Payload JSON thực sự.
     * Kiểu JsonElement để có thể chứa bất kỳ cấu trúc JSON nào.
     * Được deserialize thành object cụ thể ở từng handler.
     */
    private JsonElement data;

    // ─── Constructors ─────────────────────────────────────────────────────────

    public Message() {}

    public Message(MessageType type, String requestId) {
        this.type = type;
        this.requestId = requestId;
    }

    // ─── Static factory methods ───────────────────────────────────────────────

    /** Tạo response thành công */
    public static Message success(MessageType type, String requestId, String message, JsonElement data) {
        Message m = new Message(type, requestId);
        m.status = "SUCCESS";
        m.message = message;
        m.data = data;
        return m;
    }

    /** Tạo response lỗi */
    public static Message error(String requestId, String errorMessage) {
        Message m = new Message(MessageType.ERROR, requestId);
        m.status = "FAIL";
        m.message = errorMessage;
        m.data = null;
        return m;
    }

    /** Tạo response UNAUTHORIZED */
    public static Message unauthorized(String requestId) {
        Message m = new Message(MessageType.ERROR, requestId);
        m.status = "FAIL";
        m.message = "Unauthorized: invalid or expired token";
        m.data = null;
        return m;
    }

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public JsonElement getData() { return data; }
    public void setData(JsonElement data) { this.data = data; }

    @Override
    public String toString() {
        return "Message{type=" + type + ", requestId='" + requestId + "', status='" + status + "'}";
    }
}
