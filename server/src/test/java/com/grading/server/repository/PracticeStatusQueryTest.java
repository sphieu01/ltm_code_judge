package com.grading.server.repository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The dashboard reads compact badge rows, rather than downloading submission source files. */
class PracticeStatusQueryTest {
    @Test
    void readsAllBadgesInOneQueryWithoutLoadingSourceCode() throws Exception {
        Fixture fixture = new Fixture(Map.of(41, "AC", 42, "WA", 43, "PENDING"));

        Map<Integer, String> statuses = SubmissionRepository.readPracticeStatuses(fixture.connection(), 7);

        assertEquals(Map.of(41, "AC", 42, "WA", 43, "PENDING"), statuses);
        assertEquals(1, fixture.queryCount);
        assertEquals(7, fixture.userId);
        assertFalse(fixture.sql.contains("source_code"));
        assertTrue(fixture.sql.contains("assessment_id IS NULL"));
        assertTrue(fixture.statementClosed);
        assertTrue(fixture.resultClosed);
    }

    @Test
    void noPracticeSubmissionsProducesNoInventedStatuses() throws Exception {
        Fixture fixture = new Fixture(Map.of());

        assertTrue(SubmissionRepository.readPracticeStatuses(fixture.connection(), 7).isEmpty());
        assertEquals(1, fixture.queryCount);
    }

    @Test
    void databaseFailureIsReportedRatherThanReturningAnApparentlySuccessfulEmptyList() {
        Fixture fixture = new Fixture(Map.of());
        fixture.failure = new SQLException("Unavailable");

        assertThrows(SQLException.class, () -> SubmissionRepository.readPracticeStatuses(fixture.connection(), 7));
        assertTrue(fixture.statementClosed);
    }

    private static final class Fixture {
        final List<Map.Entry<Integer, String>> rows;
        String sql;
        int userId;
        int queryCount;
        boolean statementClosed;
        boolean resultClosed;
        SQLException failure;

        Fixture(Map<Integer, String> rows) {
            this.rows = new ArrayList<>(new LinkedHashMap<>(rows).entrySet());
        }

        Connection connection() {
            return proxy(Connection.class, (object, method, args) -> {
                if (method.getName().equals("prepareStatement")) {
                    sql = (String) args[0];
                    return statement();
                }
                throw new UnsupportedOperationException(method.getName());
            });
        }

        PreparedStatement statement() {
            return proxy(PreparedStatement.class, (object, method, args) -> switch (method.getName()) {
                case "setInt" -> { userId = (Integer) args[1]; yield null; }
                case "executeQuery" -> {
                    queryCount++;
                    if (failure != null) throw failure;
                    yield result();
                }
                case "close" -> { statementClosed = true; yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        ResultSet result() {
            int[] position = {-1};
            return proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
                case "next" -> ++position[0] < rows.size();
                case "getInt" -> rows.get(position[0]).getKey();
                case "getString" -> rows.get(position[0]).getValue();
                case "close" -> { resultClosed = true; yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
