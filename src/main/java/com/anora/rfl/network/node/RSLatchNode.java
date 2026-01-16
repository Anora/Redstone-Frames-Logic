package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * RS Latch (Set/Reset) driven by bundled inputs:
 * - S = channel 0 (rising edge sets Q=ON)
 * - R = channel 1 (rising edge resets Q=OFF)
 *
 * Outputs:
 * - singleOut = Q
 * - qBar() accessor = /Q
 *
 * If both S and R rise in the same evaluate pass: Reset wins.
 */
public final class RSLatchNode extends PositionedNode {

    private SignalValue q = SignalValue.OFF;
    private SignalValue qBar = SignalValue.ON;

    private boolean lastS = false;
    private boolean lastR = false;

    public RSLatchNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = q;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    public SignalValue q() {
        return q;
    }

    public SignalValue qBar() {
        return qBar;
    }

    @Override
    public void beginPass() {
        // stateful outputs; do not clear
    }

    @Override
    public boolean evaluate() {
        boolean s = bundledIn.isOn(0);
        boolean r = bundledIn.isOn(1);

        boolean sRise = (!lastS && s);
        boolean rRise = (!lastR && r);

        lastS = s;
        lastR = r;

        if (!sRise && !rRise) {
            return false;
        }

        // Apply priority: Reset wins if both rise simultaneously
        SignalValue nextQ = q;
        SignalValue nextQb = qBar;

        if (rRise) {
            nextQ = SignalValue.OFF;
            nextQb = SignalValue.ON;
        } else if (sRise) {
            nextQ = SignalValue.ON;
            nextQb = SignalValue.OFF;
        }

        if (nextQ != q) {
            q = nextQ;
            qBar = nextQb;
            singleOut = q; // drive output
            return true;
        }

        return false;
    }
}
