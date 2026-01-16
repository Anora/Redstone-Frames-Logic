package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * Simple pulse-driven counter.
 *
 * Clock input:
 *  - singleIn rising edge increments the counter.
 *
 * Reset input:
 *  - bundled channel 0 = RESET (level). If ON, counter is forced to 0.
 *
 * Output:
 *  - bundledOut channels 0..3 hold the 4-bit count (LSB=ch0, MSB=ch3)
 *  - singleOut is OFF (not used)
 */
public final class CounterNode extends PositionedNode {

    private SignalValue lastClock = SignalValue.OFF;
    private int value = 0; // 0..15

    public CounterNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = encode4(value);
    }

    public int value() {
        return value;
    }

    @Override
    public void beginPass() {
        // stateful; do not clear
    }

    @Override
    public boolean evaluate() {
        // Level reset
        if (bundledIn.isOn(0)) {
            if (value != 0) {
                value = 0;
                bundledOut = encode4(value);
                // keep lastClock tracking stable
                lastClock = singleIn;
                return true;
            }
            lastClock = singleIn;
            return false;
        }

        boolean rise = (lastClock == SignalValue.OFF && singleIn == SignalValue.ON);
        lastClock = singleIn;

        if (!rise) return false;

        value = (value + 1) & 0xF; // mod 16
        bundledOut = encode4(value);
        return true;
    }

    private static BundledSignal encode4(int v) {
        BundledSignal out = BundledSignal.ALL_OFF;
        out = out.with(0, (v & 0b0001) != 0);
        out = out.with(1, (v & 0b0010) != 0);
        out = out.with(2, (v & 0b0100) != 0);
        out = out.with(3, (v & 0b1000) != 0);
        return out;
    }
}
