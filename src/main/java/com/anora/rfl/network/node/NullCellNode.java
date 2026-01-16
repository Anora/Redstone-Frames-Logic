package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower 2 Null Cell (sim model).
 *
 * Two crossing lines with NO interaction:
 *  - bottom line (channel 0) passes through unchanged
 *  - top line (channel 1) passes through unchanged
 *
 * Output is bundledOut with channels 0 and 1 set accordingly.
 */
public final class NullCellNode extends PositionedNode {

    public NullCellNode(NetPos pos, DelayStore store) {
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
        boolean bottom = bundledIn.isOn(0);
        boolean top = bundledIn.isOn(1);

        BundledSignal next = BundledSignal.ALL_OFF
                .with(0, bottom)
                .with(1, top);

        if (!next.equals(bundledOut)) {
            bundledOut = next;
            return true;
        }
        return false;
    }
}