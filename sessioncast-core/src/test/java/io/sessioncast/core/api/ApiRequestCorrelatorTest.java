package io.sessioncast.core.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class ApiRequestCorrelatorTest {

    private ApiRequestCorrelator correlator;

    @BeforeEach
    void setUp() {
        correlator = new ApiRequestCorrelator(Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        correlator.close();
    }

    @Test
    @DisplayName("register 후 complete 시 Future가 정상 해소되어야 함")
    void testRegisterAndComplete() throws Exception {
        // Given
        CompletableFuture<String> future = correlator.register("req-1");
        assertEquals(1, correlator.pendingCount());

        // When
        boolean completed = correlator.complete("req-1", "{\"exitCode\": 0}");

        // Then
        assertTrue(completed);
        assertEquals("{\"exitCode\": 0}", future.get());
        assertEquals(0, correlator.pendingCount());
    }

    @Test
    @DisplayName("존재하지 않는 requestId로 complete 시 false 반환")
    void testCompleteUnknownRequest() {
        boolean result = correlator.complete("non-existent", "payload");
        assertFalse(result);
    }

    @Test
    @DisplayName("completeExceptionally 시 Future가 예외로 실패해야 함")
    void testCompleteExceptionally() {
        // Given
        CompletableFuture<String> future = correlator.register("req-2");

        // When
        boolean completed = correlator.completeExceptionally("req-2", "Agent not connected");

        // Then
        assertTrue(completed);
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertTrue(ex.getCause().getMessage().contains("Agent not connected"));
        assertEquals(0, correlator.pendingCount());
    }

    @Test
    @DisplayName("타임아웃 시 Future가 TimeoutException으로 실패해야 함")
    void testTimeout() {
        // Given - 매우 짧은 타임아웃
        CompletableFuture<String> future = correlator.register("req-timeout", Duration.ofMillis(100));

        // When & Then
        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(TimeoutException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("req-timeout"));
    }

    @Test
    @DisplayName("타임아웃 전에 complete 하면 정상 해소되어야 함")
    void testCompleteBeforeTimeout() throws Exception {
        // Given
        CompletableFuture<String> future = correlator.register("req-fast", Duration.ofSeconds(10));

        // When - 즉시 완료
        correlator.complete("req-fast", "result");

        // Then
        assertEquals("result", future.get());
    }

    @Test
    @DisplayName("여러 요청을 동시에 추적할 수 있어야 함")
    void testMultipleRequests() throws Exception {
        // Given
        CompletableFuture<String> f1 = correlator.register("req-a");
        CompletableFuture<String> f2 = correlator.register("req-b");
        CompletableFuture<String> f3 = correlator.register("req-c");
        assertEquals(3, correlator.pendingCount());

        // When - 순서 무관하게 완료
        correlator.complete("req-b", "result-b");
        correlator.complete("req-a", "result-a");
        correlator.complete("req-c", "result-c");

        // Then
        assertEquals("result-a", f1.get());
        assertEquals("result-b", f2.get());
        assertEquals("result-c", f3.get());
        assertEquals(0, correlator.pendingCount());
    }

    @Test
    @DisplayName("close 시 모든 pending 요청이 취소되어야 함")
    void testCloseCancelsPending() {
        // Given
        CompletableFuture<String> f1 = correlator.register("req-x");
        CompletableFuture<String> f2 = correlator.register("req-y");

        // When
        correlator.close();

        // Then
        assertTrue(f1.isCompletedExceptionally());
        assertTrue(f2.isCompletedExceptionally());
    }

    @Test
    @DisplayName("같은 requestId로 두 번 complete 시 두 번째는 false 반환")
    void testDoubleComplete() {
        // Given
        correlator.register("req-dup");

        // When
        boolean first = correlator.complete("req-dup", "first");
        boolean second = correlator.complete("req-dup", "second");

        // Then
        assertTrue(first);
        assertFalse(second);
    }

    @Test
    @DisplayName("빈 payload로도 정상 complete 되어야 함")
    void testEmptyPayload() throws Exception {
        CompletableFuture<String> future = correlator.register("req-empty");
        correlator.complete("req-empty", "");
        assertEquals("", future.get());
    }
}
