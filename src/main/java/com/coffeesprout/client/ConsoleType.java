package com.coffeesprout.client;

public enum ConsoleType {
    VNC("vnc"),
    SPICE("spice"),
    TERMINAL("terminal");

    private final String value;

    ConsoleType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ConsoleType fromValue(String value) {
        for (ConsoleType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown console type: " + value);
    }
}
