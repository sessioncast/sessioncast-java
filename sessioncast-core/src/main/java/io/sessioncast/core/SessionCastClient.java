package io.sessioncast.core;

import io.sessioncast.core.client.RelayConfig;
import io.sessioncast.core.client.RelayWebSocketClient;
import io.sessioncast.core.event.Disposable;
import io.sessioncast.core.event.Event;
import io.sessioncast.core.event.Event.*;
import io.sessioncast.core.event.EventBus;
import io.sessioncast.core.protocol.Message.*;
import io.sessioncast.core.screen.ScreenCapture;
import io.sessioncast.core.screen.ScreenCompressor;
import io.sessioncast.core.screen.ScreenData;
import io.sessioncast.core.tmux.SpecialKey;
import io.sessioncast.core.tmux.TmuxController;
import io.sessioncast.core.tmux.TmuxSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Main client for SessionCast - manages tmux sessions and streams them to relay.
 *
 * <pre>{@code
 * SessionCastClient client = SessionCastClient.builder()
 *     .relay("wss://relay.sessioncast.io/ws")
 *     .token("agt_xxx")
 *     .machineId("my-machine")
 *     .build();
 *
 * client.connect().join();
 *
 * // Create and stream a session
 * client.createSession("my-session", "/path/to/workdir");
 * client.sendKeys("my-session", "echo hello", true);
 *
 * // Subscribe to events
 * client.onScreen("my-session", data -> {
 *     System.out.println("Screen updated: " + data.content());
 * });
 *
 * client.close();
 * }</pre>
 */
public class SessionCastClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SessionCastClient.class);

    private final RelayConfig config;
    private final TmuxController tmux;
    private final ScreenCapture screenCapture;
    private final EventBus eventBus;
    private final RelayWebSocketClient wsClient;
    private final ScheduledExecutorService scheduler;

    // Session tracking
    private final Map<String, Disposable> sessionScreenSubscriptions = new ConcurrentHashMap<>();
    private final boolean autoStreamOnCreate;

    private SessionCastClient(Builder builder) {
        this.config = builder.buildConfig();
        this.eventBus = new EventBus(true);
        this.tmux = builder.tmuxController != null ? builder.tmuxController : new TmuxController();
        this.screenCapture = new ScreenCapture(tmux, new ScreenCompressor());
        this.wsClient = new RelayWebSocketClient(config, eventBus);
        this.autoStreamOnCreate = builder.autoStreamOnCreate;

        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "sessioncast-client");
            t.setDaemon(true);
            return t;
        });

        // Set up internal event handlers
        setupEventHandlers();
    }

    public static Builder builder() {
        return new Builder();
    }

    // ========== Connection Management ==========

    /**
     * Connect to the relay server.
     */
    public CompletableFuture<Void> connect() {
        return wsClient.connect();
    }

    /**
     * Disconnect from the relay server.
     */
    public void disconnect() {
        wsClient.disconnect();
    }

    /**
     * Check if connected to relay.
     */
    public boolean isConnected() {
        return wsClient.isConnected();
    }

    // ========== Session Management ==========

    /**
     * List all tmux sessions.
     */
    public List<TmuxSession> listSessions() {
        return tmux.listSessions();
    }

    /**
     * Check if a session exists.
     */
    public boolean sessionExists(String sessionName) {
        return tmux.sessionExists(sessionName);
    }

    /**
     * Create a new tmux session.
     */
    public CompletableFuture<String> createSession(String sessionName, String workDir) {
        return CompletableFuture.supplyAsync(() -> {
            tmux.createSession(sessionName, workDir);

            if (autoStreamOnCreate) {
                startStreaming(sessionName);
            }

            eventBus.publish(new SessionCreatedEvent(sessionName, Instant.now()));
            return sessionName;
        }, scheduler);
    }

    /**
     * Create a session without specifying work directory.
     */
    public CompletableFuture<String> createSession(String sessionName) {
        return createSession(sessionName, null);
    }

    /**
     * Kill a tmux session.
     */
    public CompletableFuture<Void> killSession(String sessionName) {
        return CompletableFuture.runAsync(() -> {
            stopStreaming(sessionName);
            tmux.killSession(sessionName);
            eventBus.publish(new SessionKilledEvent(sessionName, Instant.now()));
        }, scheduler);
    }

    // ========== Key Input ==========

    /**
     * Send keys to a session.
     *
     * @param sessionName Target session
     * @param keys        Keys to send
     */
    public CompletableFuture<Void> sendKeys(String sessionName, String keys) {
        return sendKeys(sessionName, keys, false);
    }

    /**
     * Send keys to a session with optional Enter.
     *
     * @param sessionName Target session
     * @param keys        Keys to send
     * @param enter       If true, press Enter after keys
     */
    public CompletableFuture<Void> sendKeys(String sessionName, String keys, boolean enter) {
        return CompletableFuture.runAsync(() -> {
            if (enter) {
                tmux.sendKeysWithEnter(sessionName, keys);
            } else {
                tmux.sendKeys(sessionName, keys, true);
            }
        }, scheduler);
    }

    /**
     * Send a special key to a session.
     */
    public CompletableFuture<Void> sendSpecialKey(String sessionName, SpecialKey key) {
        return CompletableFuture.runAsync(() -> {
            tmux.sendSpecialKey(sessionName, key);
        }, scheduler);
    }

    // ========== Screen Streaming ==========

    /**
     * Start streaming a session's screen to relay.
     */
    public void startStreaming(String sessionName) {
        if (sessionScreenSubscriptions.containsKey(sessionName)) {
            return;
        }

        screenCapture.start(sessionName, data -> {
            if (wsClient.isConnected()) {
                if (data.isCompressed()) {
                    wsClient.send(new ScreenGzMessage(sessionName, data.getBase64Content()));
                } else {
                    wsClient.send(new ScreenMessage(sessionName, data.getBase64Content()));
                }
            }
        });

        sessionScreenSubscriptions.put(sessionName, Disposable.fromRunnable(() -> {
            screenCapture.stop(sessionName);
        }));

        log.info("Started streaming session: {}", sessionName);
    }

    /**
     * Stop streaming a session's screen.
     */
    public void stopStreaming(String sessionName) {
        Disposable subscription = sessionScreenSubscriptions.remove(sessionName);
        if (subscription != null) {
            subscription.dispose();
            log.info("Stopped streaming session: {}", sessionName);
        }
    }

    // ========== Event Subscription ==========

    /**
     * Subscribe to screen updates for a specific session.
     */
    public Disposable onScreen(String sessionName, Consumer<ScreenData> handler) {
        return eventBus.subscribe(ScreenEvent.class, event -> {
            if (event.sessionName().equals(sessionName)) {
                handler.accept(event.data());
            }
        });
    }

    /**
     * Subscribe to all screen updates.
     */
    public Disposable onScreen(Consumer<ScreenEvent> handler) {
        return eventBus.subscribe(ScreenEvent.class, handler);
    }

    /**
     * Subscribe to connection events.
     */
    public Disposable onConnect(Runnable handler) {
        return eventBus.subscribe(ConnectedEvent.class, e -> handler.run());
    }

    /**
     * Subscribe to disconnect events.
     */
    public Disposable onDisconnect(Consumer<DisconnectReason> handler) {
        return eventBus.subscribe(DisconnectedEvent.class, e -> handler.accept(e.reason()));
    }

    /**
     * Subscribe to error events.
     */
    public Disposable onError(Consumer<SessionCastException> handler) {
        return eventBus.subscribe(ErrorEvent.class, e -> handler.accept(e.exception()));
    }

    /**
     * Subscribe to keys received events (from remote).
     */
    public Disposable onKeysReceived(Consumer<KeysReceivedEvent> handler) {
        return eventBus.subscribe(KeysReceivedEvent.class, handler);
    }

    // ========== Internal Setup ==========

    private void setupEventHandlers() {
        // Handle incoming keys from relay
        eventBus.subscribe(KeysReceivedEvent.class, event -> {
            log.debug("Received keys for {}: {}", event.sessionName(), event.keys());
            if (event.enter()) {
                tmux.sendKeysWithEnter(event.sessionName(), event.keys());
            } else {
                tmux.sendKeys(event.sessionName(), event.keys(), true);
            }
        });

        // Handle resize requests
        eventBus.subscribe(ResizeRequestEvent.class, event -> {
            log.debug("Resize request for {}: {}x{}", event.sessionName(), event.cols(), event.rows());
            tmux.resizeWindow(event.sessionName(), event.cols(), event.rows());
        });

        // Handle session creation requests from relay
        eventBus.subscribe(SessionCreatedEvent.class, event -> {
            // Session created externally, start streaming if connected
            if (wsClient.isConnected() && !sessionScreenSubscriptions.containsKey(event.sessionName())) {
                startStreaming(event.sessionName());
            }
        });

        // Handle session kill requests
        eventBus.subscribe(SessionKilledEvent.class, event -> {
            stopStreaming(event.sessionName());
        });
    }

    // ========== Cleanup ==========

    @Override
    public void close() {
        // Stop all streaming
        sessionScreenSubscriptions.keySet().forEach(this::stopStreaming);

        // Close components
        screenCapture.close();
        wsClient.close();
        eventBus.close();
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("SessionCastClient closed");
    }

    // ========== Builder ==========

    public static class Builder {
        private String relay = "wss://relay.sessioncast.io/ws";
        private String token;
        private String machineId;
        private String label;
        private boolean reconnect = true;
        private Duration reconnectInitialDelay = Duration.ofSeconds(2);
        private Duration reconnectMaxDelay = Duration.ofSeconds(60);
        private int maxReconnectAttempts = 5;
        private boolean autoStreamOnCreate = true;
        private TmuxController tmuxController;

        public Builder relay(String url) {
            this.relay = url;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder machineId(String machineId) {
            this.machineId = machineId;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder reconnect(boolean enabled) {
            this.reconnect = enabled;
            return this;
        }

        public Builder reconnectDelay(Duration initial, Duration max) {
            this.reconnectInitialDelay = initial;
            this.reconnectMaxDelay = max;
            return this;
        }

        public Builder maxReconnectAttempts(int attempts) {
            this.maxReconnectAttempts = attempts;
            return this;
        }

        public Builder autoStreamOnCreate(boolean enabled) {
            this.autoStreamOnCreate = enabled;
            return this;
        }

        public Builder tmuxController(TmuxController controller) {
            this.tmuxController = controller;
            return this;
        }

        RelayConfig buildConfig() {
            return RelayConfig.builder()
                .url(relay)
                .token(token)
                .machineId(machineId)
                .label(label)
                .reconnectEnabled(reconnect)
                .reconnectInitialDelay(reconnectInitialDelay)
                .reconnectMaxDelay(reconnectMaxDelay)
                .maxReconnectAttempts(maxReconnectAttempts)
                .build();
        }

        public SessionCastClient build() {
            return new SessionCastClient(this);
        }
    }
}
