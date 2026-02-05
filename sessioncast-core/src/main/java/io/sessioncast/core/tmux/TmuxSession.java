package io.sessioncast.core.tmux;

import java.time.LocalDateTime;

/**
 * Represents a tmux session.
 */
public record TmuxSession(
    String name,
    int windows,
    LocalDateTime created,
    boolean attached
) {
    /**
     * Parse tmux ls output format: "session_name: N windows (created Mon Jan 26 19:54:13 2026) (attached)"
     */
    public static TmuxSession parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        try {
            // Extract session name (before colon)
            int colonIdx = line.indexOf(':');
            if (colonIdx < 0) {
                return new TmuxSession(line.trim(), 1, LocalDateTime.now(), false);
            }

            String name = line.substring(0, colonIdx).trim();

            // Extract window count
            int windows = 1;
            int windowsIdx = line.indexOf(" windows");
            if (windowsIdx < 0) {
                windowsIdx = line.indexOf(" window");
            }
            if (windowsIdx > colonIdx) {
                String numStr = line.substring(colonIdx + 1, windowsIdx).trim();
                try {
                    windows = Integer.parseInt(numStr);
                } catch (NumberFormatException ignored) {}
            }

            // Check if attached
            boolean attached = line.contains("(attached)");

            return new TmuxSession(name, windows, LocalDateTime.now(), attached);
        } catch (Exception e) {
            // Fallback: just use the line as session name
            return new TmuxSession(line.trim(), 1, LocalDateTime.now(), false);
        }
    }
}
