package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetworkNode;

/**
 * Simple wire: output mirrors input.
 */
public final class WireNode implements NetworkNode {

    private SignalValue inS = SignalValue.OFF;
    private BundledSignal inB = BundledSignal.ALL_OFF;

    private SignalValue outS = SignalValue.OFF;
    private BundledSignal outB = BundledSignal.ALL_OFF;

    @Override
    public void beginPass() {
        // Prevent “floating latch” behavior
        outS = SignalValue.OFF;
        outB = BundledSignal.ALL_OFF;
    }

    @Override
    public void setInputs(SignalValue singleIn, BundledSignal bundledIn) {
        this.inS = singleIn;
        this.inB = bundledIn;
    }

    @Override
    public boolean evaluate() {
        boolean changed = (outS != inS) || !outB.equals(inB);
        outS = inS;
        outB = inB;
        return changed;
    }

    @Override
    public SignalValue singleOut() {
        return outS;
    }

    @Override
    public BundledSignal bundledOut() {
        return outB;
    }
}


