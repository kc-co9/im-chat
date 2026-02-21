package com.co.kc.imchat.support.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.co.kc.imchat.support.exception.SerializationException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * JSON序列化和反序列化工具类
 *
 * @author kc
 */
public class JsonUtils {
    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        OBJECT_MAPPER.registerModule(getJavaTimeModule());
        // 忽略未知字段，避免反序列化失败
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        OBJECT_MAPPER.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    }

    private JsonUtils() {
    }

    /**
     * 将对象序列化为 JSON 字符串
     */
    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new SerializationException("JSON 序列化失败", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化为指定类型
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            throw new SerializationException("JSON 反序列化失败: " + json, e);
        }
    }

    /**
     * 从 JSON 字符串反序列化为泛型类型
     */
    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        try {
            return OBJECT_MAPPER.readValue(json, typeRef);
        } catch (IOException e) {
            throw new SerializationException("JSON 泛型反序列化失败: " + json, e);
        }
    }

    /**
     * 获取全局共享的 ObjectMapper（如需进一步配置）
     */
    public static ObjectMapper getMapper() {
        return OBJECT_MAPPER;
    }

    public static JavaTimeModule getJavaTimeModule() {
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeJsonSerializer());
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeJsonDeserializer());
        return javaTimeModule;
    }

    private static class LocalDateTimeJsonSerializer extends JsonSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
            if (value != null) {
                jsonGenerator.writeNumber(value.toInstant(ZoneId.systemDefault().getRules().getOffset(value)).toEpochMilli());
            }
        }
    }

    private static class LocalDateTimeJsonDeserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            long timestamp = jsonParser.getValueAsLong();
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

        }
    }

}
