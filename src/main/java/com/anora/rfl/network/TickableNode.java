package com.anora.rfl.network;

/**
 * Nodes with time-based behavior (repeaters).
 * NetworkGraph.tick() will call tick() on these.
 */
public interface TickableNode {
    void tick();
}
