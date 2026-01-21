package com.anora.rfl.core.block;

import com.anora.rfl.core.block.common.LogicGateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class RSLatchBlock extends LogicGateBlock {

    public RSLatchBlock(Properties properties) {
        super(properties);
        // Default: LEFT outputs
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, true));
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null && side.getAxis().isHorizontal();
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        Direction front = frontDir(state);
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();

        boolean leftOutputs = state.getValue(POWERED);

        if (leftOutputs && dir == left.getOpposite()) return 15;
        if (!leftOutputs && dir == right.getOpposite()) return 15;

        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }
}
