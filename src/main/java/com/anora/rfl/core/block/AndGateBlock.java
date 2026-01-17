package com.anora.rfl.core.block;

import com.anora.rfl.core.block.common.LogicGateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class AndGateBlock extends LogicGateBlock {

    public AndGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        // IMPORTANT:
        // side == null is a "general probe" used by redstone dust.
        // If we return true here, dust may connect visually on non-ports (like the back).
        if (side == null) return false;

        Direction front = frontDir(state);
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();

        // AND ports: inputs = left/right, output = front. BACK is not a port.
        return side == front || side == left || side == right;
    }
}