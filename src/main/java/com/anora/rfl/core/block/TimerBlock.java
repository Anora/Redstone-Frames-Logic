package com.anora.rfl.core.block;

import com.anora.rfl.core.block.common.LogicGateBlock;
import com.anora.rfl.network.runtime.RFLNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * RedPower-style Timer.
 *
 * - No input
 * - Outputs a 1-tick pulse on FRONT + LEFT + RIGHT (block handles multi-output)
 * - Period is configured via right-click (no BlockEntity)
 * - Synchronized to world time (handled by manager/node)
 */
public class TimerBlock extends LogicGateBlock {

    /** Low-profile: 2/16 tall. */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);


    public TimerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                                               net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                                        net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    /**
     * Right-click to adjust period (no GUI, no BlockEntity).
     * - Right click: increase
     * - Sneak + right click: decrease
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.CONSUME;

        RFLNetworkManager mgr = RFLNetworkManager.get(sl);

        // Shift = reverse
        mgr.cycleTimerPeriod(pos, player.isShiftKeyDown());

        // Optional click sound + feedback (safe, doesn't touch repeater logic)
        level.playSound(null, pos, SoundType.STONE.getPlaceSound(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);

        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[RFL] Timer period updated"
            ));
        }

        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        if (side == null) return true;
        return side.getAxis().isHorizontal();
    }

    /**
     * Multi-output: FRONT + LEFT + RIGHT only (no output on BACK).
     * POWERED is the pulse state.
     */
    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        if (!state.getValue(POWERED)) return 0;

        Direction front = frontDir(state);
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();

        if (dir == front.getOpposite()) return 15;
        if (dir == left.getOpposite()) return 15;
        if (dir == right.getOpposite()) return 15;

        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }
}

