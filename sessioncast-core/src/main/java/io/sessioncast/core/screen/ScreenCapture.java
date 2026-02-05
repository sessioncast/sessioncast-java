package io.sessioncast.core.screen;

import io.sessioncast.core.tmux.TmuxController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Adaptive screen capture with configurable polling intervals.
 */
public class ScreenCapture implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ScreenCapture.class);

    private static final Duration DEFAULT_ACTIVE_INTERVAL = Duration.ofMillis(50);
    private static final Duration DEFAULT_IDLE_INTERVAL = Duration.ofMillis(200);
    private static final Duration DEFAULT_IDLE_THRESHOLD = Duration.ofSeconds(2);
    private static final Duration DEFAULT_FORCE_SEND_INTERVAL = Duration.ofSeconds(10);

    private final TmuxController tmux;
    private final ScreenCompressor compressor;
    private final ScheduledExecutorService scheduler;

    private Duration activeInterval = DEFAULT_ACTIVE_INTERVAL;
    private Duration idleInterval = DEFAULT_IDLE_INTERVAL;
    private Duration idleThreshold = DEFAULT_IDLE_THRESHOLD;
    private Duration forceSendInterval = DEFAULT_FORCE_SEND_INTERVAL;

    private final Map<String, CaptureTask> activeTasks = new ConcurrentHashMap<>();

    public ScreenCapture(TmuxController tmux) {
        this(tmux, new ScreenCompressor());
    }

    public ScreenCapture(TmuxController tmux, ScreenCompressor compressor) {
        this.tmux = tmux;
        this.compressor = compressor;
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "screen-capture");
            t.setDaemon(true);
            return t;
        });
    }

    // ========== Configuration ==========

    public ScreenCapture setActiveInterval(Duration interval) {
        this.activeInterval = interval;
        return this;
    }

    public ScreenCapture setIdleInterval(Duration interval) {
        this.idleInterval = interval;
        return this;
    }

    public ScreenCapture setIdleThreshold(Duration threshold) {
        this.idleThreshold = threshold;
        return this;
    }

    public ScreenCapture setForceSendInterval(Duration interval) {
        this.forceSendInterval = interval;
        return this;
    }

    // ========== Capture Control ==========

    /**
     * Start capturing screen for a session.
     *
     * @param sessionName Session to capture
     * @param handler     Handler for captured screen data
     */
    public void start(String sessionName, Consumer<ScreenData> handler) {
        if (activeTasks.containsKey(sessionName)) {
            log.warn("Capture already running for session: {}", sessionName);
            return;
        }

        CaptureTask task = new CaptureTask(sessionName, handler);
        activeTasks.put(sessionName, task);
        task.start();

        log.info("Started screen capture for session: {}", sessionName);
    }

    /**
     * Stop capturing screen for a session.
     */
    public void stop(String sessionName) {
        CaptureTask task = activeTasks.remove(sessionName);
        if (task != null) {
            task.stop();
            log.info("Stopped screen capture for session: {}", sessionName);
        }
    }

    /**
     * Stop all active captures.
     */
    public void stopAll() {
        activeTasks.keySet().forEach(this::stop);
    }

    /**
     * Check if capture is running for a session.
     */
    public boolean isCapturing(String sessionName) {
        return activeTasks.containsKey(sessionName);
    }

    @Override
    public void close() {
        stopAll();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ========== Inner Classes ==========

    private class CaptureTask {
        private final String sessionName;
        private final Consumer<ScreenData> handler;

        private volatile boolean running = false;
        private ScheduledFuture<?> future;

        private String lastContent = "";
        private long lastChangeTime = System.currentTimeMillis();
        private long lastSendTime = 0;
        private boolean isIdle = false;

        CaptureTask(String sessionName, Consumer<ScreenData> handler) {
            this.sessionName = sessionName;
            this.handler = handler;
        }

        void start() {
            running = true;
            scheduleNext();
        }

        void stop() {
            running = false;
            if (future != null) {
                future.cancel(false);
            }
        }

        private void scheduleNext() {
            if (!running) return;

            Duration delay = isIdle ? idleInterval : activeInterval;
            future = scheduler.schedule(this::capture, delay.toMillis(), TimeUnit.MILLISECONDS);
        }

        private void capture() {
            if (!running) return;

            try {
                String content = tmux.capturePaneForStream(sessionName);
                if (content == null) {
                    scheduleNext();
                    return;
                }

                long now = System.currentTimeMillis();
                boolean contentChanged = !content.equals(lastContent);
                boolean forceSend = (now - lastSendTime) >= forceSendInterval.toMillis();

                if (contentChanged) {
                    lastContent = content;
                    lastChangeTime = now;
                    isIdle = false;
                } else if ((now - lastChangeTime) >= idleThreshold.toMillis()) {
                    isIdle = true;
                }

                // Send if content changed or force send interval reached
                if (contentChanged || forceSend) {
                    ScreenData data = compressor.compressScreenData(sessionName, content);
                    handler.accept(data);
                    lastSendTime = now;
                }

            } catch (Exception e) {
                log.error("Error capturing screen for {}: {}", sessionName, e.getMessage());
            }

            scheduleNext();
        }
    }
}
