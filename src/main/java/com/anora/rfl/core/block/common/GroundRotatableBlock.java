// File: src/main/java/com/anora/rfl/core/block/common/GroundRotatableBlock.java
package com.anora.rfl.core.block.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;

/**
 * Base block:
 * - Ground-only placement
 * - Horizontal rotation
 *
 * IMPORTANT:
 * Do NOT default to "connect to redstone on all horizontal sides" here.
 * That causes dust to visually connect to sides that are not real ports
 * for logic gates like AND/OR/etc.
 *
 * Each logic block should explicitly decide its redstone ports by overriding
 * canConnectRedstone in LogicGateBlock or the specific gate block.
 *
 * NOTE: DirectionProperty does not exist in 1.21.2, so we use EnumProperty<Direction>.
 */
public class GroundRotatableBlock extends Block {

    public static final EnumProperty<Direction> FACING =
            EnumProperty.create("facing", Direction.class,
                    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    public GroundRotatableBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Default: do NOT allow dust connections.
     * Subclasses (LogicGateBlock / wires) should explicitly opt-in.
     */
    @Override
    @SuppressWarnings("deprecation")
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }
}





