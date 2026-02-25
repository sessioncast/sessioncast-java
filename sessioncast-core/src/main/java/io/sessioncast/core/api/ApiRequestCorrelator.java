package io.sessioncast.core.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * Tracks pending API requests and resolves CompletableFutures when responses arrive.
 */
public class ApiRequestCorrelator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestCorrelator.class);

    private final ConcurrentHashMap<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutScheduler;
    private final Duration defaultTimeout;

    public ApiRequestCorrelator(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "api-request-timeout");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Register a pending request with the default timeout.
     *
     * @param requestId unique request identifier
     * @return a CompletableFuture that will be completed with the response payload
     */
    public CompletableFuture<String> register(String requestId) {
        return register(requestId, defaultTimeout);
    }

    /**
     * Register a pending request with a custom timeout.
     *
     * @param requestId unique request identifier
     * @param timeout   how long to wait before timing out
     * @return a CompletableFuture that will be completed with the response payload
     */
    public CompletableFuture<String> register(String requestId, Duration timeout) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(requestId, future);

        timeoutScheduler.schedule(() -> {
            if (pending.remove(requestId, future)) {
                future.completeExceptionally(
                    new TimeoutException("API request timed out after " + timeout.toSeconds() + "s: " + requestId)
                );
                log.warn("API request timed out: {}", requestId);
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * Complete a pending request with a response payload.
     *
     * @param requestId the request to complete
     * @param payload   the response payload (JSON string)
     * @return true if the request was found and completed
     */
    public boolean complete(String requestId, String payload) {
        CompletableFuture<String> future = pending.remove(requestId);
        if (future != null) {
            future.complete(payload);
            return true;
        }
        log.warn("No pending request found for requestId: {}", requestId);
        return false;
    }

    /**
     * Complete a pending request exceptionally.
     *
     * @param requestId the request to complete
     * @param error     the error message
     * @return true if the request was found and completed
     */
    public boolean completeExceptionally(String requestId, String error) {
        CompletableFuture<String> future = pending.remove(requestId);
        if (future != null) {
            future.completeExceptionally(new RuntimeException("API request failed: " + error));
            return true;
        }
        return false;
    }

    /**
     * Get the number of pending requests.
     */
    public int pendingCount() {
        return pending.size();
    }

    @Override
    public void close() {
        timeoutScheduler.shutdown();
        // Cancel all pending requests
        pending.forEach((id, future) -> {
            future.completeExceptionally(new CancellationException("Correlator closed"));
        });
        pending.clear();
    }
}
