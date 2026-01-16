package com.anora.rfl.network;

import com.anora.rfl.core.BundledSignal;
import com.anora.rfl.core.SignalValue;

/**
 * Base interface for network nodes.
 */
public interface NetworkNode {

    /** Called each settle pass before inputs are computed (used to prevent latchy feedback). */
    default void beginPass() {}

    /** Inputs from the merged neighbor outputs. */
    void setInputs(SignalValue singleIn, BundledSignal bundledIn);

    /**
     * Update outputs based on inputs.
     * @return true if this node changed outputs this evaluate.
     */
    boolean evaluate();

    /** Whether this node contributes its outputs to the merge step. */
    default boolean drivesNetwork() { return true; }

    /** Current output signals. */
    SignalValue singleOut();
    BundledSignal bundledOut();
}


