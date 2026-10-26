package com.grading.server.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.grading.server.protocol.Message;
import com.grading.server.protocol.MessageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StudentDataChangeNotificationTest {
    private static final int STUDENT = -91001;
    private static final int OTHER_STUDENT = -91002;
    private static final int TEACHER = -91003;
    private final ConnectionRegistry registry = ConnectionRegistry.getInstance();
    private StringWriter studentOutput;
    private StringWriter teacherOutput;

    @BeforeEach
    void registerRecipients() {
        studentOutput = new StringWriter();
        teacherOutput = new StringWriter();
        registry.register(STUDENT, new PrintWriter(studentOutput, true), null, "test", "STUDENT");
        registry.register(TEACHER, new PrintWriter(teacherOutput, true), null, "test", "TEACHER");
    }

    @AfterEach
    void unregisterRecipients() {
        registry.unregister(STUDENT);
        registry.unregister(OTHER_STUDENT);
        registry.unregister(TEACHER);
    }

    @Test
    void broadcastsOnlyInvalidationToStudentSessions() {
        StringWriter otherOutput = new StringWriter();
        registry.register(OTHER_STUDENT, new PrintWriter(otherOutput, true), null, "test", "student");

        registry.broadcastStudentDataChanged("PROBLEMS", "TOGGLE_PROBLEM_ACTIVE_RESPONSE");

        JsonObject notification = JsonParser.parseString(studentOutput.toString().trim()).getAsJsonObject();
        assertEquals("DATA_CHANGED", notification.get("type").getAsString());
        assertTrue(!notification.has("requestId") || notification.get("requestId").isJsonNull());
        assertEquals("SUCCESS", notification.get("status").getAsString());
        JsonObject data = notification.getAsJsonObject("data");
        assertEquals(2, data.size());
        assertEquals("PROBLEMS", data.get("scope").getAsString());
        assertEquals("TOGGLE_PROBLEM_ACTIVE_RESPONSE", data.get("changeType").getAsString());
        assertEquals(studentOutput.toString(), otherOutput.toString());
        assertTrue(teacherOutput.toString().isEmpty());
    }

    @Test
    void successfulTeacherMutationResponseNotifiesStudents() throws Exception {
        JsonObject privateData = new JsonObject();
        privateData.addProperty("sourceCode", "do not broadcast teacher response contents");

        sendResponse("TEACHER", Message.success(MessageType.UPDATE_PROBLEM_RESPONSE, "request-1", "OK", privateData));

        String notification = studentOutput.toString();
        assertTrue(notification.contains("DATA_CHANGED"));
        assertTrue(notification.contains("UPDATE_PROBLEM_RESPONSE"));
        assertFalse(notification.contains("sourceCode"));
        assertFalse(notification.contains("request-1"));
    }

    @Test
    void failedMutationDoesNotNotifyStudents() throws Exception {
        Message failed = new Message(MessageType.TOGGLE_PROBLEM_ACTIVE_RESPONSE, "request-2");
        failed.setStatus("FAIL");

        sendResponse("TEACHER", failed);

        assertTrue(studentOutput.toString().isEmpty());
    }

    @Test
    void readsAndStudentResponsesDoNotNotifyStudents() throws Exception {
        sendResponse("TEACHER", Message.success(MessageType.GET_PROBLEMS_RESPONSE, "read", "OK"));
        sendResponse("STUDENT", Message.success(MessageType.UPDATE_PROBLEM_RESPONSE, "student", "OK"));
        sendResponse("TEACHER", Message.success(MessageType.PROCTOR_EVENT_RESPONSE, "event", "OK"));

        assertTrue(studentOutput.toString().isEmpty());
    }

    @Test
    void coversAllTeacherMutationsThatAffectStudentScreens() {
        Map<MessageType, String> mutations = Map.ofEntries(
                Map.entry(MessageType.CREATE_PROBLEM_RESPONSE, "PROBLEMS"),
                Map.entry(MessageType.UPDATE_PROBLEM_RESPONSE, "PROBLEMS"),
                Map.entry(MessageType.DELETE_PROBLEM_RESPONSE, "PROBLEMS"),
                Map.entry(MessageType.TOGGLE_PROBLEM_ACTIVE_RESPONSE, "PROBLEMS"),
                Map.entry(MessageType.ASSIGN_CLASS_PROBLEMS_RESPONSE, "PROBLEMS"),
                Map.entry(MessageType.REMOVE_CLASS_PROBLEM_RESPONSE, "PROBLEMS"),
                Map.entry(MessageType.CREATE_COURSE_RESPONSE, "COURSES"),
                Map.entry(MessageType.UPDATE_COURSE_RESPONSE, "COURSES"),
                Map.entry(MessageType.DELETE_COURSE_RESPONSE, "COURSES"),
                Map.entry(MessageType.TOGGLE_COURSE_ACTIVE_RESPONSE, "COURSES"),
                Map.entry(MessageType.CREATE_CLASS_RESPONSE, "CLASSES"),
                Map.entry(MessageType.UPDATE_CLASS_RESPONSE, "CLASSES"),
                Map.entry(MessageType.DELETE_CLASS_RESPONSE, "CLASSES"),
                Map.entry(MessageType.IMPORT_STUDENTS_RESPONSE, "ENROLLMENTS"),
                Map.entry(MessageType.UPDATE_STUDENT_RESPONSE, "ENROLLMENTS"),
                Map.entry(MessageType.DELETE_STUDENT_RESPONSE, "ENROLLMENTS"),
                Map.entry(MessageType.CREATE_ASSESSMENT_RESPONSE, "ASSESSMENTS"));

        mutations.forEach((type, scope) -> assertEquals(scope, ClientHandler.studentDataChangeScope(type), type.name()));
        assertNull(ClientHandler.studentDataChangeScope(MessageType.GET_MY_CLASSES_RESPONSE));
        assertNull(ClientHandler.studentDataChangeScope(null));
    }

    @Test
    void disconnectedStudentsDoNotReceiveLaterChanges() {
        registry.unregister(STUDENT);

        registry.broadcastStudentDataChanged("CLASSES", "DELETE_CLASS_RESPONSE");

        assertTrue(studentOutput.toString().isEmpty());
        assertTrue(teacherOutput.toString().isEmpty());
    }

    private void sendResponse(String role, Message response) throws Exception {
        ClientHandler handler = new ClientHandler(new Socket(), null, null, null, null, null, registry);
        setField(handler, "writer", new PrintWriter(new StringWriter(), true));
        setField(handler, "authenticatedRole", role);
        Method send = ClientHandler.class.getDeclaredMethod("send", Message.class);
        send.setAccessible(true);
        send.invoke(handler, response);
    }

    private void setField(ClientHandler handler, String name, Object value) throws Exception {
        Field field = ClientHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(handler, value);
    }
}
