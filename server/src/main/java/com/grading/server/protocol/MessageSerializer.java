package com.grading.server.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class — serialize/deserialize Message ↔ JSON string.
 *
 * Mỗi message được truyền qua socket là 1 dòng JSON không chứa ký tự '\n'
 * bên trong, và kết thúc bằng '\n' (newline delimiter cho TCP stream).
 */
public class MessageSerializer {

    private static final Logger logger = LoggerFactory.getLogger(MessageSerializer.class);

    /** Gson instance — thread-safe, dùng chung toàn app */
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .create();

    private MessageSerializer() {} // utility class — không instantiate

    /**
     * Serialize một Message thành JSON string (không có '\n' cuối).
     */
    public static String toJson(Message message) {
        return GSON.toJson(message);
    }

    /**
     * Deserialize một dòng JSON thành Message.
     *
     * @param json dòng JSON nhận được từ socket (đã strip '\n')
     * @return Message object, hoặc null nếu parse lỗi
     */
    public static Message fromJson(String json) {
        try {
            return GSON.fromJson(json, Message.class);
        } catch (JsonSyntaxException e) {
            logger.warn("Invalid JSON received: {}", json, e);
            return null;
        }
    }

    /**
     * Serialize bất kỳ object nào thành JsonElement để đặt vào Message.data.
     */
    public static <T> com.google.gson.JsonElement toJsonElement(T obj) {
        return GSON.toJsonTree(obj);
    }

    /**
     * Deserialize Message.data (JsonElement) thành object cụ thể.
     *
     * @param element  JsonElement từ Message.getData()
     * @param classOfT kiểu cần deserialize
     */
    public static <T> T fromJsonElement(com.google.gson.JsonElement element, Class<T> classOfT) {
        return GSON.fromJson(element, classOfT);
    }

    /** Lấy Gson instance nếu cần dùng trực tiếp */
    public static Gson getGson() {
        return GSON;
    }
}
