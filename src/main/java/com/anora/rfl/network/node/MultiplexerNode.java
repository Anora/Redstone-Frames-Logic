package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * 2-to-1 Multiplexer (RedPower-style behavior, sim version).
 *
 * Bundled inputs:
 *  - A   = channel 0
 *  - B   = channel 1
 *  - SEL = channel 2
 *
 * Output:
 *  - singleOut = SEL ? B : A
 */
public final class MultiplexerNode extends PositionedNode {

    public MultiplexerNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    @Override
    public void beginPass() {
        // no-op
    }

    @Override
    public boolean evaluate() {
        boolean a = bundledIn.isOn(0);
        boolean b = bundledIn.isOn(1);
        boolean sel = bundledIn.isOn(2);

        boolean out = sel ? b : a;
        SignalValue next = out ? SignalValue.ON : SignalValue.OFF;

        if (next != singleOut) {
            singleOut = next;
            return true;
        }
        return false;
    }
}

