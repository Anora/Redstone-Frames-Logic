package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.util.DelayStore;

/**
 * Light Sensor (sim model):
 *
 * Inputs (bundled):
 *  - Light level (0..15) in channels 0..3 (LSB=ch0)
 *  - SKY_VISIBLE (optional) in channel 4. If false, output is forced OFF.
 *
 * Config:
 *  - mode 0..3 (4 sensitivity levels)
 *
 * Output:
 *  - singleOut = ON if lightLevel >= threshold(mode) and sky_visible
 */
public final class LightSensorNode extends PositionedNode {

    // Four "levels of adjustment" (sim thresholds; can be tuned later)
    // 0 = most inclusive, 3 = most exclusive
    private static final int[] MODE_THRESHOLDS = { 4, 8, 12, 15 };

    private int mode = 1; // default-ish middle

    public LightSensorNode(NetPos pos, DelayStore store) {
        super(pos, store);
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;
    }

    public int mode() { return mode; }

    public void setMode(int mode) {
        if (mode < 0) mode = 0;
        if (mode > 3) mode = 3;
        this.mode = mode;
    }

    @Override
    public void beginPass() {
        // no-op
    }

    @Override
    public boolean evaluate() {
        boolean skyVisible = bundledIn.isOn(4);

        int level = 0;
        level |= bundledIn.isOn(0) ? 1 : 0;
        level |= bundledIn.isOn(1) ? 2 : 0;
        level |= bundledIn.isOn(2) ? 4 : 0;
        level |= bundledIn.isOn(3) ? 8 : 0;

        boolean on = skyVisible && (level >= MODE_THRESHOLDS[mode]);
        SignalValue next = on ? SignalValue.ON : SignalValue.OFF;

        if (next != singleOut) {
            singleOut = next;
            return true;
        }
        return false;
    }
}
