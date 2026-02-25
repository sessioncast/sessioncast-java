package io.sessioncast.core.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CapabilityTest {

    @Test
    @DisplayName("Capability enum value() 반환")
    void testCapabilityValues() {
        assertEquals("exec", Capability.EXEC.value());
        assertEquals("exec_cwd", Capability.EXEC_CWD.value());
        assertEquals("llm_chat", Capability.LLM_CHAT.value());
        assertEquals("send_keys", Capability.SEND_KEYS.value());
        assertEquals("list_sessions", Capability.LIST_SESSIONS.value());
    }

    @Test
    @DisplayName("fromValue()로 문자열에서 Capability 변환")
    void testFromValue() {
        assertEquals(Capability.EXEC, Capability.fromValue("exec"));
        assertEquals(Capability.EXEC_CWD, Capability.fromValue("exec_cwd"));
        assertEquals(Capability.LLM_CHAT, Capability.fromValue("llm_chat"));
        assertEquals(Capability.SEND_KEYS, Capability.fromValue("send_keys"));
        assertEquals(Capability.LIST_SESSIONS, Capability.fromValue("list_sessions"));
    }

    @Test
    @DisplayName("fromValue()에 잘못된 값이면 IllegalArgumentException")
    void testFromValueInvalid() {
        assertThrows(IllegalArgumentException.class, () -> Capability.fromValue("invalid"));
        assertThrows(IllegalArgumentException.class, () -> Capability.fromValue(""));
    }

    @Test
    @DisplayName("Capability를 콤마 구분 문자열로 변환")
    void testCapabilitiesToString() {
        String capStr = Arrays.stream(new Capability[]{Capability.EXEC, Capability.LLM_CHAT})
                .map(Capability::value)
                .collect(Collectors.joining(","));

        assertEquals("exec,llm_chat", capStr);
    }

    @Test
    @DisplayName("콤마 구분 문자열에서 Capability 집합으로 변환")
    void testStringToCapabilities() {
        String capStr = "exec,exec_cwd,llm_chat";
        var caps = Arrays.stream(capStr.split(","))
                .map(String::trim)
                .map(Capability::fromValue)
                .collect(Collectors.toSet());

        assertEquals(3, caps.size());
        assertTrue(caps.contains(Capability.EXEC));
        assertTrue(caps.contains(Capability.EXEC_CWD));
        assertTrue(caps.contains(Capability.LLM_CHAT));
    }
}
