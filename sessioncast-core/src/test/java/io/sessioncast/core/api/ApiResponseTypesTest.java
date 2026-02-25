package io.sessioncast.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTypesTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // ========== ExecResult ==========

    @Test
    @DisplayName("ExecResult 파싱 - 성공 케이스")
    void testExecResultSuccess() throws Exception {
        String json = """
            {"exitCode": 0, "stdout": "hello\\n", "stderr": "", "duration": 42}
            """;
        ExecResult result = mapper.readValue(json, ExecResult.class);

        assertEquals(0, result.exitCode());
        assertEquals("hello\n", result.stdout());
        assertEquals("", result.stderr());
        assertEquals(42, result.duration());
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("ExecResult 파싱 - 실패 케이스")
    void testExecResultFailure() throws Exception {
        String json = """
            {"exitCode": 1, "stdout": "", "stderr": "command not found", "duration": 5}
            """;
        ExecResult result = mapper.readValue(json, ExecResult.class);

        assertEquals(1, result.exitCode());
        assertEquals("command not found", result.stderr());
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("ExecResult - 알 수 없는 필드 무시")
    void testExecResultIgnoresUnknown() throws Exception {
        String json = """
            {"exitCode": 0, "stdout": "ok", "stderr": "", "duration": 1, "extraField": "ignored"}
            """;
        ExecResult result = mapper.readValue(json, ExecResult.class);
        assertEquals(0, result.exitCode());
    }

    // ========== LlmChatResponse ==========

    @Test
    @DisplayName("LlmChatResponse 파싱 - 정상 응답")
    void testLlmChatResponse() throws Exception {
        String json = """
            {
              "id": "chatcmpl-123",
              "model": "gpt-4",
              "choices": [
                {
                  "index": 0,
                  "message": {"role": "assistant", "content": "Hello!"},
                  "finish_reason": "stop"
                }
              ],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
            }
            """;
        LlmChatResponse response = mapper.readValue(json, LlmChatResponse.class);

        assertEquals("chatcmpl-123", response.id());
        assertEquals("gpt-4", response.model());
        assertEquals("Hello!", response.content());
        assertFalse(response.hasError());
        assertNotNull(response.usage());
        assertEquals(15, response.usage().totalTokens());
    }

    @Test
    @DisplayName("LlmChatResponse 파싱 - 에러 응답")
    void testLlmChatResponseError() throws Exception {
        String json = """
            {
              "id": null,
              "model": null,
              "choices": null,
              "usage": null,
              "error": {"message": "Model not found", "code": "model_not_found"}
            }
            """;
        LlmChatResponse response = mapper.readValue(json, LlmChatResponse.class);

        assertTrue(response.hasError());
        assertEquals("Model not found", response.error().message());
        assertNull(response.content());
    }

    @Test
    @DisplayName("LlmChatResponse - choices가 비어있을 때 content()는 null")
    void testLlmChatResponseEmptyChoices() throws Exception {
        String json = """
            {"id": "test", "model": "test", "choices": [], "usage": null}
            """;
        LlmChatResponse response = mapper.readValue(json, LlmChatResponse.class);

        assertNull(response.content());
        assertFalse(response.hasError());
    }

    // ========== LlmChatRequest ==========

    @Test
    @DisplayName("LlmChatRequest Builder 테스트")
    void testLlmChatRequestBuilder() throws Exception {
        LlmChatRequest request = LlmChatRequest.builder()
            .model("gpt-4")
            .system("You are helpful.")
            .user("Hello")
            .temperature(0.7)
            .maxTokens(100)
            .build();

        assertEquals("gpt-4", request.model());
        assertEquals(2, request.messages().size());
        assertEquals("system", request.messages().get(0).role());
        assertEquals("You are helpful.", request.messages().get(0).content());
        assertEquals("user", request.messages().get(1).role());
        assertEquals("Hello", request.messages().get(1).content());
        assertEquals(0.7, request.temperature());
        assertEquals(100, request.maxTokens());
        assertFalse(request.stream());

        // 직렬화 확인
        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("gpt-4"));
        assertTrue(json.contains("You are helpful."));
    }

    @Test
    @DisplayName("LlmChatRequest Builder - 최소 구성")
    void testLlmChatRequestMinimal() {
        LlmChatRequest request = LlmChatRequest.builder()
            .user("What is 1+1?")
            .build();

        assertNull(request.model());
        assertEquals(1, request.messages().size());
        assertNull(request.temperature());
        assertNull(request.maxTokens());
    }

    // ========== ApiResponse ==========

    @Test
    @DisplayName("ApiResponse.as() 타입 변환")
    void testApiResponseTypedParsing() {
        String payload = "{\"exitCode\": 0, \"stdout\": \"ok\", \"stderr\": \"\", \"duration\": 10}";
        ApiResponse response = new ApiResponse("req-1", payload);

        ExecResult result = response.as(ExecResult.class);
        assertEquals(0, result.exitCode());
        assertEquals("ok", result.stdout());
    }

    @Test
    @DisplayName("ApiResponse.as() 잘못된 타입 변환 시 예외")
    void testApiResponseInvalidTypeParsing() {
        ApiResponse response = new ApiResponse("req-2", "not valid json");
        assertThrows(RuntimeException.class, () -> response.as(ExecResult.class));
    }
}
