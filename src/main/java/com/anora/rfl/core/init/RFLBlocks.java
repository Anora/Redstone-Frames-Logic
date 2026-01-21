package com.anora.rfl.core.init;

import com.anora.rfl.RFL;
import com.anora.rfl.core.block.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RFLBlocks {

    private RFLBlocks() {
    }

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(RFL.MODID);

    public static final DeferredBlock<RepeaterBlock> REPEATER =
            BLOCKS.registerBlock("repeater", RepeaterBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(2.0f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false)
                            .lightLevel(s -> s.getValue(RepeaterBlock.POWERED) ? 12 : 0));

    public static final DeferredBlock<NotGateBlock> NOT_GATE =
            BLOCKS.registerBlock("not_gate", NotGateBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false));

    public static final DeferredBlock<AndGateBlock> AND_GATE =
            BLOCKS.registerBlock("and_gate", AndGateBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false));

    public static final DeferredBlock<OrGateBlock> OR_GATE =
            BLOCKS.registerBlock("or_gate", OrGateBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false));

    public static final DeferredBlock<NandGateBlock> NAND_GATE =
            BLOCKS.registerBlock("nand_gate", NandGateBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false));

    public static final DeferredBlock<NorGateBlock> NOR_GATE =
            BLOCKS.registerBlock("nor_gate", NorGateBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false));

    public static final DeferredBlock<XorGateBlock> XOR_GATE =
            BLOCKS.registerBlock("xor_gate", XorGateBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false));

    public static final DeferredBlock<XnorGateBlock> XNOR_GATE =
            BLOCKS.registerBlock("xnor_gate", XnorGateBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false));

// unchanged imports …

    public static final DeferredBlock<BufferGateBlock> BUFFER_GATE =
            BLOCKS.registerBlock(
                    "buffer_gate",
                    BufferGateBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false)
            );

    // imports unchanged …

    public static final DeferredBlock<TimerBlock> TIMER =
            BLOCKS.registerBlock(
                    "timer",
                    TimerBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false)
            );

    public static final DeferredBlock<ToggleLatchBlock> TOGGLE_LATCH =
            BLOCKS.registerBlock(
                    "toggle_latch",
                    ToggleLatchBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false)
            );

    public static final DeferredBlock<RSLatchBlock> RS_LATCH =
            BLOCKS.registerBlock(
                    "rs_latch",
                    RSLatchBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_RED)
                            .sound(SoundType.STONE)
                            .strength(1.5f)
                            .noOcclusion()
                            .isRedstoneConductor((s, l, p) -> false)
            );

}
