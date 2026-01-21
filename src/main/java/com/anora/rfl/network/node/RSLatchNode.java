package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TwoInputNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower RS Latch (two I/O faces that swap roles).
 *
 * Inputs:
 *   A = LEFT power
 *   B = RIGHT power
 *
 * State:
 *   singleOut == ON  => LEFT is the constant-output side (RIGHT is input)
 *   singleOut == OFF => RIGHT is the constant-output side (LEFT is input)
 *
 * Switching rule:
 *   A rising-edge pulse on the CURRENT INPUT side flips the latch.
 *
 * Example:
 *   If LEFT is output (singleOut=ON), then RIGHT is input.
 *   Pulse RIGHT => latch flips so RIGHT becomes output (singleOut=OFF).
 */
public final class RSLatchNode extends PositionedNode implements TwoInputNode {

    private SignalValue inLeft = SignalValue.OFF;
    private SignalValue inRight = SignalValue.OFF;

    private boolean lastLeft = false;
    private boolean lastRight = false;

    public RSLatchNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.ON; // default: LEFT outputs
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    /** Initialize from blockstate POWERED (true => LEFT outputs). */
    public void setLeftOutputs(boolean leftOutputs) {
        this.singleOut = leftOutputs ? SignalValue.ON : SignalValue.OFF;
    }

    @Override
    public void setInputs(SignalValue a, SignalValue b) {
        this.inLeft = (a == null) ? SignalValue.OFF : a;
        this.inRight = (b == null) ? SignalValue.OFF : b;
    }

    @Override
    public void beginPass() {}

    @Override
    public boolean evaluate() {
        boolean leftNow = (inLeft == SignalValue.ON);
        boolean rightNow = (inRight == SignalValue.ON);

        boolean leftRising = leftNow && !lastLeft;
        boolean rightRising = rightNow && !lastRight;

        lastLeft = leftNow;
        lastRight = rightNow;

        boolean leftIsOutput = (singleOut == SignalValue.ON);
        // Only the CURRENT INPUT side can trigger a swap.
        if (leftIsOutput) {
            // RIGHT is input
            if (rightRising) {
                singleOut = SignalValue.OFF; // RIGHT becomes output
                return true;
            }
        } else {
            // LEFT is input
            if (leftRising) {
                singleOut = SignalValue.ON; // LEFT becomes output
                return true;
            }
        }

        return false;
    }
}
