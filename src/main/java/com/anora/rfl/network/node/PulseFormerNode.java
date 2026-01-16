package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TickableNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * Pulse Former (edge-to-pulse):
 * - On rising edge of singleIn (OFF -> ON), emits a 1-tick ON pulse on singleOut.
 * - Does not re-trigger while input stays ON.
 */
public final class PulseFormerNode extends PositionedNode implements TickableNode {

    private SignalValue lastIn = SignalValue.OFF;
    private int pulseCountdown = 0;

    public PulseFormerNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    @Override
    public void beginPass() {
        // stateful output; do not clear
    }

    @Override
    public boolean evaluate() {
        boolean rise = (lastIn == SignalValue.OFF && singleIn == SignalValue.ON);
        lastIn = singleIn;

        if (rise && pulseCountdown == 0) {
            singleOut = SignalValue.ON;
            pulseCountdown = 1; // 1 tick pulse
            return true;
        }

        return false;
    }

    @Override
    public void tick() {
        if (pulseCountdown > 0) {
            pulseCountdown--;
            if (pulseCountdown == 0) {
                singleOut = SignalValue.OFF;
            }
        }
    }
}

