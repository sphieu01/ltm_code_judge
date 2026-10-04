package com.grading.client.protocol;

/**
 * Enum chứa tất cả message types trong protocol.
 * Client và Server phải dùng CHUNG enum này.
 */
public enum MessageType {
    // Auth
    LOGIN,
    REGISTER,
    LOGIN_RESPONSE,
    REGISTER_RESPONSE,

    // Problems
    GET_PROBLEMS,
    GET_PROBLEMS_RESPONSE,
    GET_PROBLEM_DETAIL,
    GET_PROBLEM_DETAIL_RESPONSE,

    // Submissions
    SUBMIT,
    SUBMIT_RESPONSE,
    GET_RESULT,
    GET_RESULT_RESPONSE,
    GET_HISTORY,
    GET_HISTORY_RESPONSE,

    // Notifications
    JUDGE_NOTIFICATION,

    // Connection
    DISCONNECT,

    // Error
    ERROR
}
