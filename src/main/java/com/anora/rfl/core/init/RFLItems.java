package com.anora.rfl.core.init;

import com.anora.rfl.RFL;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RFLItems {

    private RFLItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RFL.MODID);

    static {
        // NeoForge 21.x safe helper: does NOT call .value() early
        ITEMS.registerSimpleBlockItem("repeater", RFLBlocks.REPEATER);
    }
}