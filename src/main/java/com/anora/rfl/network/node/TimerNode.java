package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TickableNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower-style Timer (pulse generator):
 * - Waits periodTicks
 * - Emits a pulse of pulseTicks (default 1)
 * - Repeats
 * - Can be inhibited (paused) by external redstone input (manager sets it)
 * - Can resync to world time (dayTime), resetting phase (RP2 behavior on time jumps)
 */
public final class TimerNode extends PositionedNode implements TickableNode {

    private static final int DEFAULT_PERIOD_TICKS = 40; // 2 seconds
    private static final int DEFAULT_PULSE_TICKS = 4;

    private int periodTicks;
    private int pulseTicks;

    private int periodCountdown;
    private int pulseCountdown;

    private boolean inhibited = false;

    public TimerNode(NetPos pos, DelayStore store) {
        this(pos, store, DEFAULT_PERIOD_TICKS, DEFAULT_PULSE_TICKS);
    }

    public TimerNode(NetPos pos, DelayStore store, int periodTicks, int pulseTicks) {
        super(pos, store);

        this.periodTicks = Math.max(1, periodTicks);
        this.pulseTicks = Math.max(1, pulseTicks);

        this.periodCountdown = this.periodTicks;
        this.pulseCountdown = 0;

        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    public int periodTicks() {
        return periodTicks;
    }

    public int pulseTicks() {
        return pulseTicks;
    }

    public void setPeriodTicks(int periodTicks) {
        this.periodTicks = Math.max(1, periodTicks);
        // Keep phase roughly stable unless resynced explicitly.
        this.periodCountdown = Math.min(this.periodCountdown, this.periodTicks);
        if (this.periodCountdown <= 0) this.periodCountdown = this.periodTicks;
    }

    public void setPulseTicks(int pulseTicks) {
        this.pulseTicks = Math.max(1, pulseTicks);
    }

    public void setInhibited(boolean inhibited) {
        this.inhibited = inhibited;
    }

    @Override
    public void beginPass() {
        // Output is stateful; do not clear.
    }

    @Override
    public boolean evaluate() {
        // No combinational evaluation; output changes in tick().
        return false;
    }

    @Override
    public void tick() {
        if (inhibited) {
            // Freeze pointer/phase and do not output pulses.
            return;
        }

        // If we're in a pulse, count it down
        if (pulseCountdown > 0) {
            pulseCountdown--;
            if (pulseCountdown == 0) {
                singleOut = SignalValue.OFF;
            }
            return;
        }

        // Otherwise, count down to next pulse
        periodCountdown--;
        if (periodCountdown <= 0) {
            // Start a pulse
            singleOut = SignalValue.ON;
            pulseCountdown = pulseTicks;

            // Reset for next cycle
            periodCountdown = periodTicks;
        }
    }

    /**
     * RP2-style world time synchronization:
     * resets the phase based on the provided dayTime.
     *
     * Any time jump (sleep, /time) should call this to reset the timer.
     */
    public void resyncToWorldTime(long dayTime) {
        // Reset output and pulse immediately
        pulseCountdown = 0;
        singleOut = SignalValue.OFF;

        int mod = (int) Math.floorMod(dayTime, (long) periodTicks);
        periodCountdown = (mod == 0) ? periodTicks : (periodTicks - mod);
    }
}

