package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetworkNode;

public final class ConstantBundledSource implements NetworkNode {

    private final BundledSignal value;

    public ConstantBundledSource(BundledSignal value) {
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
        return SignalValue.OFF;
    }

    @Override
    public BundledSignal bundledOut() {
        return value;
    }
}
