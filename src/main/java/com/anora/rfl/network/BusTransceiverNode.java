package com.anora.rfl.network;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.node.PositionedNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * Bus Transceiver (sim model).
 *
 * Real block has two bundled connections and two latch inputs enabling flow
 * front<->back. In sim (without directional sides), we encode both buses in one bundle:
 *
 * Bundled inputs:
 *  - Front bus bits: channels 0..3
 *  - Back  bus bits: channels 8..11
 *  - Enable front->back: channel 4
 *  - Enable back->front: channel 5
 *
 * Bundled outputs:
 *  - Toward front (from back): channels 0..3 when back->front enabled
 *  - Toward back  (from front): channels 8..11 when front->back enabled
 *
 * Default is blocked in both directions.
 */
public final class BusTransceiverNode extends PositionedNode {

    public BusTransceiverNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    @Override
    public void beginPass() {
        // stateful is fine; but we recompute bundledOut each evaluate
    }

    @Override
    public boolean evaluate() {
        boolean enFwd = bundledIn.isOn(4); // front -> back
        boolean enRev = bundledIn.isOn(5); // back -> front

        // Read 4-bit front bus
        boolean f0 = bundledIn.isOn(0);
        boolean f1 = bundledIn.isOn(1);
        boolean f2 = bundledIn.isOn(2);
        boolean f3 = bundledIn.isOn(3);

        // Read 4-bit back bus
        boolean b0 = bundledIn.isOn(8);
        boolean b1 = bundledIn.isOn(9);
        boolean b2 = bundledIn.isOn(10);
        boolean b3 = bundledIn.isOn(11);

        BundledSignal next = BundledSignal.ALL_OFF;

        // back -> front output on 0..3
        if (enRev) {
            next = next.with(0, b0).with(1, b1).with(2, b2).with(3, b3);
        }

        // front -> back output on 8..11
        if (enFwd) {
            next = next.with(8, f0).with(9, f1).with(10, f2).with(11, f3);
        }

        if (!next.equals(bundledOut)) {
            bundledOut = next;
            return true;
        }
        return false;
    }
}
