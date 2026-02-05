package io.sessioncast.core.event;

import io.sessioncast.core.screen.ScreenData;

import java.time.Instant;

/**
 * Base interface for all SessionCast events.
 */
public sealed interface Event permits
        Event.ConnectedEvent,
        Event.DisconnectedEvent,
        Event.ScreenEvent,
        Event.KeysReceivedEvent,
        Event.SessionCreatedEvent,
        Event.SessionKilledEvent,
        Event.ResizeRequestEvent,
        Event.ErrorEvent {

    Instant timestamp();

    record ConnectedEvent(String machineId, Instant timestamp) implements Event {}

    record DisconnectedEvent(DisconnectReason reason, String message, Instant timestamp) implements Event {}

    record ScreenEvent(String sessionName, ScreenData data, Instant timestamp) implements Event {}

    record KeysReceivedEvent(String sessionName, String keys, boolean enter, Instant timestamp) implements Event {}

    record SessionCreatedEvent(String sessionName, Instant timestamp) implements Event {}

    record SessionKilledEvent(String sessionName, Instant timestamp) implements Event {}

    record ResizeRequestEvent(String sessionName, int cols, int rows, Instant timestamp) implements Event {}

    record ErrorEvent(SessionCastException exception, Instant timestamp) implements Event {}

    enum DisconnectReason {
        NORMAL,
        CONNECTION_LOST,
        AUTH_FAILED,
        CIRCUIT_BREAKER,
        SERVER_ERROR
    }

    class SessionCastException extends RuntimeException {
        private final String code;

        public SessionCastException(String code, String message) {
            super(message);
            this.code = code;
        }

        public SessionCastException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
