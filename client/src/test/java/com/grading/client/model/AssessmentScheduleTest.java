package com.grading.client.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentScheduleTest {

    private static final long OPENS = Instant.parse("2026-10-25T03:00:00Z").toEpochMilli();
    private static final long CLOSES = OPENS + 30 * 60 * 1000L;
    private final AssessmentSchedule schedule = new AssessmentSchedule(OPENS, CLOSES);

    @Test
    void practiceRemainsVisibleBeforeFiveMinuteWindow() {
        assertFalse(schedule.isLobbyEligible("UPCOMING", false, OPENS - 300_001));
    }

    @Test
    void lobbyOpensAtExactlyFiveMinutesBeforeExam() {
        assertTrue(schedule.isLobbyEligible("UPCOMING", false, OPENS - 300_000));
        assertEquals(300, schedule.secondsUntilOpen(OPENS - 300_000));
        assertFalse(schedule.canStart(OPENS - 300_000));
    }

    @Test
    void finalFractionOfSecondCannotStartExamEarly() {
        assertTrue(schedule.isLobbyEligible("UPCOMING", false, OPENS - 1));
        assertEquals(1, schedule.secondsUntilOpen(OPENS - 1));
        assertFalse(schedule.canStart(OPENS - 1));
    }

    @Test
    void startButtonBecomesAvailableAtOpeningTime() {
        assertTrue(schedule.isLobbyEligible("ACTIVE", false, OPENS));
        assertTrue(schedule.canStart(OPENS));
        assertEquals(0, schedule.secondsUntilOpen(OPENS));
    }

    @Test
    void staleUpcomingStatusStillAllowsOnTimeCountdownTransition() {
        assertTrue(schedule.isLobbyEligible("UPCOMING", false, OPENS + 1));
        assertTrue(schedule.canStart(OPENS + 1));
    }

    @Test
    void openUnfinishedExamStaysInLobbyUntilClosingTime() {
        assertTrue(schedule.isLobbyEligible("ACTIVE", false, CLOSES - 1));
        assertTrue(schedule.canStart(CLOSES - 1));
    }

    @Test
    void examIsExcludedAtAndAfterClosingTime() {
        assertFalse(schedule.isLobbyEligible("ACTIVE", false, CLOSES));
        assertFalse(schedule.canStart(CLOSES));
        assertFalse(schedule.isLobbyEligible("ACTIVE", false, CLOSES + 1));
        assertFalse(schedule.canStart(CLOSES + 1));
    }

    @Test
    void completedExamNeverReturnsToLobby() {
        assertFalse(schedule.isLobbyEligible("ACTIVE", true, OPENS));
        assertFalse(schedule.isLobbyEligible("COMPLETED", false, OPENS));
    }

    @Test
    void expiredAndUnknownStatusesAreExcludedEvenWithFutureDeadline() {
        assertFalse(schedule.isLobbyEligible("EXPIRED", false, OPENS));
        assertFalse(schedule.isLobbyEligible("DRAFT", false, OPENS));
        assertFalse(schedule.isLobbyEligible(null, false, OPENS));
    }

    @Test
    void timestampsWithOffsetsAndUtcUseTheirSpecifiedZone() {
        AssessmentSchedule utc = AssessmentSchedule.from("2026-10-25T03:00:00Z", "2026-10-25T03:30:00Z");
        AssessmentSchedule vietnam = AssessmentSchedule.from("2026-10-25T10:00:00+07:00", "2026-10-25T10:30:00+07:00");
        assertEquals(schedule, utc);
        assertEquals(utc, vietnam);
    }

    @Test
    void localAndSqlStyleTimestampsUseLocalZone() {
        AssessmentSchedule local = AssessmentSchedule.from("2026-10-25T10:00:00", "2026-10-25 10:30:00");
        assertEquals(LocalDateTime.of(2026, 10, 25, 10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                local.opensAtEpochMs());
        assertEquals(30 * 60 * 1000L, local.closesAtEpochMs() - local.opensAtEpochMs());
    }

    @Test
    void malformedAndMissingTimesCannotEnterOrStartExam() {
        for (String value : new String[]{null, "", " ", "not-a-time"}) {
            AssessmentSchedule invalid = AssessmentSchedule.from(value, "2026-10-25T03:30:00Z");
            assertFalse(invalid.isValid());
            assertFalse(invalid.isLobbyEligible("ACTIVE", false, OPENS));
            assertFalse(invalid.canStart(OPENS));
        }
        assertFalse(AssessmentSchedule.from("2026-10-25T03:00:00Z", null).isValid());
    }

    @Test
    void emptyOrReversedWindowCannotStartExam() {
        assertFalse(AssessmentSchedule.from("2026-10-25T03:00:00Z", "2026-10-25T03:00:00Z").isValid());
        assertFalse(AssessmentSchedule.from("2026-10-25T03:30:00Z", "2026-10-25T03:00:00Z").isValid());
    }

    @Test
    void countdownRoundsUpPartialSecondsAndStopsAtZero() {
        assertEquals(2, schedule.secondsUntilOpen(OPENS - 1001));
        assertEquals(1, schedule.secondsUntilOpen(OPENS - 1000));
        assertEquals(0, schedule.secondsUntilOpen(OPENS + 1000));
    }

    @Test
    void countdownFormattingSupportsMinutesHoursAndZero() {
        assertEquals("05:00", AssessmentSchedule.formatCountdown(300));
        assertEquals("00:01", AssessmentSchedule.formatCountdown(1));
        assertEquals("01:01:01", AssessmentSchedule.formatCountdown(3661));
        assertEquals("00:00", AssessmentSchedule.formatCountdown(-1));
    }
}
