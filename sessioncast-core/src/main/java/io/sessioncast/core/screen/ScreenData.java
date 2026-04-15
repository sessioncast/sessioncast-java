package io.sessioncast.core.screen;

import java.util.Base64;

/**
 * Represents captured screen data.
 */
public record ScreenData(
    String sessionName,
    String content,           // Raw ANSI content
    byte[] compressed,        // Gzip compressed data (nullable)
    boolean isCompressed,
    boolean idle,             // Whether the screen is idle (no changes for idleThreshold)
    long timestamp,
    int cols,
    int rows
) {
    /**
     * Get content as base64 encoded string.
     */
    public String getBase64Content() {
        if (isCompressed && compressed != null) {
            return Base64.getEncoder().encodeToString(compressed);
        }
        return Base64.getEncoder().encodeToString(content.getBytes());
    }

    /**
     * Create uncompressed screen data.
     */
    public static ScreenData uncompressed(String sessionName, String content) {
        return new ScreenData(sessionName, content, null, false, false,
            System.currentTimeMillis(), 80, 24);
    }

    /**
     * Create compressed screen data.
     */
    public static ScreenData compressed(String sessionName, String content, byte[] compressedData) {
        return new ScreenData(sessionName, content, compressedData, true, false,
            System.currentTimeMillis(), 80, 24);
    }

    /**
     * Create screen data with idle flag.
     */
    public ScreenData withIdle(boolean idle) {
        return new ScreenData(sessionName, content, compressed, isCompressed, idle, timestamp, cols, rows);
    }

    /**
     * Create screen data with dimensions.
     */
    public ScreenData withDimensions(int cols, int rows) {
        return new ScreenData(sessionName, content, compressed, isCompressed, idle, timestamp, cols, rows);
    }
}
