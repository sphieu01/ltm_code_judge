package com.grading.server.repository;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual repository lifecycle without a database or a running judge. */
class AssessmentLifecycleTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 9, 0);

    @Test
    void fiveMinuteLobbyDoesNotStartAnAttemptOrRestrictPractice() throws Exception {
        Fixture fixture = new Fixture(NOW.plusMinutes(5));

        JsonObject exam = fixture.summary();
        assertEquals("UPCOMING", exam.get("status").getAsString());
        assertEquals(300, exam.get("startsInSeconds").getAsLong());
        assertFalse(exam.get("started").getAsBoolean());
        assertEquals(NOW.toString(), exam.get("serverTime").getAsString());
        assertNull(fixture.repository.findActiveForStudent(7, null));
        assertTrue(fixture.repository.isProblemAllowedForStudent(7, 999));
        assertThrows(IllegalArgumentException.class, () -> fixture.repository.start(1, 7));
        assertNull(fixture.database.startedAt);
    }

    @Test
    void openingTimeEnablesEntryButOnlyExplicitStartActivatesExamContext() throws Exception {
        Fixture fixture = new Fixture(NOW.plusMinutes(5));
        fixture.clock.advanceSeconds(300);

        JsonObject exam = fixture.summary();
        assertEquals("ACTIVE", exam.get("status").getAsString());
        assertEquals(0, exam.get("startsInSeconds").getAsLong());
        assertNull(fixture.repository.findActiveForStudent(7, null));

        fixture.repository.start(1, 7);
        JsonObject active = fixture.repository.findActiveForStudent(7, null);
        assertNotNull(active);
        assertTrue(active.get("started").getAsBoolean());
        assertEquals(1, fixture.repository.findActiveAssessmentIdForProblem(7, 42));
        assertTrue(fixture.repository.isProblemAllowedForStudent(7, 42));
        assertFalse(fixture.repository.isProblemAllowedForStudent(7, 999));
        assertNull(fixture.repository.findActiveForStudent(7, 100));
    }

    @Test
    void finishingWithoutSubmissionsPersistsCompletionAndRestoresPractice() throws Exception {
        Fixture fixture = new Fixture(NOW.minusMinutes(1));
        fixture.repository.start(1, 7);
        assertEquals(0, fixture.summary().get("submittedCount").getAsInt());

        fixture.repository.complete(1, 7);

        JsonObject completed = fixture.summary();
        assertEquals("COMPLETED", completed.get("status").getAsString());
        assertTrue(completed.get("completed").getAsBoolean());
        assertNotNull(fixture.database.completedAt);
        assertNull(fixture.repository.findActiveForStudent(7, null));
        assertNull(fixture.repository.findActiveAssessmentIdForProblem(7, 42));
        assertTrue(fixture.repository.isProblemAllowedForStudent(7, 42));
        assertTrue(fixture.repository.isProblemAllowedForStudent(7, 999));
        assertThrows(IllegalArgumentException.class, () -> fixture.repository.start(1, 7));

        Timestamp firstCompletion = fixture.database.completedAt;
        fixture.clock.advanceSeconds(10);
        fixture.repository.complete(1, 7);
        assertEquals(firstCompletion, fixture.database.completedAt, "A retry must preserve the first completion");
    }

    @Test
    void expiryRemovesStartedAttemptFromActiveContextAndRejectsReentry() throws Exception {
        Fixture fixture = new Fixture(NOW);
        fixture.repository.start(1, 7);
        fixture.clock.advanceSeconds(30 * 60);

        JsonObject expired = fixture.summary();
        assertEquals("EXPIRED", expired.get("status").getAsString());
        assertEquals(0, expired.get("remainingSeconds").getAsLong());
        assertNull(fixture.repository.findActiveForStudent(7, null));
        assertThrows(IllegalArgumentException.class, () -> fixture.repository.start(1, 7));
        // Students can still explicitly finish an expired attempt and return to practice.
        fixture.repository.complete(1, 7);
        assertEquals("COMPLETED", fixture.summary().get("status").getAsString());
    }

    @Test
    void cannotCompleteAnAttemptThatWasNeverStarted() {
        Fixture fixture = new Fixture(NOW);
        assertThrows(IllegalArgumentException.class, () -> fixture.repository.complete(1, 7));
        assertNull(fixture.database.completedAt);
    }

    @Test
    void assessmentAndProblemUseTheSameCourseLanguagePolicyAsSubmissions() throws Exception {
        Fixture fixture = new Fixture(NOW);
        fixture.database.courseLanguage = "java";
        JsonObject exam = fixture.summary();
        assertEquals("java", exam.get("allowedLanguages").getAsString());
        assertEquals("java", exam.getAsJsonArray("problems").get(0).getAsJsonObject()
                .get("allowedLanguages").getAsString());

        fixture.database.courseLanguage = "cpp";
        assertEquals("cpp,c", fixture.summary().get("allowedLanguages").getAsString());
    }

    @Test
    void pollingManyAssessmentsUsesOneQueryAndOmitsQuestionsAndCode() throws Exception {
        Fixture fixture = new Fixture(NOW.plusMinutes(5));
        fixture.database.assessmentRows = 100;

        var summaries = fixture.repository.findSummariesForStudent(7);

        assertEquals(100, summaries.size());
        assertEquals(1, fixture.database.queries.size(), "Polling must not add a query per assessment or question");
        assertFalse(fixture.database.queries.get(0).contains("source_code"));
        for (var element : summaries) {
            JsonObject exam = element.getAsJsonObject();
            assertEquals(1, exam.get("problemCount").getAsInt());
            assertFalse(exam.has("problems"));
            assertFalse(exam.has("problemIds"));
            assertFalse(exam.has("description"));
        }
    }

    @Test
    void activeAttemptLoadsHeaderAndQuestionsInTwoQueriesRegardlessOfHistory() throws Exception {
        Fixture fixture = new Fixture(NOW);
        fixture.database.assessmentRows = 100;
        fixture.database.startedAt = Timestamp.valueOf(NOW);

        JsonObject active = fixture.repository.findActiveForStudent(7, null);

        assertNotNull(active);
        assertEquals(2, fixture.database.queries.size());
        assertTrue(fixture.database.queries.get(0).contains("JOIN assessment_attempts aa"));
        assertTrue(fixture.database.queries.get(0).contains("LIMIT 1"));
        assertTrue(fixture.database.queries.get(1).contains("LEFT JOIN LATERAL"));
        assertEquals(1, active.getAsJsonArray("problems").size());
    }

    @Test
    void practiceOnlyChecksForAnActiveAttemptWithoutLoadingAnyQuestionSets() throws Exception {
        Fixture fixture = new Fixture(NOW.plusMinutes(5));

        assertNull(fixture.repository.findActiveForStudent(7, null));

        assertEquals(1, fixture.database.queries.size());
    }

    @Test
    void latestQuestionVerdictIsReadInTheQuestionQuery() throws Exception {
        Fixture fixture = new Fixture(NOW);
        fixture.database.startedAt = Timestamp.valueOf(NOW);
        fixture.database.lastSubmittedAt = Timestamp.valueOf(NOW);

        JsonObject active = fixture.repository.findActiveForStudent(7, null);
        JsonObject problem = active.getAsJsonArray("problems").get(0).getAsJsonObject();

        assertTrue(problem.get("submitted").getAsBoolean());
        assertEquals("WA", problem.get("verdict").getAsString());
        assertEquals(2, fixture.database.queries.size());
    }

    private static final class Fixture {
        final MutableClock clock = new MutableClock(NOW.toInstant(ZoneOffset.UTC));
        final MemoryDatabase database;
        final AssessmentRepository repository;

        Fixture(LocalDateTime opensAt) {
            database = new MemoryDatabase(opensAt);
            repository = new AssessmentRepository(database::connection, clock);
        }

        JsonObject summary() throws Exception {
            return repository.findForStudent(7).get(0).getAsJsonObject();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }
        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

    /** A JDBC protocol stub: stores attempts and rows, leaving lifecycle decisions to the repository. */
    private static final class MemoryDatabase {
        private final LocalDateTime opensAt;
        private Timestamp startedAt;
        private Timestamp completedAt;
        private String courseLanguage = "java";
        private int assessmentRows = 1;
        private Timestamp lastSubmittedAt;
        private final List<String> queries = new ArrayList<>();

        MemoryDatabase(LocalDateTime opensAt) { this.opensAt = opensAt; }

        Connection connection() {
            return proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> statement((String) args[0]);
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        PreparedStatement statement(String sql) {
            Map<Integer, Object> parameters = new HashMap<>();
            return proxy(PreparedStatement.class, (object, method, args) -> {
                if (method.getName().startsWith("set")) {
                    parameters.put((Integer) args[0], args[1]);
                    return null;
                }
                return switch (method.getName()) {
                    case "executeQuery" -> rows(query(sql, parameters));
                    case "executeUpdate" -> update(sql, parameters);
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
        }

        List<Map<String, Object>> query(String sql, Map<Integer, Object> parameters) {
            queries.add(sql);
            if (sql.startsWith("SELECT COUNT(")) {
                return List.of(row("count", sql.contains("assessment_problems") ? 1 : 0));
            }
            if (sql.startsWith("SELECT started_at,completed_at")) {
                return startedAt == null ? List.of() : List.of(row("started_at", startedAt, "completed_at", completedAt));
            }
            if (sql.startsWith("SELECT completed_at")) {
                return startedAt == null ? List.of() : List.of(row("completed_at", completedAt));
            }
            if (sql.startsWith("SELECT verdict::text")) return List.of();
            if (sql.contains("FROM assessment_problems ap ")) {
                return List.of(row("id", 42, "title", "Sum", "description", "Compute a sum",
                        "difficulty", "EASY", "time_limit_ms", 2000, "memory_limit_kb", 262144,
                        "class_id", 5, "class_name", "Class 5", "course_name", "Programming",
                        "allowed_languages", "c,cpp,java,python", "course_lang", courseLanguage,
                        "last_submitted_at", lastSubmittedAt, "latest_verdict", "WA", "latest_score", 0,
                        "problem_type", "STANDARD", "oop_spec", null));
            }
            if (sql.startsWith("SELECT a.opens_at")) {
                if (!Integer.valueOf(1).equals(parameters.get(1)) || !Integer.valueOf(7).equals(parameters.get(2)))
                    return List.of();
                return List.of(row("opens_at", Timestamp.valueOf(opensAt),
                        "closes_at", Timestamp.valueOf(opensAt.plusHours(1)), "duration_minutes", 30));
            }
            if (sql.contains("FROM assessments a JOIN classes cl")) {
                Map<String, Object> assessment = row("id", 1, "class_id", 5, "title", "Midterm", "class_code", "CS101",
                        "class_name", "Class 5", "course_name", "Programming", "course_lang", courseLanguage,
                        "allowed_languages", "c,cpp,java,python", "opens_at", Timestamp.valueOf(opensAt),
                        "closes_at", Timestamp.valueOf(opensAt.plusHours(1)), "duration_minutes", 30,
                        "problem_count", 1, "submitted_count", 0, "started_at", startedAt, "completed_at", completedAt);
                if (sql.contains("LIMIT 1")) return List.of(assessment);
                List<Map<String, Object>> records = new ArrayList<>();
                for (int i = 0; i < assessmentRows; i++) records.add(assessment);
                return records;
            }
            throw new UnsupportedOperationException(sql);
        }

        int update(String sql, Map<Integer, Object> parameters) {
            if (sql.startsWith("INSERT INTO assessment_attempts")) {
                if (startedAt != null) return 0;
                startedAt = (Timestamp) parameters.get(3);
                return 1;
            }
            if (sql.startsWith("UPDATE assessment_attempts")) {
                if (startedAt == null || !Integer.valueOf(1).equals(parameters.get(2))
                        || !Integer.valueOf(7).equals(parameters.get(3))) return 0;
                if (completedAt == null) completedAt = (Timestamp) parameters.get(1);
                return 1;
            }
            throw new UnsupportedOperationException(sql);
        }
    }

    private static ResultSet rows(List<Map<String, Object>> values) {
        int[] position = {-1};
        Object[] lastValue = {null};
        return proxy(ResultSet.class, (object, method, args) -> {
            if (method.getName().equals("next")) return ++position[0] < values.size();
            if (method.getName().equals("close")) return null;
            if (method.getName().equals("wasNull")) return lastValue[0] == null;
            Map<String, Object> row = values.get(position[0]);
            Object key = args[0];
            Object value = key instanceof Integer index ? new ArrayList<>(row.values()).get(index - 1) : row.get(key);
            lastValue[0] = value;
            return switch (method.getName()) {
                case "getTimestamp", "getObject" -> value;
                case "getString" -> value == null ? null : value.toString();
                case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
    }

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) result.put((String) pairs[index], pairs[index + 1]);
        return result;
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
