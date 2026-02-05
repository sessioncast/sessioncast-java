package io.sessioncast.core.client;

import java.time.Duration;

/**
 * Configuration for relay WebSocket connection.
 */
public record RelayConfig(
    String url,
    String token,
    String machineId,
    String label,
    boolean reconnectEnabled,
    Duration reconnectInitialDelay,
    Duration reconnectMaxDelay,
    int maxReconnectAttempts,
    Duration circuitBreakerDuration
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String url = "wss://relay.sessioncast.io/ws";
        private String token;
        private String machineId;
        private String label;
        private boolean reconnectEnabled = true;
        private Duration reconnectInitialDelay = Duration.ofSeconds(2);
        private Duration reconnectMaxDelay = Duration.ofSeconds(60);
        private int maxReconnectAttempts = 5;
        private Duration circuitBreakerDuration = Duration.ofMinutes(2);

        public Builder url(String url) {
            this.url = url;
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

        public Builder reconnectEnabled(boolean enabled) {
            this.reconnectEnabled = enabled;
            return this;
        }

        public Builder reconnectInitialDelay(Duration delay) {
            this.reconnectInitialDelay = delay;
            return this;
        }

        public Builder reconnectMaxDelay(Duration delay) {
            this.reconnectMaxDelay = delay;
            return this;
        }

        public Builder maxReconnectAttempts(int attempts) {
            this.maxReconnectAttempts = attempts;
            return this;
        }

        public Builder circuitBreakerDuration(Duration duration) {
            this.circuitBreakerDuration = duration;
            return this;
        }

        public RelayConfig build() {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("Token is required");
            }
            if (machineId == null || machineId.isBlank()) {
                throw new IllegalArgumentException("MachineId is required");
            }

            return new RelayConfig(
                url, token, machineId, label != null ? label : machineId,
                reconnectEnabled, reconnectInitialDelay, reconnectMaxDelay,
                maxReconnectAttempts, circuitBreakerDuration
            );
        }
    }
}
