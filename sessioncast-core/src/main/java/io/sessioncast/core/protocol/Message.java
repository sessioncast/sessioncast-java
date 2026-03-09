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

    // API Request Messages (SDK → Relay → CLI)
    @JsonSubTypes.Type(value = Message.ExecMessage.class, name = "exec"),
    @JsonSubTypes.Type(value = Message.LlmChatMessage.class, name = "llm_chat"),
    @JsonSubTypes.Type(value = Message.SendKeysApiMessage.class, name = "send_keys"),
    @JsonSubTypes.Type(value = Message.ListSessionsApiMessage.class, name = "list_sessions"),

    // API Response Message (CLI → Relay → SDK)
    @JsonSubTypes.Type(value = Message.ApiResponseMessage.class, name = "api_response"),

    // Capability Handshake
    @JsonSubTypes.Type(value = Message.CapabilityRequestMessage.class, name = "capability_request"),
    @JsonSubTypes.Type(value = Message.CapabilityResultMessage.class, name = "capability_result"),
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
    Message.PongMessage,
    Message.ExecMessage,
    Message.LlmChatMessage,
    Message.SendKeysApiMessage,
    Message.ListSessionsApiMessage,
    Message.ApiResponseMessage,
    Message.CapabilityRequestMessage,
    Message.CapabilityResultMessage {

    String type();

    // ========== Outgoing Messages (Agent → Relay) ==========

    record RegisterMessage(
        @JsonProperty("session") String session,
        @JsonProperty("role") String role,
        @JsonProperty("meta") java.util.Map<String, String> meta,
        @JsonProperty("requiredCapabilities") String requiredCapabilities
    ) implements Message {
        public RegisterMessage(String machineId, String label, String token) {
            this(machineId, "host", buildMeta(machineId, label, token), null);
        }

        public RegisterMessage(String machineId, String label, String token, String role) {
            this(machineId, role, buildMeta(machineId, label, token), null);
        }

        public RegisterMessage(String machineId, String label, String token, String role, String requiredCapabilities) {
            this(machineId, role, buildMeta(machineId, label, token), requiredCapabilities);
        }

        private static java.util.Map<String, String> buildMeta(String machineId, String label, String token) {
            var map = new java.util.LinkedHashMap<String, String>();
            if (label != null) map.put("label", label);
            if (machineId != null) map.put("machineId", machineId);
            if (token != null) map.put("token", token);
            return map;
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

    // ========== API Request Messages (SDK → Relay → CLI) ==========

    record ExecMessage(
        @JsonProperty("meta") java.util.Map<String, String> meta
    ) implements Message {
        @Override
        public String type() { return "exec"; }
    }

    record LlmChatMessage(
        @JsonProperty("meta") java.util.Map<String, String> meta
    ) implements Message {
        @Override
        public String type() { return "llm_chat"; }
    }

    record SendKeysApiMessage(
        @JsonProperty("meta") java.util.Map<String, String> meta
    ) implements Message {
        @Override
        public String type() { return "send_keys"; }
    }

    record ListSessionsApiMessage(
        @JsonProperty("meta") java.util.Map<String, String> meta
    ) implements Message {
        @Override
        public String type() { return "list_sessions"; }
    }

    // ========== API Response Message (CLI → Relay → SDK) ==========

    record ApiResponseMessage(
        @JsonProperty("meta") java.util.Map<String, String> meta
    ) implements Message {
        @Override
        public String type() { return "api_response"; }
    }

    // ========== Capability Handshake Messages ==========

    /**
     * Sent from Relay to CLI agent when an SDK connects with required capabilities.
     * meta: { "from": "service-name", "capabilities": "exec,exec_cwd,llm_chat" }
     */
    record CapabilityRequestMessage(
        @JsonProperty("meta") java.util.Map<String, String> meta
    ) implements Message {
        @Override
        public String type() { return "capability_request"; }
    }

    /**
     * Sent from Relay to SDK with the grant/deny result.
     * meta: { "granted": "exec,llm_chat", "denied": "exec_cwd" }
     */
    record CapabilityResultMessage(
        @JsonProperty("meta") java.util.Map<String, String> meta
    ) implements Message {
        @Override
        public String type() { return "capability_result"; }
    }
}
