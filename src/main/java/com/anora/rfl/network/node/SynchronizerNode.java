package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TickableNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower-style Synchronizer:
 * - Watches two asynchronous pulse inputs.
 * - Outputs a single pulse as soon as BOTH inputs have been seen high at least once.
 * - After outputting, it resets its internal "seen" flags.
 *
 * Bundled channel mapping:
 *  - A input = channel 0
 *  - B input = channel 1
 *  - Reset  = channel 2 (level reset: if ON, clears immediately)
 *
 * Output:
 *  - singleOut pulses ON for 1 tick
 */
public final class SynchronizerNode extends PositionedNode implements TickableNode {

    private boolean seenA = false;
    private boolean seenB = false;

    private boolean lastA = false;
    private boolean lastB = false;

    private int pulseCountdown = 0;

    public SynchronizerNode(NetPos pos, DelayStore store) {
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
        boolean reset = bundledIn.isOn(2);
        if (reset) {
            boolean changed = (seenA || seenB || singleOut != SignalValue.OFF || pulseCountdown != 0);
            seenA = false;
            seenB = false;
            pulseCountdown = 0;
            singleOut = SignalValue.OFF;

            // Track current levels to avoid false edges after reset
            lastA = bundledIn.isOn(0);
            lastB = bundledIn.isOn(1);
            return changed;
        }

        boolean a = bundledIn.isOn(0);
        boolean b = bundledIn.isOn(1);

        boolean aRise = (!lastA && a);
        boolean bRise = (!lastB && b);

        lastA = a;
        lastB = b;

        boolean changed = false;

        if (aRise && !seenA) {
            seenA = true;
            changed = true;
        }
        if (bRise && !seenB) {
            seenB = true;
            changed = true;
        }

        // Fire exactly once when both have been seen
        if (pulseCountdown == 0 && seenA && seenB) {
            singleOut = SignalValue.ON;
            pulseCountdown = 1; // 1 tick pulse
            // reset input-seen flags after firing (RedPower behavior)
            seenA = false;
            seenB = false;
            changed = true;
        }

        return changed;
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

    // Optional debug accessors (read-only)
    public boolean seenA() { return seenA; }
    public boolean seenB() { return seenB; }
}
