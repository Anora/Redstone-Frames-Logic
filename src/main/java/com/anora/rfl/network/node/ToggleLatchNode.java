package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower-style Toggle Latch (T flip-flop).
 *
 * Input: singleIn (BACK)
 * Outputs:
 *   singleOut == ON  -> LEFT output ON
 *   singleOut == OFF -> RIGHT output ON
 *
 * Toggles on RISING edge only (OFF->ON):
 * - lever OFF->ON toggles
 * - lever ON->OFF does nothing
 */
public final class ToggleLatchNode extends PositionedNode {

    private boolean lastIn = false;

    public ToggleLatchNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.ON; // default LEFT ON
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    public void setLeftOn(boolean leftOn) {
        this.singleOut = leftOn ? SignalValue.ON : SignalValue.OFF;
    }

    private void toggle() {
        this.singleOut = (this.singleOut == SignalValue.ON) ? SignalValue.OFF : SignalValue.ON;
    }

    @Override
    public void beginPass() {}

    @Override
    public boolean evaluate() {
        boolean now = (singleIn == SignalValue.ON);

        // Rising edge only
        boolean rising = now && !lastIn;
        lastIn = now;

        if (rising) {
            toggle();
            return true;
        }

        return false;
    }
}
