package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TickableNode;
import com.anora.rfl.network.util.DelayStore;

import java.util.Random;

/**
 * Pulse-driven randomizer.
 *
 * Clock input:
 *  - singleIn rising edge picks a random output line and pulses it for 1 tick.
 *
 * Output:
 *  - bundledOut is one-hot on channels 0..7 for 1 tick (then clears)
 *  - singleOut is ON for 1 tick (then clears)
 *
 * Notes:
 *  - This is sim-only right now, so we keep RNG simple.
 *  - Seed is derived from position to keep it stable per-node across runs.
 */
public final class RandomizerNode extends PositionedNode implements TickableNode {

    private final Random rng;

    private SignalValue lastClock = SignalValue.OFF;
    private int pulseCountdown = 0;

    private int lastChoice = -1;

    public RandomizerNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;

        // Stable per-position seed (good for sim repeatability)
        long seed = 1469598103934665603L;
        seed = seed * 1099511628211L + pos.hashCode();
        this.rng = new Random(seed);
    }

    public int lastChoice() {
        return lastChoice;
    }

    @Override
    public void beginPass() {
        // stateful; do not clear
    }

    @Override
    public boolean evaluate() {
        boolean rise = (lastClock == SignalValue.OFF && singleIn == SignalValue.ON);
        lastClock = singleIn;

        if (!rise || pulseCountdown > 0) return false;

        // Pick 0..7
        lastChoice = rng.nextInt(8);

        bundledOut = BundledSignal.ALL_OFF.with(lastChoice, true);
        singleOut = SignalValue.ON;

        pulseCountdown = 1; // 1 tick pulse
        return true;
    }

    @Override
    public void tick() {
        if (pulseCountdown > 0) {
            pulseCountdown--;
            if (pulseCountdown == 0) {
                bundledOut = BundledSignal.ALL_OFF;
                singleOut = SignalValue.OFF;
            }
        }
    }
}
