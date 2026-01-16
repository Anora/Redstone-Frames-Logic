package com.anora.rfl.network.node.util;

public final class RplDelays {
    private RplDelays() {}

    // RedPower delay settings (in redstone ticks)
    public static final int[] RP_TICKS = { 1, 2, 3, 4, 8, 16, 32, 64, 128 };

    // Convert to server ticks (20/sec). 1 redstone tick = 2 server ticks.
    public static int toServerTicksFromIndex(int idx) {
        if (idx < 0) idx = 0;
        if (idx >= RP_TICKS.length) idx = RP_TICKS.length - 1;
        return RP_TICKS[idx] * 2;
    }

    public static int nextIndex(int idx) {
        return (idx + 1) % RP_TICKS.length;
    }

    public static int prevIndex(int idx) {
        return (idx + RP_TICKS.length - 1) % RP_TICKS.length;
    }
}
