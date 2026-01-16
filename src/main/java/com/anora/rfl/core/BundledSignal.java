package com.anora.rfl.core;

import java.util.Objects;

/**
 * 16-channel bundled signal (like a 16-bit bus).
 *
 * v1: each channel is a boolean (OFF/ON).
 * Internally stored as a 16-bit bitmask for performance and easy merging.
 */
public final class BundledSignal {
    /** Bit i = 1 means channel i is ON. Only lower 16 bits are used. */
    private final int mask;

    public static final BundledSignal ALL_OFF = new BundledSignal(0);
    public static final BundledSignal ALL_ON  = new BundledSignal(0xFFFF);

    public BundledSignal(int mask) {
        this.mask = mask & 0xFFFF;
    }

    /** Raw 16-bit mask (lower 16 bits only). */
    public int mask() {
        return mask;
    }

    /** True if channel [0..15] is ON. */
    public boolean isOn(int channel) {
        checkChannel(channel);
        return ((mask >>> channel) & 1) != 0;
    }

    /** True if channel [0..15] is OFF. */
    public boolean isOff(int channel) {
        return !isOn(channel);
    }

    /**
     * Returns a new BundledSignal with the given channel set to ON/OFF.
     * This is immutable: it does not modify the original.
     */
    public BundledSignal with(int channel, boolean on) {
        checkChannel(channel);
        int bit = (1 << channel);
        int next = on ? (mask | bit) : (mask & ~bit);
        return (next == mask) ? this : new BundledSignal(next);
    }

    /**
     * Returns a new BundledSignal with channel set based on SignalValue.
     */
    public BundledSignal with(int channel, SignalValue value) {
        return with(channel, value == SignalValue.ON);
    }

    /** Bitwise OR merge (channel ON if either is ON). */
    public BundledSignal or(BundledSignal other) {
        Objects.requireNonNull(other, "other");
        int next = (this.mask | other.mask) & 0xFFFF;
        return (next == this.mask) ? this : new BundledSignal(next);
    }

    /** Bitwise AND merge (channel ON only if both are ON). */
    public BundledSignal and(BundledSignal other) {
        Objects.requireNonNull(other, "other");
        int next = (this.mask & other.mask) & 0xFFFF;
        return (next == this.mask) ? this : new BundledSignal(next);
    }

    /** Bitwise NOT (flip all 16 channels). */
    public BundledSignal not() {
        int next = (~this.mask) & 0xFFFF;
        return (next == this.mask) ? this : new BundledSignal(next);
    }

    private static void checkChannel(int channel) {
        if (channel < 0 || channel > 15) {
            throw new IllegalArgumentException("BundledSignal channel must be 0..15, got " + channel);
        }
    }

    @Override
    public boolean equals(Object o) {
        return (this == o) || (o instanceof BundledSignal other && this.mask == other.mask);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(mask);
    }

    @Override
    public String toString() {
        return "BundledSignal{mask=0x" + Integer.toHexString(mask).toUpperCase() + "}";
    }
}
