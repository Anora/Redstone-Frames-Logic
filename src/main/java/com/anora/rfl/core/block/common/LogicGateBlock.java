package com.anora.rfl.core.block.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Shared base for low-profile "logic" blocks.
 *
 * Convention (same as your repeater):
 *  - FACING points toward the FRONT (output side)
 *  - BACK is input side (front.getOpposite())
 *
 * Important redstone rule:
 *  getSignal() must output when queried from dir == front.getOpposite()
 *  because MC queries power "toward the requester".
 */
public abstract class LogicGateBlock extends GroundRotatableBlock {

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    /** Must match manager convention. */
    protected static final boolean FRONT_IS_FACING = true;

    /** Low-profile: 2/16 tall (fixes dust connectivity). */
    protected static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    protected LogicGateBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(POWERED, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    protected static Direction frontDir(BlockState state) {
        Direction f = state.getValue(FACING);
        return FRONT_IS_FACING ? f : f.getOpposite();
    }

    protected static Direction backDir(BlockState state) {
        return frontDir(state).getOpposite();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        if (side == null) return true;
        Direction front = frontDir(state);
        Direction back = front.getOpposite();
        return side == front || side == back;
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        if (!state.getValue(POWERED)) return 0;

        // MC asks "how much power do you provide toward me?"
        return dir == frontDir(state).getOpposite() ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }
}

