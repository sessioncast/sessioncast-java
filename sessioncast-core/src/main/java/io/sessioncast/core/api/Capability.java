package io.sessioncast.core.api;

/**
 * Capabilities that can be requested by the SDK and granted by the CLI agent.
 *
 * <p>The user controls which capabilities are allowed via their CLI agent config:
 * <pre>
 * # ~/.sessioncast.yml
 * capabilities:
 *   exec: true          # auto-grant
 *   exec_cwd: ask       # prompt user on connect
 *   llm_chat: true      # auto-grant
 *   send_keys: false    # auto-deny
 *   list_sessions: true  # auto-grant
 * </pre>
 */
public enum Capability {
    /** Execute shell commands on the agent's machine. */
    EXEC("exec"),

    /** Execute shell commands with a custom working directory. */
    EXEC_CWD("exec_cwd"),

    /** Send LLM chat requests via the agent's local AI. */
    LLM_CHAT("llm_chat"),

    /** Send keys to tmux sessions. */
    SEND_KEYS("send_keys"),

    /** List tmux sessions on the agent's machine. */
    LIST_SESSIONS("list_sessions");

    private final String value;

    Capability(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Capability fromValue(String value) {
        for (Capability c : values()) {
            if (c.value.equals(value)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Unknown capability: " + value);
    }
}
