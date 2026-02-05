package io.sessioncast.core.tmux;

/**
 * Special keys for tmux send-keys.
 */
public enum SpecialKey {
    ENTER("Enter"),
    ESCAPE("Escape"),
    TAB("Tab"),
    SPACE("Space"),
    BACKSPACE("BSpace"),
    DELETE("DC"),

    // Ctrl combinations
    CTRL_C("C-c"),
    CTRL_D("C-d"),
    CTRL_Z("C-z"),
    CTRL_L("C-l"),
    CTRL_A("C-a"),
    CTRL_E("C-e"),
    CTRL_K("C-k"),
    CTRL_U("C-u"),
    CTRL_W("C-w"),
    CTRL_R("C-r"),

    // Arrow keys
    UP("Up"),
    DOWN("Down"),
    LEFT("Left"),
    RIGHT("Right"),

    // Navigation
    HOME("Home"),
    END("End"),
    PAGE_UP("PPage"),
    PAGE_DOWN("NPage"),

    // Function keys
    F1("F1"),
    F2("F2"),
    F3("F3"),
    F4("F4"),
    F5("F5"),
    F6("F6"),
    F7("F7"),
    F8("F8"),
    F9("F9"),
    F10("F10"),
    F11("F11"),
    F12("F12");

    private final String tmuxKey;

    SpecialKey(String tmuxKey) {
        this.tmuxKey = tmuxKey;
    }

    public String getTmuxKey() {
        return tmuxKey;
    }

    /**
     * Parse a key string to SpecialKey (case-insensitive).
     */
    public static SpecialKey fromString(String key) {
        if (key == null) return null;

        String normalized = key.trim().toUpperCase().replace("-", "_");

        // Handle common aliases
        return switch (normalized) {
            case "ENTER", "RETURN", "CR" -> ENTER;
            case "ESC", "ESCAPE" -> ESCAPE;
            case "TAB" -> TAB;
            case "SPACE", " " -> SPACE;
            case "BACKSPACE", "BS", "BSPACE" -> BACKSPACE;
            case "DELETE", "DEL", "DC" -> DELETE;
            case "C_C", "CTRL_C", "CTRLC" -> CTRL_C;
            case "C_D", "CTRL_D", "CTRLD" -> CTRL_D;
            case "C_Z", "CTRL_Z", "CTRLZ" -> CTRL_Z;
            default -> {
                try {
                    yield SpecialKey.valueOf(normalized);
                } catch (IllegalArgumentException e) {
                    yield null;
                }
            }
        };
    }
}
