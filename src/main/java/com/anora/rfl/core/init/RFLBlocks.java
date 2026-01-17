package com.anora.rfl.core.init;

import com.anora.rfl.RFL;
import com.anora.rfl.core.block.NotGateBlock;
import com.anora.rfl.core.block.RepeaterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.anora.rfl.core.block.AndGateBlock;

public final class RFLBlocks {

    private RFLBlocks() {}

    // IMPORTANT: use DeferredRegister.Blocks (not DeferredRegister.create(Registries.BLOCK,...))
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RFL.MODID);

    // registerBlock sets the ID internally (avoids “Block id not set”)
    public static final DeferredBlock<RepeaterBlock> REPEATER = BLOCKS.registerBlock(
            "repeater",
            RepeaterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .sound(SoundType.STONE)
                    .strength(2.0f)
                    .noOcclusion() // ✅ important: not treated like a full cube
                    .isRedstoneConductor((state, level, pos) -> false) // ✅ no through-block conduction
                    .lightLevel(state -> state.getValue(RepeaterBlock.POWERED) ? 12 : 0)
    );

    public static final DeferredBlock<NotGateBlock> NOT_GATE = BLOCKS.registerBlock(
            "not_gate",
            NotGateBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .sound(SoundType.STONE)
                    .strength(1.5f)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
    );

    public static final DeferredBlock<AndGateBlock> AND_GATE = BLOCKS.registerBlock(
            "and_gate",
            AndGateBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .sound(SoundType.STONE)
                    .strength(1.5f)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
    );

}