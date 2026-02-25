package io.sessioncast.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Typed wrapper for API responses from the CLI agent.
 */
public record ApiResponse(String requestId, String rawPayload) {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();

    /**
     * Parse the raw payload into a typed object.
     *
     * @param type   the target class
     * @param mapper the ObjectMapper to use
     * @param <T>    the target type
     * @return parsed object
     */
    public <T> T as(Class<T> type, ObjectMapper mapper) {
        try {
            return mapper.readValue(rawPayload, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse API response as " + type.getSimpleName(), e);
        }
    }

    /**
     * Parse the raw payload into a typed object using the default ObjectMapper.
     */
    public <T> T as(Class<T> type) {
        return as(type, DEFAULT_MAPPER);
    }
}
