package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * Single-only wire/buffer:
 * - singleOut mirrors singleIn
 * - bundledOut is ALWAYS ALL_OFF (never drives bus)
 *
 * Use this for distributing clocks or other single-line signals without
 * accidentally contaminating bundled channels.
 */
public final class SingleWireNode extends PositionedNode {

    public SingleWireNode(NetPos pos, DelayStore store) {
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
        // bundledOut stays ALL_OFF forever
        if (singleOut != singleIn) {
            singleOut = singleIn;
            return true;
        }
        return false;
    }
}
