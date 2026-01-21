package com.anora.rfl.core.block;

import com.anora.rfl.core.block.common.LogicGateBlock;
import com.anora.rfl.network.runtime.RFLNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class ToggleLatchBlock extends LogicGateBlock {

    public ToggleLatchBlock(Properties properties) {
        super(properties);
        // Default: LEFT output ON
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, true));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.CONSUME;

        // Manual toggle
        RFLNetworkManager.get(sl).toggleLatchManual(pos);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null && side.getAxis().isHorizontal();
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        Direction front = frontDir(state);
        Direction back = front.getOpposite();
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();

        // 1) LEFT/RIGHT one-hot outputs (stateful)
        boolean leftOn = state.getValue(POWERED);

        if (leftOn && dir == left.getOpposite()) return 15;
        if (!leftOn && dir == right.getOpposite()) return 15;

        // 2) Passthrough: BACK -> FRONT (stateless)
        // When neighbor in front asks us (dir == front.getOpposite()), output is whatever is powering our BACK.
        if (dir == front.getOpposite()) {
            // Only possible if we have a real Level instance (server logic).
            if (level instanceof Level lvl) {
                int p = readBackInputPower(lvl, pos, back);
                return p > 0 ? 15 : 0;
            }
        }

        // No output on BACK, and no other outputs
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }

    private static int readBackInputPower(Level lvl, BlockPos pos, Direction back) {
        BlockPos neighbor = pos.relative(back);
        Direction towardThis = back.getOpposite();

        int weak = lvl.getSignal(neighbor, towardThis);
        int direct = lvl.getDirectSignal(neighbor, towardThis);
        int best = lvl.getBestNeighborSignal(neighbor);

        return Math.max(Math.max(weak, direct), best);
    }
}
