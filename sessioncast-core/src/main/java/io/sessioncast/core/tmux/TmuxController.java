package io.sessioncast.core.tmux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Controller for tmux operations.
 */
public class TmuxController {

    private static final Logger log = LoggerFactory.getLogger(TmuxController.class);

    private static final long DEFAULT_TIMEOUT_SECONDS = 10;
    private static final String TMUX_CMD = "tmux";

    private final long timeoutSeconds;

    public TmuxController() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    public TmuxController(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    // ========== Session Management ==========

    /**
     * List all tmux sessions.
     */
    public List<TmuxSession> listSessions() {
        String output = executeCommand(TMUX_CMD, "ls");
        if (output == null || output.isBlank() || output.contains("no server running")) {
            return List.of();
        }

        List<TmuxSession> sessions = new ArrayList<>();
        for (String line : output.split("\n")) {
            TmuxSession session = TmuxSession.parse(line);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    /**
     * Check if a session exists.
     */
    public boolean sessionExists(String sessionName) {
        String result = executeCommand(TMUX_CMD, "has-session", "-t", sessionName);
        return result != null && !result.contains("can't find session");
    }

    /**
     * Create a new tmux session.
     */
    public void createSession(String sessionName) {
        createSession(sessionName, null);
    }

    /**
     * Create a new tmux session with a specific working directory.
     */
    public void createSession(String sessionName, String workDir) {
        if (sessionExists(sessionName)) {
            log.warn("Session already exists: {}", sessionName);
            return;
        }

        List<String> args = new ArrayList<>();
        args.add(TMUX_CMD);
        args.add("new-session");
        args.add("-d");
        args.add("-s");
        args.add(sessionName);

        if (workDir != null && !workDir.isBlank()) {
            args.add("-c");
            args.add(workDir);
        }

        executeCommand(args.toArray(String[]::new));
        log.info("Created session: {}", sessionName);
    }

    /**
     * Kill a tmux session.
     */
    public void killSession(String sessionName) {
        executeCommand(TMUX_CMD, "kill-session", "-t", sessionName);
        log.info("Killed session: {}", sessionName);
    }

    // ========== Window/Pane Operations ==========

    /**
     * Resize a window.
     */
    public void resizeWindow(String sessionName, int cols, int rows) {
        executeCommand(TMUX_CMD, "resize-window", "-t", sessionName, "-x", String.valueOf(cols), "-y", String.valueOf(rows));
    }

    /**
     * Get the current working directory of a pane.
     */
    public String getPaneWorkDir(String sessionName) {
        String result = executeCommand(TMUX_CMD, "display-message", "-t", sessionName, "-p", "#{pane_current_path}");
        return result != null ? result.trim() : null;
    }

    // ========== Key Input ==========

    /**
     * Send keys to a tmux target.
     *
     * @param target  Session name, or session:window, or session:window.pane
     * @param keys    Keys to send
     * @param literal If true, use -l flag (literal mode)
     */
    public void sendKeys(String target, String keys, boolean literal) {
        if (keys == null || keys.isEmpty()) return;

        String sanitizedKeys = sanitizeKeys(keys);

        List<String> args = new ArrayList<>();
        args.add(TMUX_CMD);
        args.add("send-keys");
        args.add("-t");
        args.add(target);

        if (literal) {
            args.add("-l");
        }

        args.add(sanitizedKeys);

        executeCommand(args.toArray(String[]::new));
    }

    /**
     * Send keys and press Enter.
     */
    public void sendKeysWithEnter(String target, String keys) {
        sendKeys(target, keys, true);
        sendSpecialKey(target, SpecialKey.ENTER);
    }

    /**
     * Send a special key.
     */
    public void sendSpecialKey(String target, SpecialKey key) {
        executeCommand(TMUX_CMD, "send-keys", "-t", target, key.getTmuxKey());
    }

    // ========== Screen Capture ==========

    /**
     * Capture pane content (plain text).
     */
    public String capturePane(String sessionName) {
        return capturePane(sessionName, false);
    }

    /**
     * Capture pane content.
     *
     * @param sessionName         Session name
     * @param withEscapeSequences If true, include ANSI escape sequences (-e flag)
     */
    public String capturePane(String sessionName, boolean withEscapeSequences) {
        List<String> args = new ArrayList<>();
        args.add(TMUX_CMD);
        args.add("capture-pane");
        args.add("-t");
        args.add(sessionName);
        args.add("-p");  // Print to stdout

        if (withEscapeSequences) {
            args.add("-e");  // Include escape sequences
            args.add("-N");  // Preserve trailing spaces
        }

        return executeCommand(args.toArray(String[]::new));
    }

    /**
     * Capture pane with clear screen prefix (for streaming).
     */
    public String capturePaneForStream(String sessionName) {
        String content = capturePane(sessionName, true);
        if (content == null) return null;

        // Add clear screen + home cursor prefix
        return "\u001b[2J\u001b[H" + content;
    }

    // ========== Helper Methods ==========

    /**
     * Sanitize keys to prevent shell injection.
     * Removes dangerous characters like quotes, backticks, dollar signs.
     */
    private String sanitizeKeys(String keys) {
        // Remove potentially dangerous characters
        return keys
            .replace("\"", "")
            .replace("'", "")
            .replace("`", "")
            .replace("$", "");
    }

    /**
     * Execute a command and return output.
     */
    private String executeCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Command timed out: {}", String.join(" ", command));
                return null;
            }

            return output.toString().trim();

        } catch (IOException | InterruptedException e) {
            log.error("Failed to execute command: {}", String.join(" ", command), e);
            return null;
        }
    }
}
