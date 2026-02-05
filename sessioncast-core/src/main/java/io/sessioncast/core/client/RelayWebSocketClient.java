package io.sessioncast.core.client;

import io.sessioncast.core.event.Event;
import io.sessioncast.core.event.Event.*;
import io.sessioncast.core.event.EventBus;
import io.sessioncast.core.protocol.Message;
import io.sessioncast.core.protocol.Message.*;
import io.sessioncast.core.protocol.MessageCodec;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket client for connecting to SessionCast relay server.
 */
public class RelayWebSocketClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RelayWebSocketClient.class);

    private final RelayConfig config;
    private final EventBus eventBus;
    private final MessageCodec codec;
    private final ScheduledExecutorService scheduler;

    private volatile WebSocketClient wsClient;
    private volatile boolean connected = false;
    private volatile boolean closing = false;

    // Reconnection state
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private volatile long circuitBreakerUntil = 0;
    private ScheduledFuture<?> reconnectFuture;

    private CompletableFuture<Void> connectFuture;

    public RelayWebSocketClient(RelayConfig config, EventBus eventBus) {
        this.config = config;
        this.eventBus = eventBus;
        this.codec = new MessageCodec();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "relay-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    // ========== Connection Management ==========

    /**
     * Connect to the relay server.
     */
    public CompletableFuture<Void> connect() {
        if (connected || closing) {
            return CompletableFuture.completedFuture(null);
        }

        // Check circuit breaker
        if (System.currentTimeMillis() < circuitBreakerUntil) {
            long waitSeconds = (circuitBreakerUntil - System.currentTimeMillis()) / 1000;
            return CompletableFuture.failedFuture(
                new SessionCastException("CIRCUIT_BREAKER", "Circuit breaker active, wait " + waitSeconds + "s")
            );
        }

        connectFuture = new CompletableFuture<>();

        try {
            URI uri = new URI(config.url());
            wsClient = createWebSocketClient(uri);
            wsClient.connect();
        } catch (Exception e) {
            connectFuture.completeExceptionally(e);
        }

        return connectFuture;
    }

    /**
     * Disconnect from the relay server.
     */
    public void disconnect() {
        closing = true;
        cancelReconnect();

        if (wsClient != null) {
            try {
                wsClient.closeBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        connected = false;
    }

    /**
     * Check if connected.
     */
    public boolean isConnected() {
        return connected && wsClient != null && wsClient.isOpen();
    }

    // ========== Message Sending ==========

    /**
     * Send a message to the relay.
     */
    public void send(Message message) {
        if (!isConnected()) {
            log.warn("Cannot send message, not connected");
            return;
        }

        try {
            String json = codec.encode(message);
            wsClient.send(json);
        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage());
        }
    }

    // ========== Reconnection Logic ==========

    private void scheduleReconnect() {
        if (!config.reconnectEnabled() || closing) {
            return;
        }

        int attempts = reconnectAttempts.incrementAndGet();

        if (attempts > config.maxReconnectAttempts()) {
            // Activate circuit breaker
            circuitBreakerUntil = System.currentTimeMillis() + config.circuitBreakerDuration().toMillis();
            reconnectAttempts.set(0);
            log.warn("Max reconnect attempts reached, circuit breaker active for {}s",
                config.circuitBreakerDuration().toSeconds());
            return;
        }

        // Calculate delay with exponential backoff + jitter
        long baseDelay = config.reconnectInitialDelay().toMillis();
        long maxDelay = config.reconnectMaxDelay().toMillis();
        long delay = Math.min(baseDelay * (1L << (attempts - 1)), maxDelay);
        delay += ThreadLocalRandom.current().nextLong(delay / 4); // Add jitter

        log.info("Scheduling reconnect attempt {} in {}ms", attempts, delay);

        reconnectFuture = scheduler.schedule(() -> {
            if (!closing) {
                connect();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelReconnect() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }

    private void resetReconnectAttempts() {
        reconnectAttempts.set(0);
        circuitBreakerUntil = 0;
    }

    // ========== WebSocket Client Creation ==========

    private WebSocketClient createWebSocketClient(URI uri) {
        return new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                log.info("WebSocket connected to {}", uri);
                connected = true;
                resetReconnectAttempts();

                // Send registration message
                RelayWebSocketClient.this.send(new RegisterMessage(config.machineId(), config.label(), config.token()));

                eventBus.publish(new ConnectedEvent(config.machineId(), Instant.now()));

                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.complete(null);
                }
            }

            @Override
            public void onMessage(String json) {
                handleMessage(json);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                log.info("WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
                connected = false;

                DisconnectReason disconnectReason = remote ? DisconnectReason.CONNECTION_LOST : DisconnectReason.NORMAL;
                eventBus.publish(new DisconnectedEvent(disconnectReason, reason, Instant.now()));

                if (!closing && remote) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onError(Exception ex) {
                log.error("WebSocket error: {}", ex.getMessage());
                eventBus.publish(new ErrorEvent(
                    new SessionCastException("WS_ERROR", ex.getMessage(), ex),
                    Instant.now()
                ));

                if (connectFuture != null && !connectFuture.isDone()) {
                    connectFuture.completeExceptionally(ex);
                }
            }
        };
    }

    // ========== Message Handling ==========

    private void handleMessage(String json) {
        try {
            Message message = codec.decode(json);

            if (message instanceof KeysMessage keys) {
                eventBus.publish(new KeysReceivedEvent(
                    keys.sessionName(), keys.keys(), keys.shouldEnter(), Instant.now()
                ));
            } else if (message instanceof ResizeMessage resize) {
                eventBus.publish(new ResizeRequestEvent(
                    resize.sessionName(), resize.cols(), resize.rows(), Instant.now()
                ));
            } else if (message instanceof CreateSessionMessage create) {
                eventBus.publish(new SessionCreatedEvent(create.sessionName(), Instant.now()));
            } else if (message instanceof KillSessionMessage kill) {
                eventBus.publish(new SessionKilledEvent(kill.sessionName(), Instant.now()));
            } else if (message instanceof ErrorMessage error) {
                eventBus.publish(new ErrorEvent(
                    new SessionCastException(error.code(), error.message()),
                    Instant.now()
                ));
            } else if (message instanceof PingMessage) {
                send(new PongMessage());
            } else {
                log.debug("Unhandled message type: {}", message.type());
            }
        } catch (Exception e) {
            log.error("Failed to handle message: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        disconnect();
        scheduler.shutdown();
    }
}
