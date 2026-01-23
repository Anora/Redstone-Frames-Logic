package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TickableNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower-style Sequencer (4-phase), NON-BUNDLED.
 *
 * - Time-driven: advances phase every stepTicks.
 * - Emits a short pulse when advancing to a new phase.
 * - Synchronized to world time; resync resets pulse state.
 *
 * Outputs:
 * - singleOut ON only while pulsing (block uses this to set POWERED)
 * - bundledOut is ALWAYS OFF (no bundled logic)
 *
 * Phase mapping:
 * 0..3 (block maps to front/right/back/left)
 */
public final class SequencerNode extends PositionedNode implements TickableNode {

    private static final int DEFAULT_STEP_TICKS = 20; // 1 second per side
    private static final int DEFAULT_PULSE_TICKS = 1; // short pulse

    private int stepTicks = DEFAULT_STEP_TICKS;
    private int pulseTicks = DEFAULT_PULSE_TICKS;

    private int phase = 0;           // 0..3
    private int ticksUntilNext = stepTicks;
    private int pulseCountdown = 0;

    public SequencerNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    public int phase() {
        return phase;
    }

    public boolean pulsing() {
        return pulseCountdown > 0;
    }

    public int stepTicks() {
        return stepTicks;
    }

    public void setStepTicks(int stepTicks) {
        this.stepTicks = Math.max(1, stepTicks);
        // keep countdown valid
        if (ticksUntilNext <= 0 || ticksUntilNext > this.stepTicks) {
            ticksUntilNext = this.stepTicks;
        }
    }

    @Override
    public void beginPass() {}

    @Override
    public boolean evaluate() {
        // no combinational evaluation; changes happen in tick()
        return false;
    }

    @Override
    public void tick() {
        // end pulse if active
        if (pulseCountdown > 0) {
            pulseCountdown--;
            if (pulseCountdown == 0) {
                singleOut = SignalValue.OFF;
            }
        }

        // advance timing
        ticksUntilNext--;
        if (ticksUntilNext <= 0) {
            phase = (phase + 1) & 3;
            ticksUntilNext = stepTicks;

            // start pulse on new phase
            singleOut = SignalValue.ON;
            pulseCountdown = pulseTicks;
        }
    }

    /**
     * Resync to world time.
     * Any time jump resets the sequencer to a deterministic phase position
     * and clears pulse state.
     */
    public void resyncToWorldTime(long dayTime) {
        long cycle = (long) stepTicks * 4L;
        long off = Math.floorMod(dayTime, cycle);

        int newPhase = (int) (off / stepTicks);  // 0..3
        int into = (int) (off % stepTicks);      // 0..stepTicks-1

        this.phase = newPhase;
        this.ticksUntilNext = (into == 0) ? stepTicks : (stepTicks - into);

        // reset pulse state
        this.pulseCountdown = 0;
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }
}

