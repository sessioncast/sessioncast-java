package io.sessioncast.core.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ExecOptionsTest {

    @Test
    @DisplayName("defaults()는 모든 값이 null")
    void testDefaults() {
        ExecOptions opts = ExecOptions.defaults();
        assertNull(opts.timeout());
        assertNull(opts.sessionId());
    }

    @Test
    @DisplayName("withTimeout()으로 timeout만 설정")
    void testWithTimeout() {
        ExecOptions opts = ExecOptions.withTimeout(Duration.ofMinutes(2));
        assertEquals(Duration.ofMinutes(2), opts.timeout());
        assertNull(opts.sessionId());
    }

    @Test
    @DisplayName("Builder로 모든 옵션 설정")
    void testBuilder() {
        ExecOptions opts = ExecOptions.builder()
            .timeout(Duration.ofSeconds(60))
            .sessionId("my-session")
            .build();

        assertEquals(Duration.ofSeconds(60), opts.timeout());
        assertEquals("my-session", opts.sessionId());
    }

    @Test
    @DisplayName("Builder로 sessionId만 설정")
    void testBuilderSessionIdOnly() {
        ExecOptions opts = ExecOptions.builder()
            .sessionId("dev")
            .build();

        assertNull(opts.timeout());
        assertEquals("dev", opts.sessionId());
    }

    @Test
    @DisplayName("Builder 빈 상태로 build → defaults와 동일")
    void testBuilderEmpty() {
        ExecOptions opts = ExecOptions.builder().build();
        assertNull(opts.timeout());
        assertNull(opts.sessionId());
    }
}
