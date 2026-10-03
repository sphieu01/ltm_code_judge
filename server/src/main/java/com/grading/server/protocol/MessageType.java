package com.grading.server.protocol;

/**
 * Enum định nghĩa tất cả các loại message trong protocol.
 * PHẢI đồng bộ với bên Client (thành viên A).
 */
public enum MessageType {
    // ── Auth ──────────────────────────────────────────────
    LOGIN,
    LOGIN_RESPONSE,
    REGISTER,
    REGISTER_RESPONSE,

    // ── Problems ──────────────────────────────────────────
    GET_PROBLEMS,
    GET_PROBLEMS_RESPONSE,
    GET_PROBLEM_DETAIL,
    GET_PROBLEM_DETAIL_RESPONSE,

    // ── Submissions ───────────────────────────────────────
    SUBMIT,
    SUBMIT_RESPONSE,
    GET_RESULT,
    GET_RESULT_RESPONSE,
    GET_HISTORY,
    GET_HISTORY_RESPONSE,

    // ── Push Notification ─────────────────────────────────
    JUDGE_NOTIFICATION,     // Server → Client: kết quả chấm xong

    // ── Control ───────────────────────────────────────────
    DISCONNECT,             // Client → Server: ngắt kết nối chủ động
    ERROR,                  // Server → Client: lỗi chung
    PING,                   // heartbeat request
    PONG                    // heartbeat response
}
