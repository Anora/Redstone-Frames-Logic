package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetworkNode;

public final class SwitchableSingleSource implements NetworkNode {

    private SignalValue value;

    public SwitchableSingleSource(SignalValue initial) {
        this.value = initial;
    }

    public void set(SignalValue v) {
        this.value = v;
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

