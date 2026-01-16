package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * Edge-triggered D Flip-Flop (sim model).
 *
 * Inputs:
 *  - D on bundled channel 0
 *  - CLK on singleIn (rising edge latches D)
 *
 * Output:
 *  - singleOut = Q
 */
public final class DFlipFlopNode extends PositionedNode {

    private SignalValue lastClk = SignalValue.OFF;

    public DFlipFlopNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    @Override
    public void beginPass() {
        // stateful; do not clear
    }

    @Override
    public boolean evaluate() {
        boolean rise = (lastClk == SignalValue.OFF && singleIn == SignalValue.ON);
        lastClk = singleIn;

        if (!rise) return false;

        boolean d = bundledIn.isOn(0);
        SignalValue next = d ? SignalValue.ON : SignalValue.OFF;

        if (next != singleOut) {
            singleOut = next;
            return true;
        }
        return false;
    }
}
