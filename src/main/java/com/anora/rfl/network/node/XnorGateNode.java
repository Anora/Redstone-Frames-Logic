package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * 2-input XNOR gate using bundled channels:
 * - A = channel 0
 * - B = channel 1
 *
 * Output is ON when inputs are equal.
 */
public final class XnorGateNode extends PositionedNode {

    public XnorGateNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.ON; // XNOR(false,false) = true
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    @Override
    public void beginPass() {
        // no-op
    }

    @Override
    public boolean evaluate() {
        boolean a = bundledIn.isOn(0);
        boolean b = bundledIn.isOn(1);

        SignalValue next =
                (a == b) ? SignalValue.ON : SignalValue.OFF;

        if (next != singleOut) {
            singleOut = next;
            return true;
        }
        return false;
    }
}
