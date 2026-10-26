package com.grading.client.network;

import com.google.gson.JsonObject;
import com.grading.client.protocol.MessageType;
import com.grading.client.protocol.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class SocketClientDataChangeTest {
    @Test
    void dataChangedPushDoesNotConsumePendingRequestCallback() throws Exception {
        SocketClient client = new SocketClient("unused", 1);
        AtomicInteger changes = new AtomicInteger();
        AtomicInteger replies = new AtomicInteger();
        client.addDataChangedListener(response -> changes.incrementAndGet());
        pendingRequests(client).put("shared-id", response -> replies.incrementAndGet());
        Response push = response("DATA_CHANGED", "shared-id");

        dispatch(client, push);

        assertEquals(1, changes.get());
        assertEquals(0, replies.get());
        assertTrue(pendingRequests(client).containsKey("shared-id"));

        dispatch(client, response("GET_PROBLEMS_RESPONSE", "shared-id"));
        assertEquals(1, replies.get());
        assertFalse(pendingRequests(client).containsKey("shared-id"));
    }

    @Test
    void addRemoveAndDuplicateSubscriptionsAreSafe() throws Exception {
        SocketClient client = new SocketClient("unused", 1);
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        Consumer<Response> first = response -> firstCalls.incrementAndGet();
        Consumer<Response> second = response -> secondCalls.incrementAndGet();
        client.addDataChangedListener(first);
        client.addDataChangedListener(first);
        client.addDataChangedListener(second);
        dispatch(client, response("DATA_CHANGED", null));
        assertEquals(1, firstCalls.get());
        assertEquals(1, secondCalls.get());

        client.removeDataChangedListener(first);
        dispatch(client, response("DATA_CHANGED", null));
        assertEquals(1, firstCalls.get());
        assertEquals(2, secondCalls.get());
    }

    @Test
    void aFailingListenerDoesNotBlockOtherScreens() throws Exception {
        SocketClient client = new SocketClient("unused", 1);
        AtomicInteger calls = new AtomicInteger();
        client.addDataChangedListener(response -> { throw new IllegalStateException("screen closed"); });
        client.addDataChangedListener(response -> calls.incrementAndGet());

        dispatch(client, response("DATA_CHANGED", null));

        assertEquals(1, calls.get());
    }

    @Test
    void disconnectClearsScreenSubscriptionsEvenBeforeConnecting() throws Exception {
        SocketClient client = new SocketClient("unused", 1);
        AtomicInteger calls = new AtomicInteger();
        client.addDataChangedListener(response -> calls.incrementAndGet());

        client.disconnect();
        dispatch(client, response("DATA_CHANGED", null));

        assertEquals(0, calls.get());
    }

    @Test
    void studentAssessmentPollingRequestsSummariesWithoutQuestionPayloads() {
        CapturingSocketClient client = new CapturingSocketClient();

        client.getStudentAssessments(response -> {});

        assertEquals(MessageType.GET_STUDENT_ASSESSMENTS, client.lastType);
        assertTrue(client.lastData.get("summariesOnly").getAsBoolean());
    }

    @Test
    void ordinaryResponsesDoNotTriggerDataChangedListeners() throws Exception {
        SocketClient client = new SocketClient("unused", 1);
        AtomicInteger changes = new AtomicInteger();
        client.addDataChangedListener(response -> changes.incrementAndGet());

        dispatch(client, response("GET_STUDENT_ASSESSMENTS_RESPONSE", null));

        assertEquals(0, changes.get());
    }

    private Response response(String type, String requestId) {
        Response response = new Response();
        response.setType(type);
        response.setRequestId(requestId);
        response.setStatus("SUCCESS");
        return response;
    }

    private void dispatch(SocketClient client, Response response) throws Exception {
        Method dispatch = SocketClient.class.getDeclaredMethod("dispatchResponse", Response.class);
        dispatch.setAccessible(true);
        dispatch.invoke(client, response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Consumer<Response>> pendingRequests(SocketClient client) throws Exception {
        Field field = SocketClient.class.getDeclaredField("pendingRequests");
        field.setAccessible(true);
        return (Map<String, Consumer<Response>>) field.get(client);
    }

    private static class CapturingSocketClient extends SocketClient {
        private MessageType lastType;
        private JsonObject lastData;

        CapturingSocketClient() { super("unused", 1); }

        @Override
        public String sendRequest(MessageType type, JsonObject data, Consumer<Response> callback) {
            lastType = type;
            lastData = data;
            return "captured";
        }
    }
}
