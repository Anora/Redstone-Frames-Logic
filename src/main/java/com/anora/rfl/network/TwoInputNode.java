package com.anora.rfl.network;

import com.anora.rfl.core.SignalValue;

/**
 * Implemented by logic nodes that have two separate boolean inputs.
 * (RedPower style: left/right inputs)
 */
public interface TwoInputNode {
    void setInputs(SignalValue a, SignalValue b);
}
