package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * Transparent Latch (D-latch behavior):
 *
 * Bundled inputs:
 *  - D (data)  = channel 0
 *  - LOCK/EN   = channel 1
 *
 * Logic:
 *  - While LOCK is ON, Q follows D (transparent).
 *  - When LOCK turns OFF, Q holds last value.
 *
 * Output:
 *  - singleOut = Q
 *  - qBar() accessor = /Q (for later directional wiring)
 */
public final class TransparentLatchNode extends PositionedNode {

    private SignalValue q = SignalValue.OFF;
    private SignalValue qBar = SignalValue.ON;

    public TransparentLatchNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = q;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    public SignalValue q() { return q; }
    public SignalValue qBar() { return qBar; }

    @Override
    public void beginPass() {
        // stateful; do not clear
    }

    @Override
    public boolean evaluate() {
        boolean d = bundledIn.isOn(0);
        boolean lock = bundledIn.isOn(1);

        if (!lock) {
            // holding state
            return false;
        }

        SignalValue nextQ = d ? SignalValue.ON : SignalValue.OFF;
        if (nextQ != q) {
            q = nextQ;
            qBar = (q == SignalValue.ON) ? SignalValue.OFF : SignalValue.ON;
            singleOut = q;
            return true;
        }
        return false;
    }
}
