package io.sessioncast.core.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Request for an LLM chat via the CLI agent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmChatRequest(
    @JsonProperty("model") String model,
    @JsonProperty("messages") List<LlmMessage> messages,
    @JsonProperty("temperature") Double temperature,
    @JsonProperty("max_tokens") Integer maxTokens,
    @JsonProperty("stream") boolean stream
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LlmMessage(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content
    ) {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private final List<LlmMessage> messages = new ArrayList<>();
        private Double temperature;
        private Integer maxTokens;
        private boolean stream = false;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder addMessage(String role, String content) {
            this.messages.add(new LlmMessage(role, content));
            return this;
        }

        public Builder system(String content) {
            return addMessage("system", content);
        }

        public Builder user(String content) {
            return addMessage("user", content);
        }

        public Builder assistant(String content) {
            return addMessage("assistant", content);
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        public LlmChatRequest build() {
            return new LlmChatRequest(model, List.copyOf(messages), temperature, maxTokens, stream);
        }
    }
}
