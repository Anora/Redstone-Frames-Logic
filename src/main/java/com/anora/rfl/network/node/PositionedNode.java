package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.util.DelayStore;

import java.util.Objects;

/**
 * Convenience base for nodes that need a position and a delay store.
 */
public abstract class PositionedNode implements NetworkNode {
    protected final NetPos pos;
    protected final DelayStore store;

    protected SignalValue singleIn = SignalValue.OFF;
    protected BundledSignal bundledIn = BundledSignal.ALL_OFF;

    protected SignalValue singleOut = SignalValue.OFF;
    protected BundledSignal bundledOut = BundledSignal.ALL_OFF;

    protected PositionedNode(NetPos pos, DelayStore store) {
        this.pos = Objects.requireNonNull(pos);
        this.store = store; // can be null
    }

    public NetPos pos() { return pos; }

    @Override
    public void setInputs(SignalValue singleIn, BundledSignal bundledIn) {
        this.singleIn = singleIn;
        this.bundledIn = bundledIn;
    }

    @Override
    public SignalValue singleOut() {
        return singleOut;
    }

    @Override
    public BundledSignal bundledOut() {
        return bundledOut;
    }
}

