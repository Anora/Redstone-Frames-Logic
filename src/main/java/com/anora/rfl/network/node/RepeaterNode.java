package com.anora.rfl.network.node;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.TickableNode;
import com.anora.rfl.network.util.DelayStore;

/**
 * RedPower-like repeater: symmetric delay on ON and OFF.
 */
public final class RepeaterNode extends PositionedNode implements TickableNode {

    private static final int DEFAULT_DELAY_TICKS = 8;

    private SignalValue lastSeenInput = SignalValue.OFF;

    private int countdown = 0;
    private SignalValue pendingTarget = SignalValue.OFF;

    public RepeaterNode(NetPos pos, DelayStore store) {
        super(pos, store);

        // defaults
        this.singleOut = SignalValue.OFF;
        this.bundledOut = BundledSignal.ALL_OFF;

        // restore persisted runtime state if available
        if (store != null) {
            DelayStore.RepeaterState st = store.getRepeaterState(pos);
            if (st != null) {
                this.lastSeenInput = (st.lastSeenInput() != null) ? st.lastSeenInput() : SignalValue.OFF;
                this.countdown = Math.max(0, st.countdown());
                this.pendingTarget = (st.pendingTarget() != null) ? st.pendingTarget() : SignalValue.OFF;
                this.singleOut = (st.singleOut() != null) ? st.singleOut() : SignalValue.OFF;
            }
        }
    }

    private int delayTicks() {
        if (store == null) return DEFAULT_DELAY_TICKS;
        return store.getDelayTicks(pos, DEFAULT_DELAY_TICKS);
    }

    private void persistState() {
        if (store == null) return;
        store.setRepeaterState(pos, new DelayStore.RepeaterState(
                lastSeenInput,
                countdown,
                pendingTarget,
                singleOut
        ));
    }

    @Override
    public void beginPass() {
        // Repeater output is stateful, do NOT clear it here.
    }

    @Override
    public boolean evaluate() {
        // detect input edge and schedule output change
        if (singleIn != lastSeenInput) {
            lastSeenInput = singleIn;
            pendingTarget = singleIn;
            countdown = delayTicks();

            // persist immediately so an in-flight countdown survives reload
            persistState();
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        if (countdown > 0) {
            countdown--;
            if (countdown == 0) {
                singleOut = pendingTarget;
            }
            // persist countdown changes + possible output flip
            persistState();
        }
    }
}
