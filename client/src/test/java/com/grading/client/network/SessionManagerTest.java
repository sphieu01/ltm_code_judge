package com.grading.client.network;

import com.grading.client.model.CreditClass;
import com.grading.client.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    private final SessionManager session = SessionManager.getInstance();

    @BeforeEach
    void clearSessionBeforeTest() {
        session.clear();
    }

    @AfterEach
    void clearSessionAfterTest() {
        session.clear();
    }

    @Test
    void completingExamRestoresPreviouslySelectedPracticeClass() {
        CreditClass practice = creditClass(10);
        session.setCurrentClass(practice);
        session.setActiveAssessment(7, false);
        session.setCurrentClass(creditClass(20));

        session.setActiveAssessment(null, true);

        assertSame(practice, session.getCurrentClass());
        assertFalse(session.isAssessmentMode());
        assertTrue(session.isAssessmentCompleted());
        assertNull(session.getActiveAssessmentId());
    }

    @Test
    void repeatedExamRefreshDoesNotReplaceSavedPracticeClass() {
        CreditClass practice = creditClass(10);
        CreditClass exam = creditClass(20);
        session.setCurrentClass(practice);
        session.setActiveAssessment(7, false);
        session.setCurrentClass(exam);
        session.setActiveAssessment(7, false);
        session.setCurrentClass(exam);
        session.setActiveAssessment(7, false);

        session.setActiveAssessment(null, true);

        assertSame(practice, session.getCurrentClass());
    }

    @Test
    void expiryRestoresPracticeClassAndClearsExamTimer() {
        CreditClass practice = creditClass(10);
        session.setCurrentClass(practice);
        session.setActiveAssessment(7, false);
        session.setCurrentClass(creditClass(20));
        session.setAssessmentClosesAt(null, 60L);
        assertTrue(session.getRemainingAssessmentSeconds() > 0);

        session.setActiveAssessment(null, false);

        assertSame(practice, session.getCurrentClass());
        assertFalse(session.isAssessmentMode());
        assertFalse(session.isAssessmentCompleted());
        assertEquals(0, session.getRemainingAssessmentSeconds());
    }

    @Test
    void completingExamWithNoPracticeClassRestoresNoSelection() {
        session.setActiveAssessment(7, false);
        session.setCurrentClass(creditClass(20));

        session.setActiveAssessment(null, true);

        assertNull(session.getCurrentClass());
    }

    @Test
    void subsequentExamCapturesNewPracticeSelection() {
        session.setCurrentClass(creditClass(10));
        session.setActiveAssessment(7, false);
        session.setCurrentClass(creditClass(20));
        session.setActiveAssessment(null, true);
        CreditClass newPractice = creditClass(30);
        session.setCurrentClass(newPractice);
        session.setActiveAssessment(8, false);
        session.setCurrentClass(creditClass(40));

        session.setActiveAssessment(null, true);

        assertSame(newPractice, session.getCurrentClass());
    }

    @Test
    void practiceRefreshAndRepeatedCompletionDoNotChangeSelection() {
        CreditClass practice = creditClass(10);
        session.setCurrentClass(practice);
        session.setActiveAssessment(null, false);
        assertSame(practice, session.getCurrentClass());

        session.setActiveAssessment(null, true);
        session.setActiveAssessment(null, true);

        assertSame(practice, session.getCurrentClass());
    }

    @Test
    void serverClockOffsetIsUsedForLobbyTime() {
        long serverEpochMs = System.currentTimeMillis() + 2 * 60 * 60 * 1000L;

        session.synchronizeAssessmentClock(Instant.ofEpochMilli(serverEpochMs).toString());

        assertTrue(Math.abs(session.getAssessmentNowEpochMs() - serverEpochMs) < 1000,
                "Lobby time must follow server time even when the local clock differs");
    }

    @Test
    void malformedClockUpdatesPreserveLastValidOffset() {
        long serverEpochMs = System.currentTimeMillis() - 2 * 60 * 60 * 1000L;
        session.synchronizeAssessmentClock(Instant.ofEpochMilli(serverEpochMs).toString());

        session.synchronizeAssessmentClock(null);
        session.synchronizeAssessmentClock("");
        session.synchronizeAssessmentClock("not-a-timestamp");

        assertTrue(Math.abs(session.getAssessmentNowEpochMs() - serverEpochMs) < 1000);
    }

    @Test
    void logoutClearsExamPracticeHistoryClockAndConnection() {
        RecordingSocketClient socket = new RecordingSocketClient();
        session.setSocketClient(socket);
        session.setCurrentUser(new User(1, "student", "Student", "STUDENT"));
        session.setCurrentClass(creditClass(10));
        session.recordSubmission(3, "ACCEPTED");
        session.setActiveAssessment(7, false);
        session.setCurrentClass(creditClass(20));
        session.setAssessmentClosesAt(null, 60L);
        session.synchronizeAssessmentClock(Instant.now().plusSeconds(7200).toString());

        session.clear();

        assertEquals(1, socket.disconnectCalls);
        assertNull(session.getSocketClient());
        assertNull(session.getCurrentUser());
        assertNull(session.getCurrentClass());
        assertNull(session.getActiveAssessmentId());
        assertFalse(session.isAssessmentMode());
        assertFalse(session.isAssessmentCompleted());
        assertEquals(0, session.getRemainingAssessmentSeconds());
        assertEquals("", session.getProblemStatus(3));
        assertTrue(Math.abs(session.getAssessmentNowEpochMs() - System.currentTimeMillis()) < 1000);
    }

    @Test
    void newLoginCannotRestorePreviousUsersPracticeClass() {
        session.setCurrentClass(creditClass(10));
        session.setActiveAssessment(7, false);
        session.setCurrentClass(creditClass(20));
        session.clear();
        session.setActiveAssessment(8, false);
        session.setCurrentClass(creditClass(30));

        session.setActiveAssessment(null, true);

        assertNull(session.getCurrentClass());
    }

    @Test
    void examCompletionClearsTimerBeforeNextExamStarts() {
        session.setActiveAssessment(7, false);
        session.setAssessmentClosesAt(null, 60L);
        session.setActiveAssessment(null, true);

        session.setActiveAssessment(8, false);

        assertEquals(0, session.getRemainingAssessmentSeconds());
        assertFalse(session.isAssessmentCompleted());
        assertTrue(session.isAssessmentMode());
    }

    @Test
    void examDeadlineUsesSynchronizedServerClockAndAuthoritativeCloseTime() {
        session.synchronizeAssessmentClock("2026-10-25T03:00:00Z");
        session.setActiveAssessment(7, false);

        session.setAssessmentClosesAt("2026-10-25T03:01:00Z", 15L);

        long remaining = session.getRemainingAssessmentSeconds();
        assertTrue(remaining >= 59 && remaining <= 60,
                "Server deadline must take priority over a stale remainingSeconds value");
    }

    @Test
    void timerRoundsUpFinalPartialSecondInsteadOfExpiringEarly() {
        session.synchronizeAssessmentClock("2026-10-25T03:00:00Z");
        session.setActiveAssessment(7, false);

        session.setAssessmentClosesAt("2026-10-25T03:00:00.900Z", null);

        assertEquals(1, session.getRemainingAssessmentSeconds());
        assertEquals("00:01", session.formatRemainingAssessmentTime());
    }

    @Test
    void elapsedDeadlineIsZeroAndCannotBecomeNegative() {
        session.synchronizeAssessmentClock("2026-10-25T03:00:00Z");
        session.setActiveAssessment(7, false);

        session.setAssessmentClosesAt("2026-10-25T03:00:00Z", null);

        assertEquals(0, session.getRemainingAssessmentSeconds());
        assertEquals("00:00", session.formatRemainingAssessmentTime());
    }

    @Test
    void remainingSecondsCanBeUsedWhenCloseTimestampIsMissing() {
        session.setActiveAssessment(7, false);

        session.setAssessmentClosesAt(null, 5L);

        long remaining = session.getRemainingAssessmentSeconds();
        assertTrue(remaining >= 4 && remaining <= 5);
    }

    private static CreditClass creditClass(int id) {
        CreditClass creditClass = new CreditClass();
        creditClass.setId(id);
        return creditClass;
    }

    private static class RecordingSocketClient extends SocketClient {
        private int disconnectCalls;

        private RecordingSocketClient() {
            super("localhost", 0);
        }

        @Override
        public void disconnect() {
            disconnectCalls++;
        }
    }
}
