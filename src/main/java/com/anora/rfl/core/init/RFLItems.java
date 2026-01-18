package com.anora.rfl.core.init;

import com.anora.rfl.RFL;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RFLItems {

    private RFLItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(RFL.MODID);

    static {
        // NeoForge 21.x safe helper: does NOT call .value() early
        ITEMS.registerSimpleBlockItem("repeater", RFLBlocks.REPEATER);
        ITEMS.registerSimpleBlockItem("not_gate", RFLBlocks.NOT_GATE);
        ITEMS.registerSimpleBlockItem("and_gate", RFLBlocks.AND_GATE);
        ITEMS.registerSimpleBlockItem("or_gate", RFLBlocks.OR_GATE);
        ITEMS.registerSimpleBlockItem("nand_gate", RFLBlocks.NAND_GATE);
        ITEMS.registerSimpleBlockItem("nor_gate", RFLBlocks.NOR_GATE);
        ITEMS.registerSimpleBlockItem("xor_gate", RFLBlocks.XOR_GATE);
        ITEMS.registerSimpleBlockItem("xnor_gate", RFLBlocks.XNOR_GATE);
    }
}