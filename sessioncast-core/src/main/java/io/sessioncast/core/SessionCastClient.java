package io.sessioncast.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sessioncast.core.api.*;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final boolean localMode; // true = no relay, direct tmux only
    private final ScheduledExecutorService scheduler;

    // Session tracking
    private final Map<String, Disposable> sessionScreenSubscriptions = new ConcurrentHashMap<>();
    private final boolean autoStreamOnCreate;

    // API support
    private final ApiRequestCorrelator apiCorrelator;
    private final ObjectMapper objectMapper;
    private final Duration apiTimeout;
    private final Duration llmTimeout;
    private final java.util.Set<Capability> requiredCapabilities;
    private final java.util.Set<Capability> grantedCapabilities = ConcurrentHashMap.newKeySet();
    private final CompletableFuture<java.util.Set<Capability>> capabilityFuture = new CompletableFuture<>();

    private SessionCastClient(Builder builder) {
        this.localMode = builder.token == null || builder.token.isBlank();
        this.config = localMode ? null : builder.buildConfig();
        this.eventBus = new EventBus(true);
        this.tmux = builder.tmuxController != null ? builder.tmuxController : new TmuxController();
        this.screenCapture = new ScreenCapture(tmux, new ScreenCompressor());
        this.wsClient = localMode ? null : new RelayWebSocketClient(config, eventBus);
        this.autoStreamOnCreate = builder.autoStreamOnCreate;
        this.apiTimeout = builder.apiTimeout;
        this.llmTimeout = builder.llmTimeout;
        this.objectMapper = new ObjectMapper();
        this.apiCorrelator = new ApiRequestCorrelator(builder.apiTimeout);
        this.wsClient.setApiCorrelator(apiCorrelator);
        this.requiredCapabilities = builder.requiredCapabilities.isEmpty()
            ? java.util.Set.of() : java.util.EnumSet.copyOf(builder.requiredCapabilities);
        if (!this.requiredCapabilities.isEmpty()) {
            String capsStr = this.requiredCapabilities.stream()
                .map(Capability::value)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
            this.wsClient.setRequiredCapabilities(capsStr);
        }

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
        if (localMode) {
            log.info("Local mode — no relay connection needed");
            return CompletableFuture.completedFuture(null);
        }
        return wsClient.connect();
    }

    /**
     * Check if running in local-direct mode (no relay).
     */
    public boolean isLocalMode() {
        return localMode;
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

    // ========== Non-Interactive API ==========

    /**
     * Execute a shell command on the CLI agent's machine.
     * The command runs in the agent's default working directory.
     *
     * <pre>{@code
     * client.exec("echo hello").thenAccept(result -> {
     *     System.out.println(result.stdout()); // "hello\n"
     * });
     * }</pre>
     *
     * @param command the shell command to execute
     * @return a future that resolves with the execution result
     */
    public CompletableFuture<ExecResult> exec(String command) {
        return exec(command, ExecOptions.defaults());
    }

    /**
     * Execute a shell command with options.
     *
     * <pre>{@code
     * client.exec("ls -la", ExecOptions.builder()
     *     .timeout(Duration.ofSeconds(60))
     *     .build());
     * }</pre>
     *
     * @param command the shell command to execute
     * @param options execution options (timeout, etc.)
     * @return a future that resolves with the execution result
     */
    public CompletableFuture<ExecResult> exec(String command, ExecOptions options) {
        // Local mode: execute directly via tmux
        if (localMode) {
            return CompletableFuture.supplyAsync(() -> {
                String output = SessionCastLocal.execOnce(SessionCastLocal.ExecSpec.builder()
                    .command(command)
                    .cwd(options.cwd())
                    .maxWait(options.timeout() != null ? options.timeout() : apiTimeout)
                    .build());
                return new ExecResult(0, output != null ? output : "", "", 0);
            });
        }

        // Check EXEC_CWD capability if cwd is specified
        if (options.cwd() != null && !grantedCapabilities.contains(Capability.EXEC_CWD)) {
            return CompletableFuture.failedFuture(
                new SecurityException("Capability EXEC_CWD not granted. "
                    + "The user's CLI agent must allow exec_cwd to specify a working directory.")
            );
        }

        String requestId = UUID.randomUUID().toString();

        Map<String, String> payload = new HashMap<>();
        payload.put("command", command);
        if (options.cwd() != null) {
            payload.put("cwd", options.cwd());
        }
        if (options.sessionId() != null) {
            payload.put("sessionId", options.sessionId());
        }

        Map<String, String> meta = new HashMap<>();
        meta.put("requestId", requestId);
        try {
            meta.put("payload", objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }

        Duration timeout = options.timeout() != null ? options.timeout() : apiTimeout;
        CompletableFuture<String> responseFuture = apiCorrelator.register(requestId, timeout);
        wsClient.send(new ExecMessage(meta));

        return responseFuture.thenApply(raw -> {
            try {
                return objectMapper.readValue(raw, ExecResult.class);
            } catch (JsonProcessingException e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Send an LLM chat request to the CLI agent's local AI.
     *
     * @param request the chat request
     * @return a future that resolves with the LLM response
     */
    public CompletableFuture<LlmChatResponse> llmChat(LlmChatRequest request) {
        String requestId = UUID.randomUUID().toString();

        Map<String, String> meta = new HashMap<>();
        meta.put("requestId", requestId);
        try {
            meta.put("payload", objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }

        CompletableFuture<String> responseFuture = apiCorrelator.register(requestId, llmTimeout);
        wsClient.send(new LlmChatMessage(meta));

        return responseFuture.thenApply(raw -> {
            try {
                return objectMapper.readValue(raw, LlmChatResponse.class);
            } catch (JsonProcessingException e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Send keys to a tmux session via non-interactive API.
     * If target is null, the CLI agent uses its default/active session.
     *
     * @param keys  keys to send
     * @param enter whether to press Enter after keys
     * @return a future that resolves with the raw API response
     */
    public CompletableFuture<ApiResponse> apiSendKeys(String keys, boolean enter) {
        return apiSendKeys(null, keys, enter);
    }

    /**
     * Send keys to a specific tmux session/pane via non-interactive API.
     *
     * @param target the tmux session/pane target (null for agent's default session)
     * @param keys   keys to send
     * @param enter  whether to press Enter after keys
     * @return a future that resolves with the raw API response
     */
    public CompletableFuture<ApiResponse> apiSendKeys(String target, String keys, boolean enter) {
        String requestId = UUID.randomUUID().toString();

        Map<String, String> payload = new HashMap<>();
        if (target != null) {
            payload.put("target", target);
        }
        payload.put("keys", keys);
        payload.put("enter", String.valueOf(enter));

        Map<String, String> meta = new HashMap<>();
        meta.put("requestId", requestId);
        try {
            meta.put("payload", objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }

        CompletableFuture<String> responseFuture = apiCorrelator.register(requestId);
        wsClient.send(new SendKeysApiMessage(meta));

        return responseFuture.thenApply(raw -> new ApiResponse(requestId, raw));
    }

    /**
     * List tmux sessions on the CLI agent's machine via non-interactive API.
     *
     * @return a future that resolves with the raw API response
     */
    public CompletableFuture<ApiResponse> apiListSessions() {
        String requestId = UUID.randomUUID().toString();

        Map<String, String> meta = new HashMap<>();
        meta.put("requestId", requestId);
        meta.put("payload", "{}");

        CompletableFuture<String> responseFuture = apiCorrelator.register(requestId);
        wsClient.send(new ListSessionsApiMessage(meta));

        return responseFuture.thenApply(raw -> new ApiResponse(requestId, raw));
    }

    /**
     * Check if a capability has been granted by the CLI agent.
     */
    public boolean hasCapability(Capability capability) {
        return grantedCapabilities.contains(capability);
    }

    /**
     * Get all granted capabilities.
     */
    public java.util.Set<Capability> getGrantedCapabilities() {
        return java.util.Set.copyOf(grantedCapabilities);
    }

    /**
     * Wait for capability negotiation to complete.
     * Returns the set of granted capabilities.
     * If no capabilities were requested, returns immediately with an empty set.
     */
    public CompletableFuture<java.util.Set<Capability>> awaitCapabilities() {
        if (requiredCapabilities.isEmpty()) {
            return CompletableFuture.completedFuture(java.util.Set.of());
        }
        return capabilityFuture;
    }

    /**
     * Subscribe to API response events.
     */
    public Disposable onApiResponse(Consumer<ApiResponseEvent> handler) {
        return eventBus.subscribe(ApiResponseEvent.class, handler);
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

        // Handle capability results from CLI agent
        eventBus.subscribe(CapabilityResultEvent.class, event -> {
            grantedCapabilities.clear();
            for (String cap : event.granted()) {
                try {
                    grantedCapabilities.add(Capability.fromValue(cap));
                } catch (IllegalArgumentException e) {
                    log.warn("Unknown granted capability: {}", cap);
                }
            }
            log.info("Capabilities granted: {}, denied: {}", event.granted(), event.denied());
            capabilityFuture.complete(java.util.Set.copyOf(grantedCapabilities));
        });
    }

    // ========== Cleanup ==========

    @Override
    public void close() {
        // Stop all streaming
        sessionScreenSubscriptions.keySet().forEach(this::stopStreaming);

        // Close components
        apiCorrelator.close();
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
        private Duration apiTimeout = Duration.ofSeconds(30);
        private Duration llmTimeout = Duration.ofMinutes(5);
        private final java.util.Set<Capability> requiredCapabilities = java.util.EnumSet.noneOf(Capability.class);

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

        public Builder apiTimeout(Duration timeout) {
            this.apiTimeout = timeout;
            return this;
        }

        public Builder llmTimeout(Duration timeout) {
            this.llmTimeout = timeout;
            return this;
        }

        /**
         * Declare capabilities this service requires from the CLI agent.
         * The user will be asked to grant these capabilities when connecting.
         */
        public Builder requiredCapabilities(Capability... capabilities) {
            java.util.Collections.addAll(this.requiredCapabilities, capabilities);
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
