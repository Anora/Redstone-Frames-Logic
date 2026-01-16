package com.anora.rfl.network;

import java.util.Objects;

/**
 * Undirected edge with directional permission flags.
 */
public final class Link {
    private final NetPos a;
    private final NetPos b;

    private boolean allowAToB = true;
    private boolean allowBToA = true;

    public Link(NetPos a, NetPos b) {
        this.a = Objects.requireNonNull(a);
        this.b = Objects.requireNonNull(b);
    }

    public NetPos a() { return a; }
    public NetPos b() { return b; }

    public boolean allows(NetPos from, NetPos to) {
        if (from.equals(a) && to.equals(b)) return allowAToB;
        if (from.equals(b) && to.equals(a)) return allowBToA;
        return false;
    }

    public Link setAToB(boolean v) { this.allowAToB = v; return this; }
    public Link setBToA(boolean v) { this.allowBToA = v; return this; }

    /**
     * Convenience used by NetworkGraph: block a single direction.
     * Example: block(from,to) makes from->to not allowed, but leaves the reverse untouched.
     */
    public void block(NetPos from, NetPos to) {
        if (from.equals(a) && to.equals(b)) {
            allowAToB = false;
        } else if (from.equals(b) && to.equals(a)) {
            allowBToA = false;
        }
    }
}



