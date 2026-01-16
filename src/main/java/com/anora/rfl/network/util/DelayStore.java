package com.anora.rfl.network.util;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.network.NetPos;

/**
 * Temporary storage for repeater delays while we don't have real block NBT yet.
 * Also persists repeater runtime state (countdown + pending output, etc).
 */
public interface DelayStore {
    int getDelayTicks(NetPos pos, int defaultDelayTicks);
    void setDelayTicks(NetPos pos, int delayTicks);

    /**
     * Optional persisted runtime state for RepeaterNode.
     * Returning null means "no persisted state; use defaults".
     */
    default RepeaterState getRepeaterState(NetPos pos) {
        return null;
    }

    /** Save/overwrite persisted runtime state for RepeaterNode. */
    default void setRepeaterState(NetPos pos, RepeaterState state) {
        // no-op by default
    }

    /** Remove persisted runtime state for RepeaterNode (optional). */
    default void clearRepeaterState(NetPos pos) {
        // no-op by default
    }

    /** Optional persistence hooks. */
    default void load() {}
    default void save() {}

    /**
     * State blob for the repeater's "in-flight" timing and remembered inputs.
     */
    record RepeaterState(
            SignalValue lastSeenInput,
            int countdown,
            SignalValue pendingTarget,
            SignalValue singleOut
    ) {}
}




