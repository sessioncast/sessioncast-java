package io.sessioncast.core.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base interface for WebSocket messages.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true)
@JsonSubTypes({
    // Outgoing (Agent → Relay)
    @JsonSubTypes.Type(value = Message.RegisterMessage.class, name = "register"),
    @JsonSubTypes.Type(value = Message.ScreenMessage.class, name = "screen"),
    @JsonSubTypes.Type(value = Message.ScreenGzMessage.class, name = "screenGz"),
    @JsonSubTypes.Type(value = Message.SessionsMessage.class, name = "sessions"),
    @JsonSubTypes.Type(value = Message.FileViewMessage.class, name = "file_view"),
    @JsonSubTypes.Type(value = Message.UploadCompleteMessage.class, name = "uploadComplete"),
    @JsonSubTypes.Type(value = Message.UploadErrorMessage.class, name = "uploadError"),

    // Incoming (Relay → Agent)
    @JsonSubTypes.Type(value = Message.KeysMessage.class, name = "keys"),
    @JsonSubTypes.Type(value = Message.ResizeMessage.class, name = "resize"),
    @JsonSubTypes.Type(value = Message.CreateSessionMessage.class, name = "createSession"),
    @JsonSubTypes.Type(value = Message.KillSessionMessage.class, name = "killSession"),
    @JsonSubTypes.Type(value = Message.RequestFileViewMessage.class, name = "requestFileView"),
    @JsonSubTypes.Type(value = Message.UploadFileMessage.class, name = "uploadFile"),
    @JsonSubTypes.Type(value = Message.ErrorMessage.class, name = "error"),
    @JsonSubTypes.Type(value = Message.PingMessage.class, name = "ping"),
    @JsonSubTypes.Type(value = Message.PongMessage.class, name = "pong"),
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface Message permits
    Message.RegisterMessage,
    Message.ScreenMessage,
    Message.ScreenGzMessage,
    Message.SessionsMessage,
    Message.FileViewMessage,
    Message.UploadCompleteMessage,
    Message.UploadErrorMessage,
    Message.KeysMessage,
    Message.ResizeMessage,
    Message.CreateSessionMessage,
    Message.KillSessionMessage,
    Message.RequestFileViewMessage,
    Message.UploadFileMessage,
    Message.ErrorMessage,
    Message.PingMessage,
    Message.PongMessage {

    String type();

    // ========== Outgoing Messages (Agent → Relay) ==========

    record RegisterMessage(
        @JsonProperty("machineId") String machineId,
        @JsonProperty("label") String label,
        @JsonProperty("token") String token,
        @JsonProperty("role") String role
    ) implements Message {
        public RegisterMessage(String machineId, String label, String token) {
            this(machineId, label, token, "host");
        }

        @Override
        public String type() { return "register"; }
    }

    record ScreenMessage(
        @JsonProperty("sessionName") String sessionName,
        @JsonProperty("screen") String screen  // base64 encoded
    ) implements Message {
        @Override
        public String type() { return "screen"; }
    }

    record ScreenGzMessage(
        @JsonProperty("sessionName") String sessionName,
        @JsonProperty("screen") String screen  // base64 encoded gzip
    ) implements Message {
        @Override
        public String type() { return "screenGz"; }
    }

    record SessionsMessage(
        @JsonProperty("sessions") java.util.List<SessionInfo> sessions
    ) implements Message {
        @Override
        public String type() { return "sessions"; }

        public record SessionInfo(
            @JsonProperty("name") String name,
            @JsonProperty("windows") int windows,
            @JsonProperty("attached") boolean attached
        ) {}
    }

    record FileViewMessage(
        @JsonProperty("filename") String filename,
        @JsonProperty("content") String content,  // base64 encoded
        @JsonProperty("contentType") String contentType,
        @JsonProperty("path") String path
    ) implements Message {
        @Override
        public String type() { return "file_view"; }
    }

    record UploadCompleteMessage(
        @JsonProperty("filename") String filename,
        @JsonProperty("path") String path,
        @JsonProperty("size") long size
    ) implements Message {
        @Override
        public String type() { return "uploadComplete"; }
    }

    record UploadErrorMessage(
        @JsonProperty("filename") String filename,
        @JsonProperty("error") String error
    ) implements Message {
        @Override
        public String type() { return "uploadError"; }
    }

    // ========== Incoming Messages (Relay → Agent) ==========

    record KeysMessage(
        @JsonProperty("sessionName") String sessionName,
        @JsonProperty("keys") String keys,
        @JsonProperty("enter") Boolean enter
    ) implements Message {
        public boolean shouldEnter() {
            return enter != null && enter;
        }

        @Override
        public String type() { return "keys"; }
    }

    record ResizeMessage(
        @JsonProperty("sessionName") String sessionName,
        @JsonProperty("cols") int cols,
        @JsonProperty("rows") int rows
    ) implements Message {
        @Override
        public String type() { return "resize"; }
    }

    record CreateSessionMessage(
        @JsonProperty("sessionName") String sessionName,
        @JsonProperty("workDir") String workDir
    ) implements Message {
        @Override
        public String type() { return "createSession"; }
    }

    record KillSessionMessage(
        @JsonProperty("sessionName") String sessionName
    ) implements Message {
        @Override
        public String type() { return "killSession"; }
    }

    record RequestFileViewMessage(
        @JsonProperty("sessionName") String sessionName,
        @JsonProperty("path") String path
    ) implements Message {
        @Override
        public String type() { return "requestFileView"; }
    }

    record UploadFileMessage(
        @JsonProperty("sessionName") String sessionName,
        @JsonProperty("filename") String filename,
        @JsonProperty("content") String content,  // base64 chunk
        @JsonProperty("chunkIndex") int chunkIndex,
        @JsonProperty("totalChunks") int totalChunks
    ) implements Message {
        @Override
        public String type() { return "uploadFile"; }
    }

    record ErrorMessage(
        @JsonProperty("code") String code,
        @JsonProperty("message") String message
    ) implements Message {
        @Override
        public String type() { return "error"; }
    }

    record PingMessage() implements Message {
        @Override
        public String type() { return "ping"; }
    }

    record PongMessage() implements Message {
        @Override
        public String type() { return "pong"; }
    }
}
