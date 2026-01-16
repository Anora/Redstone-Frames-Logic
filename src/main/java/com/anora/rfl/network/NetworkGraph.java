package com.anora.rfl.network;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.util.DelayStore;

import java.util.*;

/**
 * Logic-network simulator (NOT multiplayer networking).
 * Nodes exchange signals via Links. Some nodes tick over time (TickableNode).
 *
 * External world redstone is injected via setExternalSingleIn(), then OR'd into singleIn.
 */
public final class NetworkGraph {

    private final Map<NetPos, NetworkNode> nodes = new HashMap<>();
    private final Map<Long, Link> links = new HashMap<>();

    // Debug snapshots of computed inputs per node (after merge)
    private final Map<NetPos, SignalValue> lastSingleIn = new HashMap<>();
    private final Map<NetPos, BundledSignal> lastBundledIn = new HashMap<>();

    // External inputs injected from Minecraft world (weak/direct/best neighbor etc.)
    private final Map<NetPos, SignalValue> externalSingleIn = new HashMap<>();

    private DelayStore delayStore;

    public NetworkGraph() {}

    public void setDelayStore(DelayStore store) {
        this.delayStore = store;
    }

    public DelayStore getDelayStore() {
        return delayStore;
    }

    // ---------------------------------------------------------------------
    // Nodes
    // ---------------------------------------------------------------------

    public void putNode(NetPos pos, NetworkNode node) {
        nodes.put(pos, node);
    }

    public NetworkNode getNode(NetPos pos) {
        return nodes.get(pos);
    }

    public NetworkNode removeNode(NetPos pos) {
        NetworkNode removed = nodes.remove(pos);
        if (removed == null) return null;

        externalSingleIn.remove(pos);
        lastSingleIn.remove(pos);
        lastBundledIn.remove(pos);

        // Remove all links touching this node
        Iterator<Map.Entry<Long, Link>> it = links.entrySet().iterator();
        while (it.hasNext()) {
            Link l = it.next().getValue();
            if (l.a.equals(pos) || l.b.equals(pos)) it.remove();
        }

        return removed;
    }

    public Collection<NetworkNode> nodesValues() {
        return nodes.values();
    }

    // ---------------------------------------------------------------------
    // External world input
    // ---------------------------------------------------------------------

    /** Injects a world single input (OR'd into the merged singleIn for this node). */
    public void setExternalSingleIn(NetPos pos, SignalValue value) {
        if (value == null || value == SignalValue.OFF) externalSingleIn.remove(pos);
        else externalSingleIn.put(pos, value);
    }

    public SignalValue getExternalSingleIn(NetPos pos) {
        return externalSingleIn.getOrDefault(pos, SignalValue.OFF);
    }

    // ---------------------------------------------------------------------
    // Links
    // ---------------------------------------------------------------------

    public void connect(NetPos a, NetPos b) {
        if (a == null || b == null) return;
        if (a.equals(b)) return;
        if (!nodes.containsKey(a) || !nodes.containsKey(b)) return;

        links.putIfAbsent(Link.key(a, b), new Link(a, b));
    }

    public void disconnect(NetPos a, NetPos b) {
        if (a == null || b == null) return;
        if (a.equals(b)) return;
        links.remove(Link.key(a, b));
    }

    public boolean isConnected(NetPos a, NetPos b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return false;
        return links.containsKey(Link.key(a, b));
    }

    public void setBlocked(NetPos from, NetPos to, boolean blocked) {
        Link l = links.get(Link.key(from, to));
        if (l != null) l.setBlocked(from, to, blocked);
    }

    public boolean isBlocked(NetPos from, NetPos to) {
        Link l = links.get(Link.key(from, to));
        return l != null && !l.allows(from, to);
    }

    // ---------------------------------------------------------------------
    // Simulation
    // ---------------------------------------------------------------------

    /** Ticks time-based nodes (repeaters, timers, etc.). */
    public void tick() {
        for (NetworkNode n : nodes.values()) {
            if (n instanceof TickableNode t) {
                t.tick();
            }
        }
    }

    public record SettleResult(boolean stable, boolean anyChange, int passes) {}

    /**
     * Runs combinational settling passes until stable or maxPasses reached.
     * - Pass:
     *   1) beginPass() on all nodes
     *   2) compute merged inputs (from links + external)
     *   3) setInputs(...)
     *   4) evaluate() on all nodes
     */
    public SettleResult settleUntilStable(int maxPasses) {
        if (maxPasses < 1) throw new IllegalArgumentException("maxPasses must be >= 1");

        boolean anyChange = false;

        for (int pass = 0; pass < maxPasses; pass++) {
            for (NetworkNode n : nodes.values()) n.beginPass();

            // 1) Merge inputs for each node
            for (Map.Entry<NetPos, NetworkNode> e : nodes.entrySet()) {
                NetPos pos = e.getKey();
                NetworkNode node = e.getValue();

                SignalValue mergedSingle = SignalValue.OFF;
                BundledSignal mergedBundled = BundledSignal.ALL_OFF;

                // OR from neighbors via links (directional)
                for (Link l : links.values()) {
                    NetPos nb = null;
                    if (l.a.equals(pos)) nb = l.b;
                    else if (l.b.equals(pos)) nb = l.a;
                    if (nb == null) continue;

                    NetworkNode other = nodes.get(nb);
                    if (other == null) continue;

                    // only allow nb -> pos if not blocked that way
                    if (!l.allows(nb, pos)) continue;

                    if (!other.drivesNetwork()) continue;

                    if (other.singleOut() == SignalValue.ON) mergedSingle = SignalValue.ON;
                    mergedBundled = mergedBundled.or(other.bundledOut());
                }

                // OR in external world input
                if (getExternalSingleIn(pos) == SignalValue.ON) {
                    mergedSingle = SignalValue.ON;
                }

                lastSingleIn.put(pos, mergedSingle);
                lastBundledIn.put(pos, mergedBundled);

                node.setInputs(mergedSingle, mergedBundled);
            }

            // 2) Evaluate nodes
            boolean passChanged = false;
            for (NetworkNode node : nodes.values()) {
                if (node.evaluate()) passChanged = true;
            }

            if (!passChanged) {
                return new SettleResult(true, anyChange, pass + 1);
            }

            anyChange = true;
        }

        return new SettleResult(false, true, maxPasses);
    }

    /** Convenience: returns whether anything changed during settling. */
    public boolean settle(int maxPasses) {
        return settleUntilStable(maxPasses).anyChange();
    }

    // ---------------------------------------------------------------------
    // Debug access
    // ---------------------------------------------------------------------

    public SignalValue lastSingleIn(NetPos pos) {
        return lastSingleIn.getOrDefault(pos, SignalValue.OFF);
    }

    public BundledSignal lastBundledIn(NetPos pos) {
        return lastBundledIn.getOrDefault(pos, BundledSignal.ALL_OFF);
    }

    // ---------------------------------------------------------------------
    // Internal Link class
    // ---------------------------------------------------------------------

    private static final class Link {
        final NetPos a;
        final NetPos b;

        // directional block flags
        private boolean aToBBlocked;
        private boolean bToABlocked;

        Link(NetPos a, NetPos b) {
            this.a = a;
            this.b = b;
        }

        static long key(NetPos a, NetPos b) {
            // order-independent key
            long ha = a.hashCode();
            long hb = b.hashCode();
            long lo = Math.min(ha, hb);
            long hi = Math.max(ha, hb);
            return (lo * 31L) ^ hi;
        }

        boolean allows(NetPos from, NetPos to) {
            if (from.equals(a) && to.equals(b)) return !aToBBlocked;
            if (from.equals(b) && to.equals(a)) return !bToABlocked;
            return false;
        }

        void setBlocked(NetPos from, NetPos to, boolean blocked) {
            if (from.equals(a) && to.equals(b)) aToBBlocked = blocked;
            if (from.equals(b) && to.equals(a)) bToABlocked = blocked;
        }
    }
}
