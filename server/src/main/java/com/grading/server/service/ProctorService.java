package com.grading.server.service;

import com.grading.server.model.ProctorLog;
import com.grading.server.repository.ProctorLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Clock;
import java.util.List;

/**
 * ProctorService — Quản lý và xử lý dữ liệu giám sát phòng thi chống gian lận.
 *
 * Chức năng:
 * - Tiếp nhận các sự kiện vi phạm từ Client (mất focus Alt+Tab, paste mã
 * nguồn...)
 * - Đếm số lần vi phạm trên từng sinh viên và từng bài thi
 * - Phân loại mức độ vi phạm (INFO, WARNING, CRITICAL)
 * - Lưu lịch sử nhật ký vào PostgreSQL để cung cấp cho Giám thị /
 * Giáo viên
 */
public class ProctorService {

    private static final Logger logger = LoggerFactory.getLogger(ProctorService.class);

    private final ProctorLogRepository repository;
    private final Clock clock;

    public ProctorService() {
        this(new ProctorLogRepository(), Clock.systemDefaultZone());
    }

    public ProctorService(ProctorLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public record ProctorResult(
            int violationCount,
            String severity,
            String warningMessage,
            ProctorLog log) {
    }

    /**
     * Ghi nhận một sự kiện giám sát từ client (kèm thông tin chi tiết vi phạm).
     */
    public ProctorResult recordEvent(int userId, String username, String fullName,
            int problemId, String problemTitle,
            String eventType, String detail, String clientIp) {
        return recordEvent(userId, username, fullName, problemId, null, problemTitle,
                eventType, detail, clientIp);
    }

    public ProctorResult recordEvent(int userId, String username, String fullName,
            int problemId, Integer assessmentId, String problemTitle,
            String eventType, String detail, String clientIp) {
        if ("FOCUS_LOSS".equalsIgnoreCase(eventType)) {
            return new ProctorResult(0, "INFO", "OK", null);
        }

        ProctorLog log;
        try {
            log = repository.append(userId, problemId, assessmentId, count -> new ProctorLog(
                    0, userId, username, fullName, problemId, assessmentId,
                    problemTitle != null ? problemTitle : "Bài tập #" + problemId,
                    eventType, detail, count, severity(eventType, count), clientIp, clock.millis()));
        } catch (SQLException e) {
            logger.error("Could not persist proctor event for user {} and problem {}", userId, problemId, e);
            throw new IllegalStateException("Không thể lưu nhật ký giám sát vào database. Hãy thử lại.", e);
        }

        logger.warn("[PROCTORING] User '{}' (id={}) committed {} [detail={}] on problem #{}: count={}, severity={}",
                username, userId, eventType, detail, problemId, log.getViolationCount(), log.getSeverity());

        return new ProctorResult(log.getViolationCount(), log.getSeverity(),
                warningMessage(eventType, detail, log.getViolationCount()), log);
    }

    public ProctorResult recordEvent(int userId, String username, String fullName,
            int problemId, String problemTitle,
            String eventType, String clientIp) {
        return recordEvent(userId, username, fullName, problemId, problemTitle, eventType, null, clientIp);
    }

    /**
     * Lấy toàn bộ nhật ký vi phạm (dành cho Giáo viên).
     */
    public List<ProctorLog> getAllLogs() {
        try {
            return repository.findAll();
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tải nhật ký giám sát từ database.", e);
        }
    }

    public List<ProctorLog> getLogsForAssessment(int assessmentId) {
        try {
            return repository.findByAssessment(assessmentId);
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tải nhật ký bài kiểm tra từ database.", e);
        }
    }

    /**
     * Lấy số lần vi phạm hiện tại của sinh viên ở bài tập này.
     */
    public int getViolationCount(int userId, int problemId) {
        return getViolationCount(userId, problemId, null);
    }

    public int getViolationCount(int userId, int problemId, Integer assessmentId) {
        try {
            return repository.getViolationCount(userId, problemId, assessmentId);
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tải số lần vi phạm từ database.", e);
        }
    }

    /**
     * Reset vi phạm (khi cần cấp quyền làm lại).
     */
    public void resetViolations(int userId, int problemId) {
        try {
            repository.resetViolations(userId, problemId);
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể đặt lại số lần vi phạm trong database.", e);
        }
    }

    private static String severity(String eventType, int count) {
        return "AI_TOOL_DETECTED".equalsIgnoreCase(eventType) || "AI_CODE_PASTED".equalsIgnoreCase(eventType)
                || count >= 3 ? "CRITICAL" : "WARNING";
    }

    private static String warningMessage(String eventType, String detail, int count) {
        if ("AI_TOOL_DETECTED".equalsIgnoreCase(eventType))
            return "CẢNH BÁO GIAN LẬN: Phát hiện mở công cụ AI! Hành vi đã được gửi cho Giám thị.";
        if ("AI_CODE_PASTED".equalsIgnoreCase(eventType)) {
            String detailInfo = detail != null && !detail.isBlank() ? detail : "Cú pháp do AI sinh ra";
            return "CẢNH BÁO VI PHẠM: Phát hiện dán mã nguồn có dấu hiệu AI (" + detailInfo + ")!";
        }
        if (count == 1)
            return "Lưu ý: Hệ thống phát hiện sự kiện bất thường (Lần 1). Hãy tập trung làm bài!";
        if (count == 2)
            return "Cảnh báo: Bạn đã vi phạm lần thứ 2! Nghiêm cấm mọi hành vi gian lận.";
        return "CẢNH BÁO VI PHẠM: Bạn đã vi phạm lần thứ " + count + "! Dữ liệu đã gửi trực tiếp cho Giám thị.";
    }
}
