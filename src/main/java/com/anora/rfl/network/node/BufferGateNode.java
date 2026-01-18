package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TickableNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower-style Buffer Gate node.
 * - Single input
 * - 1 tick delay
 * - Output mirrors input after delay
 */
public final class BufferGateNode extends PositionedNode implements TickableNode {

    private static final int DELAY_TICKS = 1;

    private SignalValue pending = SignalValue.OFF;
    private int timer = 0;

    public BufferGateNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    @Override
    public void beginPass() {}

    @Override
    public boolean evaluate() {
        if (singleIn != pending) {
            pending = singleIn;
            timer = DELAY_TICKS;
        }
        return false;
    }

    @Override
    public void tick() {
        if (timer <= 0) return;
        timer--;
        if (timer == 0 && singleOut != pending) {
            singleOut = pending;
        }
    }
}