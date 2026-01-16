package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetworkNode;

/**
 * Simple diode/buffer: outputs its input, but you enforce "no backflow"
 * by blocking the reverse link direction in the graph.
 */
public final class DiodeNode implements NetworkNode {

    private SignalValue inS = SignalValue.OFF;
    private SignalValue outS = SignalValue.OFF;

    @Override
    public void setInputs(SignalValue singleIn, BundledSignal bundledIn) {
        this.inS = singleIn;
    }

    @Override
    public boolean evaluate() {
        boolean changed = outS != inS;
        outS = inS;
        return changed;
    }

    @Override
    public SignalValue singleOut() {
        return outS;
    }

    @Override
    public BundledSignal bundledOut() {
        return BundledSignal.ALL_OFF;
    }
}

