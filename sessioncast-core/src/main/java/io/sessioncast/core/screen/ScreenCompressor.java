package io.sessioncast.core.screen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compressor for screen data using gzip.
 */
public class ScreenCompressor {

    private static final Logger log = LoggerFactory.getLogger(ScreenCompressor.class);

    private static final int DEFAULT_COMPRESSION_THRESHOLD = 512;

    private final int compressionThreshold;

    public ScreenCompressor() {
        this(DEFAULT_COMPRESSION_THRESHOLD);
    }

    public ScreenCompressor(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    /**
     * Check if content should be compressed based on size.
     */
    public boolean shouldCompress(String content) {
        return content != null && content.length() > compressionThreshold;
    }

    /**
     * Compress string content using gzip.
     *
     * @param content Content to compress
     * @return Compressed bytes, or null if compression fails
     */
    public byte[] compress(String content) {
        if (content == null || content.isEmpty()) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {

            gzip.write(content.getBytes(StandardCharsets.UTF_8));
            gzip.finish();

            return baos.toByteArray();

        } catch (Exception e) {
            log.warn("Failed to compress content: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Decompress gzip bytes to string.
     *
     * @param data Compressed data
     * @return Decompressed string, or null if decompression fails
     */
    public String decompress(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzip = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int len;
            while ((len = gzip.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }

            return baos.toString(StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.warn("Failed to decompress data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Compress content and create ScreenData.
     * Returns uncompressed data if compression fails or is not beneficial.
     */
    public ScreenData compressScreenData(String sessionName, String content) {
        if (!shouldCompress(content)) {
            return ScreenData.uncompressed(sessionName, content);
        }

        byte[] compressed = compress(content);
        if (compressed == null) {
            return ScreenData.uncompressed(sessionName, content);
        }

        // Only use compression if it actually reduces size
        if (compressed.length >= content.getBytes(StandardCharsets.UTF_8).length) {
            return ScreenData.uncompressed(sessionName, content);
        }

        return ScreenData.compressed(sessionName, content, compressed);
    }
}
