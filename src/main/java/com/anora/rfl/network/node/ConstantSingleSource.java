package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetworkNode;

public final class ConstantSingleSource implements NetworkNode {

    private final SignalValue value;

    public ConstantSingleSource(SignalValue value) {
        this.value = value;
    }

    @Override
    public void setInputs(SignalValue singleIn, BundledSignal bundledIn) {
        // ignore
    }

    @Override
    public boolean evaluate() {
        return false;
    }

    @Override
    public SignalValue singleOut() {
        return value;
    }

    @Override
    public BundledSignal bundledOut() {
        return BundledSignal.ALL_OFF;
    }
}
