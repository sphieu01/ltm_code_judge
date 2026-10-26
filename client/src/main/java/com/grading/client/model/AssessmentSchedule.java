package com.grading.client.model;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;

/** The shared timing rules for the student exam lobby and its start button. */
public record AssessmentSchedule(long opensAtEpochMs, long closesAtEpochMs) {

    private static final long LOBBY_LEAD_MS = 5 * 60 * 1000L;
    private static final AssessmentSchedule INVALID = new AssessmentSchedule(Long.MIN_VALUE, Long.MIN_VALUE);

    public static AssessmentSchedule from(String opensAt, String closesAt) {
        try {
            AssessmentSchedule schedule = new AssessmentSchedule(parseTimestamp(opensAt), parseTimestamp(closesAt));
            return schedule.isValid() ? schedule : INVALID;
        } catch (DateTimeException | ArithmeticException | IllegalArgumentException ex) {
            return INVALID;
        }
    }

    private static long parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing assessment time");
        }
        String normalized = value.trim().replace(' ', 'T');
        try {
            return OffsetDateTime.parse(normalized).toInstant().toEpochMilli();
        } catch (DateTimeException ex) {
            return LocalDateTime.parse(normalized).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
    }

    public boolean isValid() {
        return opensAtEpochMs != Long.MIN_VALUE && closesAtEpochMs > opensAtEpochMs;
    }

    public boolean isLobbyEligible(String status, boolean completed, long nowMs) {
        return isValid() && !completed
                && ("UPCOMING".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status))
                && nowMs >= opensAtEpochMs - LOBBY_LEAD_MS && nowMs < closesAtEpochMs;
    }

    public boolean canStart(long nowMs) {
        return isValid() && nowMs >= opensAtEpochMs && nowMs < closesAtEpochMs;
    }

    public long secondsUntilOpen(long nowMs) {
        if (!isValid() || nowMs >= opensAtEpochMs) return 0;
        long remainingMs = opensAtEpochMs - nowMs;
        return remainingMs / 1000L + (remainingMs % 1000L == 0 ? 0 : 1);
    }

    public static String formatCountdown(long seconds) {
        long remaining = Math.max(0, seconds);
        long hours = remaining / 3600;
        long minutes = (remaining % 3600) / 60;
        long secs = remaining % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.ROOT, "%02d:%02d", minutes, secs);
    }
}
