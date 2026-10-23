package com.grading.server.service;

import com.grading.server.model.ProctorLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ProctorService — Quản lý và xử lý dữ liệu giám sát phòng thi chống gian lận.
 *
 * Chức năng:
 * - Tiếp nhận các sự kiện vi phạm từ Client (mất focus Alt+Tab, paste mã
 * nguồn...)
 * - Đếm số lần vi phạm trên từng sinh viên và từng bài thi
 * - Phân loại mức độ vi phạm (INFO, WARNING, CRITICAL)
 * - Lưu trữ lịch sử nhật ký (In-Memory Thread-Safe) để cung cấp cho Giám thị /
 * Giáo viên
 */
public class ProctorService {

    private static final Logger logger = LoggerFactory.getLogger(ProctorService.class);

    private final AtomicInteger idGenerator = new AtomicInteger(1);

    // Map: "userId:problemId" -> số lần vi phạm
    private final Map<String, AtomicInteger> violationCounters = new ConcurrentHashMap<>();

    // Danh sách nhật ký vi phạm (thread-safe)
    private final List<ProctorLog> logList = new CopyOnWriteArrayList<>();

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
        String key = userId + ":" + problemId;
        int count = violationCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        String severity;
        String warningMessage;

        if ("AI_TOOL_DETECTED".equalsIgnoreCase(eventType)) {
            severity = "CRITICAL";
            String toolInfo = (detail != null && !detail.isBlank()) ? detail : "ChatGPT / Gemini";
            warningMessage = "CẢNH BÁO GIAN LẬN: Phát hiện mở công cụ AI! Hành vi đã được gửi cho Giám thị.";
        } else if ("AI_CODE_PASTED".equalsIgnoreCase(eventType)) {
            severity = "CRITICAL";
            String detailInfo = (detail != null && !detail.isBlank()) ? detail : "Cú pháp do AI sinh ra";
            warningMessage = "CẢNH BÁO VI PHẠM: Phát hiện dán mã nguồn có dấu hiệu AI (" + detailInfo + ")!";
        } else {
            if (count == 1) {
                severity = "WARNING";
                warningMessage = "Lưu ý: Hệ thống phát hiện sự kiện bất thường (Lần 1). Hãy tập trung làm bài!";
            } else if (count == 2) {
                severity = "WARNING";
                warningMessage = "Cảnh báo: Bạn đã vi phạm lần thứ 2! Nghiêm cấm mọi hành vi gian lận.";
            } else {
                severity = "CRITICAL";
                warningMessage = "CẢNH BÁO VI PHẠM: Bạn đã vi phạm lần thứ " + count
                        + "! Dữ liệu đã gửi trực tiếp cho Giám thị.";
            }
        }

        ProctorLog log = new ProctorLog(
                idGenerator.getAndIncrement(),
                userId,
                username,
                fullName,
                problemId,
                problemTitle != null ? problemTitle : "Bài tập #" + problemId,
                eventType,
                detail,
                count,
                severity,
                clientIp,
                System.currentTimeMillis());

        // Lưu log (giới hạn tối đa 1000 logs gần nhất)
        if (logList.size() >= 1000) {
            logList.remove(0);
        }
        logList.add(log);

        logger.warn("[PROCTORING] User '{}' (id={}) committed {} [detail={}] on problem #{}: count={}, severity={}",
                username, userId, eventType, detail, problemId, count, severity);

        return new ProctorResult(count, severity, warningMessage, log);
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
        // Trả về bản sao đảo ngược (mới nhất lên đầu)
        List<ProctorLog> reversed = new ArrayList<>(logList);
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    /**
     * Lấy số lần vi phạm hiện tại của sinh viên ở bài tập này.
     */
    public int getViolationCount(int userId, int problemId) {
        AtomicInteger counter = violationCounters.get(userId + ":" + problemId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Reset vi phạm (khi cần cấp quyền làm lại).
     */
    public void resetViolations(int userId, int problemId) {
        violationCounters.remove(userId + ":" + problemId);
    }
}
