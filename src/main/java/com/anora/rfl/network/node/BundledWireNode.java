package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * Bundled-only wire/buffer:
 * - bundledOut mirrors bundledIn
 * - singleOut is ALWAYS OFF (never drives single-line)
 *
 * Use this to route bundled signals through the graph without
 * accidentally zeroing them (SingleWireNode would zero bundled).
 */
public final class BundledWireNode extends PositionedNode {

    public BundledWireNode(NetPos pos, DelayStore store) {
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
        if (!bundledOut.equals(bundledIn)) {
            bundledOut = bundledIn;
            return true;
        }
        return false;
    }
}
