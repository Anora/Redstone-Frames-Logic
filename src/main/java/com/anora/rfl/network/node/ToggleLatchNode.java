package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower-style Toggle Latch (T flip-flop):
 * - On each rising edge pulse at input, toggles internal state.
 *
 * In RedPower it has two output faces (Q and /Q).
 * In the sim for now:
 *   - singleOut = Q
 *   - bundledOut unused (ALL_OFF)
 *   - provide qBar() accessor for /Q so we can wire it later when we support multi-output directions.
 */
public final class ToggleLatchNode extends PositionedNode {

    private SignalValue q = SignalValue.OFF;
    private SignalValue qBar = SignalValue.ON;

    private SignalValue lastSeenIn = SignalValue.OFF;

    public ToggleLatchNode(NetPos pos, DelayStore store) {
        super(pos, store);
        // initialize outputs
        this.singleOut = q;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    /** Q output (same as singleOut). */
    public SignalValue q() {
        return q;
    }

    /** /Q output (not currently wired into the graph). */
    public SignalValue qBar() {
        return qBar;
    }

    @Override
    public void beginPass() {
        // stateful, do not clear outputs
    }

    @Override
    public boolean evaluate() {
        // Rising edge detect: OFF -> ON
        if (lastSeenIn == SignalValue.OFF && singleIn == SignalValue.ON) {
            // Toggle
            q = (q == SignalValue.ON) ? SignalValue.OFF : SignalValue.ON;
            qBar = (qBar == SignalValue.ON) ? SignalValue.OFF : SignalValue.ON;

            // Drive singleOut from Q
            singleOut = q;

            lastSeenIn = singleIn;
            return true;
        }

        lastSeenIn = singleIn;
        return false;
    }
}
