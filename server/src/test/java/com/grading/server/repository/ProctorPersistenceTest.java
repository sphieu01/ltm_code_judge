package com.grading.server.repository;

import com.grading.server.model.ProctorLog;
import com.grading.server.service.ProctorService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/** The JDBC fixture persists state independently of repository/service lifetimes. */
class ProctorPersistenceTest {
    private static final Instant EVENT_TIME = Instant.parse("2026-10-25T03:00:00Z");

    @Test
    void newServerServiceReadsPreviousEventsAndContinuesCounts() {
        MemoryDatabase database = new MemoryDatabase();
        ProctorService firstServer = service(database, EVENT_TIME);
        ProctorLog first = event(firstServer, 7, 42, 11).log();

        ProctorService restartedServer = service(database, EVENT_TIME.plusSeconds(60));
        ProctorLog restored = restartedServer.getAllLogs().get(0);
        assertEquals(first.getId(), restored.getId());
        assertEquals("student7", restored.getUsername());
        assertEquals("Student 7", restored.getFullName());
        assertEquals("Question 42", restored.getProblemTitle());
        assertEquals("ChatGPT", restored.getDetail());
        assertEquals("127.0.0.1", restored.getClientIp());
        assertEquals(11, restored.getAssessmentId());
        assertEquals(EVENT_TIME.toEpochMilli(), restored.getTimestamp());
        assertEquals(EVENT_TIME.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy")), restored.getFormattedTime());

        ProctorLog second = event(restartedServer, 7, 42, 11).log();
        assertEquals(2, second.getViolationCount());
        assertTrue(second.getId() > first.getId());
        assertEquals(List.of(second.getId(), first.getId()),
                restartedServer.getAllLogs().stream().map(ProctorLog::getId).toList());
        assertEquals(2, firstServer.getViolationCount(7, 42, 11));
    }

    @Test
    void attemptsStudentsAndProblemsHaveIndependentPersistentCounters() {
        MemoryDatabase database = new MemoryDatabase();
        ProctorService server = service(database, EVENT_TIME);
        assertEquals(1, event(server, 7, 42, 11).violationCount());
        assertEquals(2, event(server, 7, 42, 11).violationCount());
        assertEquals(1, event(server, 7, 42, 12).violationCount());
        assertEquals(1, event(server, 8, 42, 11).violationCount());
        assertEquals(1, event(server, 7, 43, 11).violationCount());
        assertEquals(1, event(server, 7, 42, null).violationCount());

        ProctorService restarted = service(database, EVENT_TIME.plusSeconds(10));
        assertEquals(2, restarted.getViolationCount(7, 42, 11));
        assertEquals(1, restarted.getViolationCount(7, 42, 12));
        assertEquals(1, restarted.getViolationCount(7, 42));
        assertEquals(4, restarted.getLogsForAssessment(11).size());
        assertEquals(1, restarted.getLogsForAssessment(12).size());
        assertTrue(restarted.getLogsForAssessment(999).isEmpty());
    }

    @Test
    void failedInsertIsNotAcknowledgedAndRollsBackTheCounter() {
        MemoryDatabase database = new MemoryDatabase();
        database.failNextInsert = true;
        ProctorService server = service(database, EVENT_TIME);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> event(server, 7, 42, 11));
        assertInstanceOf(SQLException.class, error.getCause());
        assertTrue(server.getAllLogs().isEmpty());
        assertEquals(0, server.getViolationCount(7, 42, 11));
        assertEquals(1, event(service(database, EVENT_TIME), 7, 42, 11).violationCount());
    }

    @Test
    void failedCommitCannotReturnASuccessfulAuditEvent() {
        MemoryDatabase database = new MemoryDatabase();
        database.failNextCommit = true;
        ProctorService server = service(database, EVENT_TIME);

        assertThrows(IllegalStateException.class, () -> event(server, 7, 42, 11));
        assertTrue(server.getAllLogs().isEmpty());
        assertEquals(0, server.getViolationCount(7, 42, 11));
        assertEquals(1, event(server, 7, 42, 11).violationCount());
    }

    @Test
    void databaseReadFailureIsReportedRatherThanShownAsAnEmptyHistory() {
        ProctorService server = new ProctorService(new ProctorLogRepository(() -> {
            throw new SQLException("Database is offline");
        }), Clock.fixed(EVENT_TIME, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, server::getAllLogs);
        assertThrows(IllegalStateException.class, () -> server.getLogsForAssessment(11));
        assertThrows(IllegalStateException.class, () -> server.getViolationCount(7, 42, 11));
        assertThrows(IllegalStateException.class, () -> server.resetViolations(7, 42));
    }

    @Test
    void resetPersistsWithoutDeletingHistoricalEvidence() {
        MemoryDatabase database = new MemoryDatabase();
        ProctorService server = service(database, EVENT_TIME);
        event(server, 7, 42, 11);
        event(server, 7, 42, 11);

        server.resetViolations(7, 42);

        ProctorService restarted = service(database, EVENT_TIME.plusSeconds(1));
        assertEquals(0, restarted.getViolationCount(7, 42, 11));
        assertEquals(2, restarted.getAllLogs().size());
        assertEquals(1, event(restarted, 7, 42, 11).violationCount());
        assertEquals(3, restarted.getAllLogs().size());
    }

    @Test
    void normalFocusLossDoesNotWriteAViolation() {
        ProctorService server = new ProctorService(new ProctorLogRepository(() -> {
            fail("Normal focus loss must not access the audit database");
            throw new SQLException();
        }), Clock.fixed(EVENT_TIME, ZoneOffset.UTC));

        ProctorService.ProctorResult result = server.recordEvent(7, "student7", "Student 7", 42, 11,
                "Question 42", "FOCUS_LOSS", null, "127.0.0.1");
        assertNull(result.log());
        assertEquals(0, result.violationCount());
    }

    @Test
    void concurrentServicesUseUniqueCommittedCounterValues() throws Exception {
        MemoryDatabase database = new MemoryDatabase();
        ProctorService first = service(database, EVENT_TIME);
        ProctorService second = service(database, EVENT_TIME);
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> events = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                ProctorService service = i % 2 == 0 ? first : second;
                events.add(() -> event(service, 7, 42, 11).violationCount());
            }
            List<Integer> counts = new ArrayList<>();
            for (var result : executor.invokeAll(events)) counts.add(result.get());
            assertEquals(30, counts.stream().distinct().count());
            assertEquals(1, counts.stream().mapToInt(Integer::intValue).min().orElseThrow());
            assertEquals(30, counts.stream().mapToInt(Integer::intValue).max().orElseThrow());
            assertEquals(30, service(database, EVENT_TIME).getViolationCount(7, 42, 11));
            assertEquals(30, first.getAllLogs().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void warningEscalationSurvivesServiceRestart() {
        MemoryDatabase database = new MemoryDatabase();
        ProctorService first = service(database, EVENT_TIME);
        assertEquals("WARNING", first.recordEvent(7, "student7", "Student 7", 42, 11,
                "Question 42", "OTHER_EVENT", null, "127.0.0.1").severity());
        ProctorService second = service(database, EVENT_TIME);
        assertEquals("WARNING", second.recordEvent(7, "student7", "Student 7", 42, 11,
                "Question 42", "OTHER_EVENT", null, "127.0.0.1").severity());
        assertEquals("CRITICAL", second.recordEvent(7, "student7", "Student 7", 42, 11,
                "Question 42", "OTHER_EVENT", null, "127.0.0.1").severity());
        assertEquals("CRITICAL", event(second, 7, 42, 12).severity());
    }

    private static ProctorService service(MemoryDatabase database, Instant now) {
        return new ProctorService(new ProctorLogRepository(database::connection), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static ProctorService.ProctorResult event(ProctorService server, int userId, int problemId, Integer assessmentId) {
        return server.recordEvent(userId, "student" + userId, "Student " + userId, problemId, assessmentId,
                "Question " + problemId, "AI_TOOL_DETECTED", "ChatGPT", "127.0.0.1");
    }

    /** Stores committed rows across fresh service/repository objects; serializes DB transactions. */
    private static final class MemoryDatabase {
        final ReentrantLock transactionLock = new ReentrantLock();
        Map<String, Integer> counters = new HashMap<>();
        List<Map<String, Object>> logs = new ArrayList<>();
        int nextId = 1;
        boolean failNextInsert;
        boolean failNextCommit;

        Connection connection() {
            Transaction transaction = new Transaction();
            return proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
                case "getAutoCommit" -> !transaction.locked;
                case "setAutoCommit" -> {
                    if (!((Boolean) args[0])) transaction.begin();
                    else transaction.close();
                    yield null;
                }
                case "prepareStatement" -> statement((String) args[0], transaction);
                case "commit" -> { transaction.commit(); yield null; }
                case "rollback" -> null;
                case "close" -> { transaction.close(); yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        PreparedStatement statement(String sql, Transaction transaction) {
            Map<Integer, Object> parameters = new HashMap<>();
            return proxy(PreparedStatement.class, (object, method, args) -> {
                if (method.getName().startsWith("set")) {
                    parameters.put((Integer) args[0], method.getName().equals("setNull") ? null : args[1]);
                    return null;
                }
                return switch (method.getName()) {
                    case "executeQuery" -> rows(query(sql, parameters, transaction));
                    case "executeUpdate" -> update(sql, parameters);
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            });
        }

        List<Map<String, Object>> query(String sql, Map<Integer, Object> p, Transaction tx) throws SQLException {
            if (sql.startsWith("INSERT INTO proctor_violation_counters")) {
                assertTrue(sql.contains("ON CONFLICT"), "Counting must be atomic in PostgreSQL");
                String scope = p.get(1) + ":" + p.get(2) + ":" + p.get(3);
                int count = tx.counters.merge(scope, 1, Integer::sum);
                return List.of(row("violation_count", count));
            }
            if (sql.startsWith("INSERT INTO proctor_logs")) {
                if (failNextInsert) { failNextInsert = false; throw new SQLException("Insert failed"); }
                int id = nextId++;
                tx.logs.add(row("id", id, "user_id", p.get(1), "username", p.get(2), "full_name", p.get(3),
                        "problem_id", p.get(4), "assessment_id", p.get(5), "problem_title", p.get(6),
                        "event_type", p.get(7), "detail", p.get(8), "violation_count", p.get(9),
                        "severity", p.get(10), "client_ip", p.get(11), "occurred_at_ms", p.get(12)));
                return List.of(row("id", id));
            }
            transactionLock.lock();
            try {
                if (sql.startsWith("SELECT violation_count")) {
                    Integer value = counters.get(p.get(1) + ":" + p.get(2) + ":" + p.get(3));
                    return value == null ? List.of() : List.of(row("violation_count", value));
                }
                if (sql.contains("FROM proctor_logs")) {
                    return logs.stream().filter(log -> !sql.contains("WHERE assessment_id")
                                    || p.get(1).equals(log.get("assessment_id")))
                            .sorted(Comparator.<Map<String, Object>>comparingLong(log -> (Long) log.get("occurred_at_ms"))
                                    .thenComparingInt(log -> (Integer) log.get("id")).reversed()).toList();
                }
            } finally { transactionLock.unlock(); }
            throw new UnsupportedOperationException(sql);
        }

        int update(String sql, Map<Integer, Object> parameters) {
            assertTrue(sql.startsWith("DELETE FROM proctor_violation_counters"));
            transactionLock.lock();
            try {
                String prefix = parameters.get(1) + ":" + parameters.get(2) + ":";
                int before = counters.size();
                counters.keySet().removeIf(key -> key.startsWith(prefix));
                return before - counters.size();
            } finally { transactionLock.unlock(); }
        }

        final class Transaction {
            Map<String, Integer> counters;
            List<Map<String, Object>> logs;
            boolean locked;
            void begin() {
                transactionLock.lock();
                locked = true;
                counters = new HashMap<>(MemoryDatabase.this.counters);
                logs = new ArrayList<>(MemoryDatabase.this.logs);
            }
            void commit() throws SQLException {
                if (failNextCommit) { failNextCommit = false; throw new SQLException("Commit failed"); }
                MemoryDatabase.this.counters = new HashMap<>(counters);
                MemoryDatabase.this.logs = new ArrayList<>(logs);
            }
            void close() { if (locked) { locked = false; transactionLock.unlock(); } }
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
                case "getString" -> value == null ? null : value.toString();
                case "getInt" -> value == null ? 0 : ((Number) value).intValue();
                case "getLong" -> value == null ? 0L : ((Number) value).longValue();
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) row.put((String) values[i], values[i + 1]);
        return row;
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
