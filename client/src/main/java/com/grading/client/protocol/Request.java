package com.grading.client.protocol;

import com.google.gson.JsonObject;

/**
 * Request message gửi từ Client → Server.
 *
 * Format JSON:
 * {
 *   "type": "LOGIN",
 *   "requestId": "req-001",
 *   "token": "jwt-token-or-null",
 *   "data": { ... }
 * }
 */
public class Request {
    private String type;
    private String requestId;
    private String token;
    private JsonObject data;

    public Request() {}

    public Request(MessageType type, String requestId, String token, JsonObject data) {
        this.type = type.name();
        this.requestId = requestId;
        this.token = token;
        this.data = data;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public JsonObject getData() { return data; }
    public void setData(JsonObject data) { this.data = data; }
}
