package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * Debug probe for bundled signals.
 * Logs when bundled input mask changes.
 */
public final class BusProbeNode extends PositionedNode {

    private int lastMask = BundledSignal.ALL_OFF.mask();

    public BusProbeNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    @Override
    public void beginPass() {
        // no output
    }

    @Override
    public boolean evaluate() {
        int m = bundledIn.mask();
        if (m != lastMask) {
            lastMask = m;
            System.out.println("[BusProbe@" + pos + "] bundled -> " + bundledIn);
            return true;
        }
        return false;
    }
}

