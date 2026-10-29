package com.grading.server.repository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProblemAssignmentTest {
    @Test
    void classAssignmentAcceptsVisibleAndLegacyProblemsWithoutDuplicates() throws Exception {
        Database db = new Database();
        AcademicRepository.assignProblemsToClass(db.connection(), 10, List.of(1, 3, 1));

        assertEquals(List.of(1, 3), db.classProblems);
        assertTrue(db.committed);
        assertTrue(db.autoCommit);
    }

    @Test
    void hiddenProblemRejectsAndRollsBackTheWholeClassAssignment() {
        Database db = new Database();
        var error = assertThrows(IllegalArgumentException.class,
                () -> AcademicRepository.assignProblemsToClass(db.connection(), 10, List.of(1, 2)));

        assertTrue(error.getMessage().contains("đang bị ẩn"));
        assertTrue(db.classProblems.isEmpty());
        assertTrue(db.rolledBack);
        assertFalse(db.committed);
        assertTrue(db.autoCommit);
    }

    @Test
    void deletedProblemAlsoRollsBackEarlierAssignments() {
        Database db = new Database();
        assertThrows(IllegalArgumentException.class,
                () -> AcademicRepository.assignProblemsToClass(db.connection(), 10, List.of(1, 99)));
        assertTrue(db.classProblems.isEmpty());
        assertTrue(db.rolledBack);
    }

    @Test
    void databaseFailureCannotLeavePartOfTheAssignmentSaved() {
        Database db = new Database();
        db.failInsert = true;
        assertThrows(SQLException.class,
                () -> AcademicRepository.assignProblemsToClass(db.connection(), 10, List.of(1)));
        assertTrue(db.classProblems.isEmpty());
        assertTrue(db.rolledBack);
        assertTrue(db.autoCommit);
    }

    @Test
    void assessmentCanContainHiddenAndVisibleProblemsWithoutAssigningThemToClass() throws Exception {
        Database db = new Database();
        AssessmentRepository repository = new AssessmentRepository(db::connection, Clock.systemUTC());
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 9, 0);

        assertEquals(50, repository.create(10, "Exam", start, start.plusHours(1), 60, 7, List.of(2, 1)));
        assertEquals(List.of(2, 1), db.assessmentProblems);
        assertTrue(db.classProblems.isEmpty());
        assertTrue(db.committed);
    }

    @Test
    void editingAssessmentKeepsHiddenProblemsAndAllowsOtherBankProblems() throws Exception {
        Database db = new Database();
        db.assessmentProblems.add(3);
        AssessmentRepository repository = new AssessmentRepository(db::connection, Clock.systemUTC());
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 9, 0);

        repository.update(50, "Exam", start, start.plusHours(1), 60, List.of(2, 1));

        assertEquals(List.of(2, 1), db.assessmentProblems);
        assertTrue(db.classProblems.isEmpty());
        assertTrue(db.committed);
    }

    /** A small JDBC fixture; the repository controls validation and transaction boundaries. */
    private static final class Database {
        final Map<Integer, Boolean> visibility = new HashMap<>();
        final List<Integer> classProblems = new ArrayList<>();
        final List<Integer> assessmentProblems = new ArrayList<>();
        boolean committed;
        boolean rolledBack;
        boolean autoCommit = true;
        boolean failInsert;

        Database() {
            visibility.put(1, true);
            visibility.put(2, false);
            visibility.put(3, null);
        }

        Connection connection() {
            return proxy(Connection.class, (object, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> statement((String) args[0]);
                case "setAutoCommit" -> { autoCommit = (Boolean) args[0]; yield null; }
                case "commit" -> { committed = true; yield null; }
                case "rollback" -> {
                    rolledBack = true;
                    classProblems.clear();
                    assessmentProblems.clear();
                    yield null;
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        PreparedStatement statement(String sql) {
            Map<Integer, Object> params = new HashMap<>();
            return proxy(PreparedStatement.class, (object, method, args) -> switch (method.getName()) {
                case "setInt", "setString", "setTimestamp" -> { params.put((Integer) args[0], args[1]); yield null; }
                case "executeQuery" -> {
                    if (sql.startsWith("SELECT is_active FROM problems")) {
                        int id = (Integer) params.get(1);
                        yield result(visibility.containsKey(id), visibility.get(id));
                    }
                    if (sql.startsWith("SELECT 1 FROM assessments")) yield result(false, null);
                    if (sql.startsWith("INSERT INTO assessments")) yield result(true, 50);
                    throw new AssertionError("Unexpected query: " + sql);
                }
                case "executeUpdate" -> {
                    if (sql.startsWith("INSERT INTO class_problems")) {
                        if (failInsert) throw new SQLException("Insert failed");
                        assertEquals(10, params.get(1));
                        int id = (Integer) params.get(2);
                        if (!classProblems.contains(id)) classProblems.add(id);
                        yield 1;
                    }
                    if (sql.startsWith("INSERT INTO assessment_problems")) {
                        // These problems are deliberately absent from class_problems.
                        assertFalse(sql.contains("class_problems"));
                        assertFalse(sql.contains("is_active"));
                        int id = (Integer) params.get(3);
                        if (!visibility.containsKey(id)) yield 0;
                        assessmentProblems.add(id);
                        yield 1;
                    }
                    if (sql.startsWith("DELETE FROM assessment_problems")) { assessmentProblems.clear(); yield 1; }
                    if (sql.startsWith("UPDATE assessments")) yield 1;
                    throw new AssertionError("Unexpected update: " + sql);
                }
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        ResultSet result(boolean exists, Object value) {
            boolean[] read = {false};
            return proxy(ResultSet.class, (object, method, args) -> switch (method.getName()) {
                case "next" -> { boolean next = exists && !read[0]; read[0] = true; yield next; }
                case "getObject", "getInt" -> value;
                case "getBoolean" -> Boolean.TRUE.equals(value);
                case "close" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
