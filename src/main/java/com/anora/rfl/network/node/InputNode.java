package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * Simple controllable input source (like a lever).
 */
public final class InputNode extends PositionedNode {

    public InputNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    /** Manually drive the input value. */
    public void setValue(SignalValue value) {
        this.singleOut = value;
    }

    @Override
    public void beginPass() {
        // nothing to clear; this node is externally driven
    }

    @Override
    public boolean evaluate() {
        // value only changes when setValue() is called
        return false;
    }
}
