package io.sessioncast.core.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Codec for encoding/decoding WebSocket messages.
 */
public class MessageCodec {

    private static final Logger log = LoggerFactory.getLogger(MessageCodec.class);

    private final ObjectMapper mapper;

    public MessageCodec() {
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    public MessageCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Encode a message to JSON string.
     */
    public String encode(Message message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to encode message: {}", message, e);
            throw new RuntimeException("Failed to encode message", e);
        }
    }

    /**
     * Decode a JSON string to a message.
     */
    public Message decode(String json) {
        try {
            return mapper.readValue(json, Message.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to decode message: {}", json, e);
            throw new RuntimeException("Failed to decode message", e);
        }
    }

    /**
     * Decode a JSON string to a specific message type.
     */
    public <T extends Message> T decode(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.error("Failed to decode message as {}: {}", type.getSimpleName(), json, e);
            throw new RuntimeException("Failed to decode message", e);
        }
    }

    /**
     * Get the underlying ObjectMapper.
     */
    public ObjectMapper getMapper() {
        return mapper;
    }
}
