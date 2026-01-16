package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * Adapter node: converts single input into a bundled output bit.
 *
 * - Reads singleIn
 * - Drives bundledOut with exactly one channel reflecting singleIn
 * - singleOut is always OFF
 *
 * Useful for building "proper" multi-source bundled circuits in the graph without
 * manual bundle assembly in demos.
 */
public final class SingleToBundledChannelNode extends PositionedNode {

    private final int channel;

    public SingleToBundledChannelNode(NetPos pos, DelayStore store, int channel) {
        super(pos, store);
        if (channel < 0 || channel > 15) {
            throw new IllegalArgumentException("channel must be 0..15, got " + channel);
        }
        this.channel = channel;
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    public int channel() {
        return channel;
    }

    @Override
    public void beginPass() {
        // no-op
    }

    @Override
    public boolean evaluate() {
        boolean on = (singleIn == SignalValue.ON);

        BundledSignal next = BundledSignal.ALL_OFF.with(channel, on);

        if (!next.equals(bundledOut)) {
            bundledOut = next;
            return true;
        }
        return false;
    }
}