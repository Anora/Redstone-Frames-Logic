package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower-style Sequencer (4-phase one-hot output).
 *
 * Clock input:
 *  - singleIn rising edge advances phase.
 *
 * Outputs:
 *  - bundledOut channels 0..3: exactly one is ON (the current phase)
 *  - singleOut remains OFF (sequencer is a multi-output device)
 */
public final class SequencerNode extends PositionedNode {

    private int phase = 0; // 0..3
    private SignalValue lastClock = SignalValue.OFF;

    public SequencerNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = oneHot(phase);
    }

    /** Current phase 0..3 */
    public int phase() {
        return phase;
    }

    @Override
    public void beginPass() {
        // stateful; do not clear
    }

    @Override
    public boolean evaluate() {
        // Rising edge detect on clock input
        boolean rise = (lastClock == SignalValue.OFF && singleIn == SignalValue.ON);
        lastClock = singleIn;

        if (!rise) return false;

        phase = (phase + 1) & 3; // mod 4
        bundledOut = oneHot(phase);
        return true;
    }

    private static BundledSignal oneHot(int phase) {
        // Output on channels 0..3 only
        return BundledSignal.ALL_OFF.with(phase, true);
    }
}
