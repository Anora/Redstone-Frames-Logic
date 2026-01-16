package com.anora.rfl.core;

public enum SignalChannel {
    CH0, CH1, CH2, CH3,
    CH4, CH5, CH6, CH7,
    CH8, CH9, CH10, CH11,
    CH12, CH13, CH14, CH15;

    public static final int COUNT = 16;

    public static SignalChannel fromIndex(int index) {
        if (index < 0 || index >= COUNT) {
            throw new IndexOutOfBoundsException("SignalChannel index out of range: " + index);
        }
        return values()[index];
    }
}
