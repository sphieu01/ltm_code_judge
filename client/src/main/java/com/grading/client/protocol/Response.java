package com.grading.client.protocol;

import com.google.gson.JsonElement;

/**
 * Response message nhận từ Server → Client.
 *
 * Format JSON:
 * {
 *   "type": "LOGIN_RESPONSE",
 *   "requestId": "req-001",
 *   "status": "SUCCESS" | "FAIL",
 *   "message": "Login successful",
 *   "data": { ... }
 * }
 */
public class Response {
    private String type;
    private String requestId;
    private String status;
    private String message;
    private JsonElement data;

    public Response() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public JsonElement getData() { return data; }
    public void setData(JsonElement data) { this.data = data; }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    /**
     * Lấy MessageType enum từ type string.
     */
    public MessageType getMessageType() {
        try {
            return MessageType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
