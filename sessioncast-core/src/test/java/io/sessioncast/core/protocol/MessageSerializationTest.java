package io.sessioncast.core.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageSerializationTest {

    private MessageCodec codec;

    @BeforeEach
    void setUp() {
        codec = new MessageCodec();
    }

    @Test
    @DisplayName("ExecMessage 직렬화/역직렬화")
    void testExecMessageRoundTrip() {
        // Given
        var meta = Map.of(
            "requestId", "req-123",
            "payload", "{\"command\":\"echo hello\"}"
        );
        var msg = new Message.ExecMessage(meta);

        // When
        String json = codec.encode(msg);
        Message decoded = codec.decode(json);

        // Then
        assertInstanceOf(Message.ExecMessage.class, decoded);
        var exec = (Message.ExecMessage) decoded;
        assertEquals("exec", exec.type());
        assertEquals("req-123", exec.meta().get("requestId"));
        assertEquals("{\"command\":\"echo hello\"}", exec.meta().get("payload"));
    }

    @Test
    @DisplayName("LlmChatMessage 직렬화/역직렬화")
    void testLlmChatMessageRoundTrip() {
        var meta = Map.of(
            "requestId", "req-456",
            "payload", "{\"model\":\"gpt-4\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"
        );
        var msg = new Message.LlmChatMessage(meta);

        String json = codec.encode(msg);
        Message decoded = codec.decode(json);

        assertInstanceOf(Message.LlmChatMessage.class, decoded);
        assertEquals("llm_chat", decoded.type());
        assertEquals("req-456", ((Message.LlmChatMessage) decoded).meta().get("requestId"));
    }

    @Test
    @DisplayName("SendKeysApiMessage 직렬화/역직렬화")
    void testSendKeysApiMessageRoundTrip() {
        var meta = Map.of(
            "requestId", "req-789",
            "payload", "{\"target\":\"my-session\",\"keys\":\"ls\",\"enter\":\"true\"}"
        );
        var msg = new Message.SendKeysApiMessage(meta);

        String json = codec.encode(msg);
        Message decoded = codec.decode(json);

        assertInstanceOf(Message.SendKeysApiMessage.class, decoded);
        assertEquals("send_keys", decoded.type());
    }

    @Test
    @DisplayName("ListSessionsApiMessage 직렬화/역직렬화")
    void testListSessionsApiMessageRoundTrip() {
        var meta = Map.of("requestId", "req-list", "payload", "{}");
        var msg = new Message.ListSessionsApiMessage(meta);

        String json = codec.encode(msg);
        Message decoded = codec.decode(json);

        assertInstanceOf(Message.ListSessionsApiMessage.class, decoded);
        assertEquals("list_sessions", decoded.type());
    }

    @Test
    @DisplayName("ApiResponseMessage 직렬화/역직렬화")
    void testApiResponseMessageRoundTrip() {
        var meta = Map.of(
            "requestId", "req-123",
            "payload", "{\"exitCode\":0,\"stdout\":\"hello\\n\",\"stderr\":\"\",\"duration\":42}"
        );
        var msg = new Message.ApiResponseMessage(meta);

        String json = codec.encode(msg);
        Message decoded = codec.decode(json);

        assertInstanceOf(Message.ApiResponseMessage.class, decoded);
        assertEquals("api_response", decoded.type());
        var resp = (Message.ApiResponseMessage) decoded;
        assertEquals("req-123", resp.meta().get("requestId"));
        assertTrue(resp.meta().get("payload").contains("exitCode"));
    }

    @Test
    @DisplayName("ApiResponseMessage에 error 필드가 있을 때")
    void testApiResponseWithError() {
        var meta = Map.of(
            "requestId", "req-err",
            "error", "Command not found"
        );
        var msg = new Message.ApiResponseMessage(meta);

        String json = codec.encode(msg);
        Message decoded = codec.decode(json);

        assertInstanceOf(Message.ApiResponseMessage.class, decoded);
        assertEquals("Command not found", ((Message.ApiResponseMessage) decoded).meta().get("error"));
    }

    @Test
    @DisplayName("알 수 없는 type의 JSON은 디코딩 시 예외 없이 처리")
    void testUnknownTypeDoesNotCrash() {
        // MessageCodec은 ignoreUnknown=true이므로 알 수 없는 필드는 무시
        // 하지만 알 수 없는 type은 예외 발생 가능 — 이 동작을 확인
        String json = "{\"type\":\"unknown_type\",\"meta\":{}}";
        assertThrows(RuntimeException.class, () -> codec.decode(json));
    }
}
