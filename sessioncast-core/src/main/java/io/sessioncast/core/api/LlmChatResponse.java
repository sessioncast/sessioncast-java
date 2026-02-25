package io.sessioncast.core.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from an LLM chat request via the CLI agent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmChatResponse(
    @JsonProperty("id") String id,
    @JsonProperty("model") String model,
    @JsonProperty("choices") List<Choice> choices,
    @JsonProperty("usage") Usage usage,
    @JsonProperty("error") Error error
) {
    /**
     * Get the content of the first choice, or null if no choices.
     */
    public String content() {
        if (choices != null && !choices.isEmpty()) {
            return choices.get(0).message() != null ? choices.get(0).message().content() : null;
        }
        return null;
    }

    /**
     * Check if the response contains an error.
     */
    public boolean hasError() {
        return error != null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
        @JsonProperty("index") int index,
        @JsonProperty("message") ChoiceMessage message,
        @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChoiceMessage(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Error(
        @JsonProperty("message") String message,
        @JsonProperty("code") String code
    ) {}
}
