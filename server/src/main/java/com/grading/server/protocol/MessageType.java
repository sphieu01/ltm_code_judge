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
    CHANGE_PASSWORD,
    CHANGE_PASSWORD_RESPONSE,

    // ── Problems ──────────────────────────────────────────
    GET_PROBLEMS,
    GET_PROBLEMS_RESPONSE,
    GET_PROBLEM_DETAIL,
    GET_PROBLEM_DETAIL_RESPONSE,
    CREATE_PROBLEM,
    CREATE_PROBLEM_RESPONSE,
    UPDATE_PROBLEM,
    UPDATE_PROBLEM_RESPONSE,
    DELETE_PROBLEM,
    DELETE_PROBLEM_RESPONSE,
    TOGGLE_PROBLEM_ACTIVE,
    TOGGLE_PROBLEM_ACTIVE_RESPONSE,

    // ── Academic Management (Teacher / Admin) ──────────────
    GET_COURSES,
    GET_COURSES_RESPONSE,
    CREATE_COURSE,
    CREATE_COURSE_RESPONSE,
    UPDATE_COURSE,
    UPDATE_COURSE_RESPONSE,
    DELETE_COURSE,
    DELETE_COURSE_RESPONSE,
    GET_CLASSES,
    GET_CLASSES_RESPONSE,
    CREATE_CLASS,
    CREATE_CLASS_RESPONSE,
    UPDATE_CLASS,
    UPDATE_CLASS_RESPONSE,
    DELETE_CLASS,
    DELETE_CLASS_RESPONSE,
    GET_STUDENTS,
    GET_STUDENTS_RESPONSE,
    IMPORT_STUDENTS,
    IMPORT_STUDENTS_RESPONSE,
    UPDATE_STUDENT,
    UPDATE_STUDENT_RESPONSE,
    DELETE_STUDENT,
    DELETE_STUDENT_RESPONSE,
    GET_CLASS_STUDENT_STATS,
    GET_CLASS_STUDENT_STATS_RESPONSE,
    GET_MY_CLASSES,
    GET_MY_CLASSES_RESPONSE,
    TOGGLE_COURSE_ACTIVE,
    TOGGLE_COURSE_ACTIVE_RESPONSE,
    GET_CLASS_PROBLEMS,
    GET_CLASS_PROBLEMS_RESPONSE,
    ASSIGN_CLASS_PROBLEMS,
    ASSIGN_CLASS_PROBLEMS_RESPONSE,
    REMOVE_CLASS_PROBLEM,
    REMOVE_CLASS_PROBLEM_RESPONSE,

    CREATE_ASSESSMENT,
    CREATE_ASSESSMENT_RESPONSE,
    GET_ACTIVE_ASSESSMENT,
    GET_ACTIVE_ASSESSMENT_RESPONSE,
    GET_STUDENT_ASSESSMENTS,
    GET_STUDENT_ASSESSMENTS_RESPONSE,
    GET_CLASS_ASSESSMENTS,
    GET_CLASS_ASSESSMENTS_RESPONSE,
    GET_ASSESSMENT_MONITOR,
    GET_ASSESSMENT_MONITOR_RESPONSE,
    START_ASSESSMENT,
    START_ASSESSMENT_RESPONSE,
    COMPLETE_ASSESSMENT,
    COMPLETE_ASSESSMENT_RESPONSE,

    // ── Submissions ───────────────────────────────────────
    SUBMIT,
    SUBMIT_RESPONSE,
    GET_RESULT,
    GET_RESULT_RESPONSE,
    GET_HISTORY,
    GET_HISTORY_RESPONSE,

    // ── Push Notification ─────────────────────────────────
    JUDGE_NOTIFICATION,     // Server → Client: kết quả chấm xong

    // ── Anti-Cheating & Proctoring ─────────────────────────
    FORCE_LOGOUT,               // Server → Client: đá khỏi phiên do đăng nhập nơi khác
    PROCTOR_EVENT,              // Client → Server: sự kiện giám sát (mất focus cửa sổ...)
    PROCTOR_EVENT_RESPONSE,     // Server → Client: phản hồi cảnh báo giám sát
    PROCTOR_ALERT,              // Server → Teacher Client: push cảnh báo gian lận thời gian thực
    GET_PROCTOR_LOGS,           // Teacher → Server: lấy danh sách nhật ký vi phạm
    GET_PROCTOR_LOGS_RESPONSE,

    // ── Control ───────────────────────────────────────────
    DISCONNECT,             // Client → Server: ngắt kết nối chủ động
    ERROR,                  // Server → Client: lỗi chung
    PING,                   // heartbeat request
    PONG                    // heartbeat response
}
