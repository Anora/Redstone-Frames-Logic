package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * State Cell (simple memory cell) driven by bundled inputs:
 * - SET   = channel 0 (rising edge sets state ON)
 * - RESET = channel 1 (rising edge sets state OFF)
 *
 * Output:
 * - singleOut = stored state
 *
 * Priority: RESET wins if both edges happen in the same pass.
 */
public final class StateCellNode extends PositionedNode {

    private SignalValue state = SignalValue.OFF;

    private boolean lastSet = false;
    private boolean lastReset = false;

    public StateCellNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = state;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    public SignalValue state() {
        return state;
    }

    @Override
    public void beginPass() {
        // stateful; do not clear
    }

    @Override
    public boolean evaluate() {
        boolean set = bundledIn.isOn(0);
        boolean reset = bundledIn.isOn(1);

        boolean setRise = (!lastSet && set);
        boolean resetRise = (!lastReset && reset);

        lastSet = set;
        lastReset = reset;

        if (!setRise && !resetRise) {
            return false;
        }

        SignalValue next = state;

        if (resetRise) next = SignalValue.OFF;
        else if (setRise) next = SignalValue.ON;

        if (next != state) {
            state = next;
            singleOut = state;
            return true;
        }

        return false;
    }
}
