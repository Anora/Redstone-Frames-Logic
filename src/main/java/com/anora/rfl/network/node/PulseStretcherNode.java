package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TickableNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * Pulse Stretcher (monostable):
 * - On rising edge of singleIn, outputs ON for widthTicks, then returns OFF.
 *
 * This is extremely useful for "pulse shaping" in RedPower-style logic.
 */
public final class PulseStretcherNode extends PositionedNode implements TickableNode {

    private final int widthTicks;

    private SignalValue lastIn = SignalValue.OFF;
    private int countdown = 0;

    public PulseStretcherNode(NetPos pos, DelayStore store, int widthTicks) {
        super(pos, store);
        if (widthTicks < 1) throw new IllegalArgumentException("widthTicks must be >= 1");
        this.widthTicks = widthTicks;
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    public int widthTicks() {
        return widthTicks;
    }

    @Override
    public void beginPass() {
        // stateful; do not clear
    }

    @Override
    public boolean evaluate() {
        boolean rise = (lastIn == SignalValue.OFF && singleIn == SignalValue.ON);
        lastIn = singleIn;

        if (rise) {
            countdown = widthTicks;
            if (singleOut != SignalValue.ON) {
                singleOut = SignalValue.ON;
                return true;
            }
            return false;
        }

        return false;
    }

    @Override
    public void tick() {
        if (countdown > 0) {
            countdown--;
            if (countdown == 0) {
                singleOut = SignalValue.OFF;
            }
        }
    }
}
