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
        return new ScreenData(
            sessionName,
            content,
            null,
            false,
            System.currentTimeMillis(),
            80,  // default cols
            24   // default rows
        );
    }

    /**
     * Create compressed screen data.
     */
    public static ScreenData compressed(String sessionName, String content, byte[] compressedData) {
        return new ScreenData(
            sessionName,
            content,
            compressedData,
            true,
            System.currentTimeMillis(),
            80,
            24
        );
    }

    /**
     * Create screen data with dimensions.
     */
    public ScreenData withDimensions(int cols, int rows) {
        return new ScreenData(sessionName, content, compressed, isCompressed, timestamp, cols, rows);
    }
}
