package com.anora.rfl.core.block;

import com.anora.rfl.core.block.common.LogicGateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class NorGateBlock extends LogicGateBlock {

    public NorGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        if (side == null) return false;

        Direction front = frontDir(state);
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();

        return side == front || side == left || side == right;
    }
}

