package com.grading.server.judge.model;

public enum VerdictType {
    PENDING,
    JUDGING,
    AC,   // Accepted - Đúng hoàn toàn
    WA,   // Wrong Answer - Sai kết quả
    TLE,  // Time Limit Exceeded - Quá thời gian
    RE,   // Runtime Error - Lỗi khi thực thi
    CE,   // Compile Error - Lỗi biên dịch
    SECURITY_VIOLATION // Vi phạm quy tắc bảo mật
}
