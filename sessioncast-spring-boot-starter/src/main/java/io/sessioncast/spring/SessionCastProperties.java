package io.sessioncast.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for SessionCast.
 */
@ConfigurationProperties(prefix = "sessioncast")
public class SessionCastProperties {

    private final Relay relay = new Relay();
    private final Agent agent = new Agent();
    private final Reconnect reconnect = new Reconnect();

    public Relay getRelay() {
        return relay;
    }

    public Agent getAgent() {
        return agent;
    }

    public Reconnect getReconnect() {
        return reconnect;
    }

    public static class Relay {
        /**
         * WebSocket URL of the relay server.
         */
        private String url = "wss://relay.sessioncast.io/ws";

        /**
         * Authentication token for the relay.
         */
        private String token;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }

    public static class Agent {
        /**
         * Unique identifier for this machine/agent.
         */
        private String machineId;

        /**
         * Human-readable label for this agent.
         */
        private String label;

        /**
         * Whether to automatically connect on startup.
         */
        private boolean autoConnect = true;

        /**
         * Whether to automatically stream new sessions.
         */
        private boolean autoStreamOnCreate = true;

        public String getMachineId() {
            return machineId;
        }

        public void setMachineId(String machineId) {
            this.machineId = machineId;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public boolean isAutoConnect() {
            return autoConnect;
        }

        public void setAutoConnect(boolean autoConnect) {
            this.autoConnect = autoConnect;
        }

        public boolean isAutoStreamOnCreate() {
            return autoStreamOnCreate;
        }

        public void setAutoStreamOnCreate(boolean autoStreamOnCreate) {
            this.autoStreamOnCreate = autoStreamOnCreate;
        }
    }

    public static class Reconnect {
        /**
         * Whether to enable automatic reconnection.
         */
        private boolean enabled = true;

        /**
         * Initial delay before first reconnect attempt.
         */
        private Duration initialDelay = Duration.ofSeconds(2);

        /**
         * Maximum delay between reconnect attempts.
         */
        private Duration maxDelay = Duration.ofSeconds(60);

        /**
         * Maximum number of reconnect attempts before circuit breaker.
         */
        private int maxAttempts = 5;

        /**
         * Duration to wait when circuit breaker is active.
         */
        private Duration circuitBreakerDuration = Duration.ofMinutes(2);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public Duration getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(Duration maxDelay) {
            this.maxDelay = maxDelay;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getCircuitBreakerDuration() {
            return circuitBreakerDuration;
        }

        public void setCircuitBreakerDuration(Duration circuitBreakerDuration) {
            this.circuitBreakerDuration = circuitBreakerDuration;
        }
    }
}
