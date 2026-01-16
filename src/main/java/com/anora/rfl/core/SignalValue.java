package com.anora.rfl.core;

/**
 * Represents a single-channel digital signal.
 * v1: boolean only (OFF/ON).
 * Later we can extend to analog without breaking everything.
 */
public enum SignalValue {
    OFF(false),
    ON(true);

    private final boolean asBoolean;

    SignalValue(boolean asBoolean) {
        this.asBoolean = asBoolean;
    }

    public boolean asBoolean() {
        return asBoolean;
    }

    public static SignalValue of(boolean value) {
        return value ? ON : OFF;
    }

    public SignalValue not() {
        return this == ON ? OFF : ON;
    }
}
