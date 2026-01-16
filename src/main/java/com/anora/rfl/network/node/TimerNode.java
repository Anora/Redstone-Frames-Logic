package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TickableNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower-style Timer (pulse generator):
 * - Waits {@code periodTicks}
 * - Emits a pulse of {@code pulseTicks} ticks (default 1)
 * - Repeats
 *
 * This is a pure simulation component (no block yet).
 */
public final class TimerNode extends PositionedNode implements TickableNode {

    private static final int DEFAULT_PERIOD_TICKS = 40; // ~2 seconds at 20 TPS (MC standard)
    private static final int DEFAULT_PULSE_TICKS = 1;

    private int periodTicks;
    private int pulseTicks;

    private int periodCountdown;
    private int pulseCountdown;

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

    /** Sets a new period and restarts the cycle predictably. */
    public void setPeriodTicks(int periodTicks) {
        this.periodTicks = Math.max(1, periodTicks);
        this.periodCountdown = this.periodTicks;
        // keep pulseCountdown as-is (if we're mid-pulse, let it finish)
    }

    /** Sets pulse width (>=1). */
    public void setPulseTicks(int pulseTicks) {
        this.pulseTicks = Math.max(1, pulseTicks);
        // if currently pulsing and new width is smaller, we still finish current pulse naturally
    }

    @Override
    public void beginPass() {
        // Output is stateful; do not clear.
    }

    @Override
    public boolean evaluate() {
        // No inputs; nothing to evaluate.
        return false;
    }

    @Override
    public void tick() {
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
}

