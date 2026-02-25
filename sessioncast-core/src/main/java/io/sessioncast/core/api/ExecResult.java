package io.sessioncast.core.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Result of a shell command execution on the CLI agent's machine.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecResult(
    @JsonProperty("exitCode") int exitCode,
    @JsonProperty("stdout") String stdout,
    @JsonProperty("stderr") String stderr,
    @JsonProperty("duration") long duration
) {
    /**
     * Check if the command executed successfully (exit code 0).
     */
    public boolean isSuccess() {
        return exitCode == 0;
    }
}
