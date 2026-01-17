package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TwoInputNode;
import com.anora.rfl.network.util.DelayStore;

public final class NandGateNode extends PositionedNode implements TwoInputNode {

    private SignalValue inA = SignalValue.OFF;
    private SignalValue inB = SignalValue.OFF;

    public NandGateNode(NetPos pos, DelayStore store) {
        super(pos, store);
        // NAND is ON unless both inputs are ON
        this.singleOut = SignalValue.ON;
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
        boolean a = (inA == SignalValue.ON);
        boolean b = (inB == SignalValue.ON);

        SignalValue next = (a && b) ? SignalValue.OFF : SignalValue.ON;

        if (next != singleOut) {
            singleOut = next;
            return true;
        }
        return false;
    }
}

