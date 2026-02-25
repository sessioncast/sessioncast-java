package io.sessioncast.core.api;

import java.time.Duration;

/**
 * Options for shell command execution via the CLI agent.
 *
 * <p>The {@code cwd} option requires the {@link Capability#EXEC_CWD} capability
 * to be granted by the user's CLI agent. If the capability is not granted,
 * the exec call will fail with an error.
 *
 * <pre>{@code
 * // Simple — runs in agent's default directory
 * client.exec("echo hello");
 *
 * // With custom timeout
 * client.exec("long-task", ExecOptions.withTimeout(Duration.ofMinutes(5)));
 *
 * // With working directory (requires EXEC_CWD capability)
 * client.exec("npm test", ExecOptions.builder()
 *     .cwd("/home/user/project")
 *     .timeout(Duration.ofMinutes(2))
 *     .build());
 *
 * // In a specific tmux session
 * client.exec("make build", ExecOptions.builder()
 *     .sessionId("my-session")
 *     .build());
 * }</pre>
 */
public record ExecOptions(
    Duration timeout,
    String sessionId,
    String cwd
) {
    private static final ExecOptions DEFAULTS = new ExecOptions(null, null, null);

    /**
     * Default options: no custom timeout, no session, no cwd.
     */
    public static ExecOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Create options with just a timeout.
     */
    public static ExecOptions withTimeout(Duration timeout) {
        return new ExecOptions(timeout, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Duration timeout;
        private String sessionId;
        private String cwd;

        /**
         * Custom timeout for this execution. If null, uses the client's default apiTimeout.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Run the command in a specific tmux session on the agent's machine.
         * If null, command runs directly via shell (not in tmux).
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Set the working directory for command execution.
         * <p><b>Requires {@link Capability#EXEC_CWD}</b> to be granted by the user.
         * If the capability is not granted, the exec call will fail.
         *
         * @param cwd absolute path on the agent's machine
         */
        public Builder cwd(String cwd) {
            this.cwd = cwd;
            return this;
        }

        public ExecOptions build() {
            return new ExecOptions(timeout, sessionId, cwd);
        }
    }
}
