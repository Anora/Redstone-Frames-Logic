package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TwoInputNode;
import com.anora.rfl.network.util.DelayStore;

public final class OrGateNode extends PositionedNode implements TwoInputNode {

    private SignalValue inA = SignalValue.OFF;
    private SignalValue inB = SignalValue.OFF;

    public OrGateNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF; // unused for non-bundled gates
    }

    @Override
    public void setInputs(SignalValue a, SignalValue b) {
        this.inA = (a == null) ? SignalValue.OFF : a;
        this.inB = (b == null) ? SignalValue.OFF : b;
    }

    @Override
    public void beginPass() {}

    @Override
    public boolean evaluate() {
        SignalValue next = (inA == SignalValue.ON || inB == SignalValue.ON)
                ? SignalValue.ON
                : SignalValue.OFF;

        if (next != singleOut) {
            singleOut = next;
            return true;
        }
        return false;
    }
}
