package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * Buffer gate (non-inverting):
 * - Output = input
 * - Single-signal only for now
 */
public final class BufferGateNode extends PositionedNode {

    public BufferGateNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    @Override
    public void beginPass() {
        // no-op
    }

    @Override
    public boolean evaluate() {
        if (singleOut != singleIn) {
            singleOut = singleIn;
            return true;
        }
        return false;
    }
}
