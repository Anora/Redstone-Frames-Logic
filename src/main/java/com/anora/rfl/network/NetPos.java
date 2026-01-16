package com.anora.rfl.network;

import java.util.Objects;

/**
 * 4D position: x,y,z plus dimension/world (w).
 * We intentionally provide BOTH dim() and w() because earlier code used both.
 */
public record NetPos(int x, int y, int z, int dim) {

    /** Alias for older code that used "w" as the 4th coordinate. */
    public int w() {
        return dim;
    }

    /** Alias for code that used "dim" as the 4th coordinate. */
    @Override
    public int dim() {
        return dim;
    }

    @Override
    public String toString() {
        return "NetPos{" + x + "," + y + "," + z + ",dim=" + dim + "}";
    }
}