package io.sessioncast.core;

import io.sessioncast.core.screen.ScreenCapture;
import io.sessioncast.core.screen.ScreenCompressor;
import io.sessioncast.core.screen.ScreenData;
import io.sessioncast.core.tmux.TmuxController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Local-direct execution helper. Runs commands via tmux without a relay server.
 *
 * <pre>{@code
 * // Simple one-shot execution
 * String output = SessionCastLocal.execOnce(ExecSpec.of("ls -la"));
 *
 * // With options
 * String output = SessionCastLocal.execOnce(ExecSpec.builder()
 *     .command("npm test")
 *     .cwd("/home/user/project")
 *     .idleThreshold(Duration.ofSeconds(5))
 *     .maxWait(Duration.ofMinutes(2))
 *     .exitPattern("\\$\\s*$")
 *     .build());
 * }</pre>
 */
public final class SessionCastLocal {

    private static final Logger log = LoggerFactory.getLogger(SessionCastLocal.class);

    private SessionCastLocal() {} // utility class

    /**
     * Execute a command locally via tmux and return the output when idle or exit pattern matches.
     *
     * @param spec Execution specification
     * @return Terminal output after command completion
     */
    public static String execOnce(ExecSpec spec) {
        TmuxController tmux = new TmuxController();
        String sessionName = "sessioncast-local-" + System.currentTimeMillis();

        try {
            // Create session
            if (spec.cwd() != null) {
                tmux.createSession(sessionName, spec.cwd());
            } else {
                tmux.createSession(sessionName);
            }

            // Send command
            tmux.sendKeysWithEnter(sessionName, spec.command());

            // Wait for completion (idle detection or exit pattern)
            String result = waitForCompletion(tmux, sessionName, spec);

            return result;
        } finally {
            // Cleanup
            try {
                tmux.killSession(sessionName);
            } catch (Exception e) {
                log.warn("Failed to kill session {}: {}", sessionName, e.getMessage());
            }
        }
    }

    /**
     * Execute a command in an existing tmux session and capture output.
     *
     * @param sessionName Existing tmux session name
     * @param spec        Execution specification
     * @return Terminal output after command completion
     */
    public static String execIn(String sessionName, ExecSpec spec) {
        TmuxController tmux = new TmuxController();

        // Capture before
        String before = tmux.capturePane(sessionName);

        // Send command
        tmux.sendKeysWithEnter(sessionName, spec.command());

        // Wait for completion
        return waitForCompletion(tmux, sessionName, spec);
    }

    /**
     * Capture current screen content of a session (synchronous, one-shot).
     *
     * @param sessionName tmux session name
     * @return Plain text content of the terminal
     */
    public static String capturePane(String sessionName) {
        return new TmuxController().capturePane(sessionName);
    }

    /**
     * Capture current screen with ANSI escape sequences.
     *
     * @param sessionName tmux session name
     * @return Content with ANSI sequences
     */
    public static String capturePaneAnsi(String sessionName) {
        return new TmuxController().capturePane(sessionName, true);
    }

    private static String waitForCompletion(TmuxController tmux, String sessionName, ExecSpec spec) {
        Pattern exitPattern = spec.exitPattern() != null ? Pattern.compile(spec.exitPattern()) : null;
        long startTime = System.currentTimeMillis();
        long maxWaitMs = spec.maxWait().toMillis();
        long idleThresholdMs = spec.idleThreshold().toMillis();

        String lastContent = "";
        long lastChangeTime = System.currentTimeMillis();

        while (true) {
            // Timeout check
            if (System.currentTimeMillis() - startTime > maxWaitMs) {
                log.warn("execOnce timed out after {}ms", maxWaitMs);
                return tmux.capturePane(sessionName);
            }

            String content = tmux.capturePane(sessionName);
            if (content == null) {
                sleep(100);
                continue;
            }

            boolean changed = !content.equals(lastContent);
            if (changed) {
                lastContent = content;
                lastChangeTime = System.currentTimeMillis();
            }

            // Exit pattern match
            if (exitPattern != null && exitPattern.matcher(content).find()) {
                return content;
            }

            // Idle detection
            if (!changed && (System.currentTimeMillis() - lastChangeTime) > idleThresholdMs) {
                return content;
            }

            // Adaptive polling
            sleep(changed ? 50 : 200);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Execution specification for one-shot commands.
     */
    public record ExecSpec(
        String command,
        String cwd,
        Duration idleThreshold,
        Duration maxWait,
        String exitPattern
    ) {
        /**
         * Quick spec with just a command.
         */
        public static ExecSpec of(String command) {
            return new ExecSpec(command, null, Duration.ofSeconds(3), Duration.ofSeconds(30), null);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String command;
            private String cwd;
            private Duration idleThreshold = Duration.ofSeconds(3);
            private Duration maxWait = Duration.ofSeconds(30);
            private String exitPattern;

            public Builder command(String command) { this.command = command; return this; }
            public Builder cwd(String cwd) { this.cwd = cwd; return this; }
            public Builder idleThreshold(Duration d) { this.idleThreshold = d; return this; }
            public Builder maxWait(Duration d) { this.maxWait = d; return this; }
            public Builder exitPattern(String pattern) { this.exitPattern = pattern; return this; }

            public ExecSpec build() {
                if (command == null || command.isBlank()) {
                    throw new IllegalArgumentException("command is required");
                }
                return new ExecSpec(command, cwd, idleThreshold, maxWait, exitPattern);
            }
        }
    }
}
