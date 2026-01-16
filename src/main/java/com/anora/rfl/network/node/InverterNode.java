package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * Simple NOT gate:
 * - Output = NOT(input)
 * - Single-signal only for now
 */
public final class InverterNode extends PositionedNode {

    public InverterNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.ON;          // NOT(OFF) = ON initial
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    @Override
    public void beginPass() {
        // stateful output is fine; no clearing required
    }

    @Override
    public boolean evaluate() {
        SignalValue next = (singleIn == SignalValue.ON) ? SignalValue.OFF : SignalValue.ON;
        if (next != singleOut) {
            singleOut = next;
            return true;
        }
        return false;
    }
}
