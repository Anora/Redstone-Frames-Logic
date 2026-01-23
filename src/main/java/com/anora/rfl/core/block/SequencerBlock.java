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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * RedPower-style Sequencer:
 * - Outputs a short pulse on one of the 4 horizontal sides
 * - Rotates between sides at a user-configurable step time
 * - Synchronized to world time; time jumps reset phase
 *
 * Output side mapping by PHASE:
 * 0 = FRONT, 1 = RIGHT, 2 = BACK, 3 = LEFT (clockwise)
 */
public class SequencerBlock extends LogicGateBlock {

    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 3);

    public SequencerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState()
                .setValue(POWERED, false)
                .setValue(PHASE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PHASE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.CONSUME;

        // No GUI yet: cycle speed for now (like timer)
        boolean reverse = player.isShiftKeyDown();
        RFLNetworkManager.get(sl).cycleSequencerStep(pos, reverse);

        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction side) {
        return side != null && side.getAxis().isHorizontal();
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        if (!state.getValue(POWERED)) return 0;

        Direction front = frontDir(state);
        int phase = state.getValue(PHASE);

        Direction out = switch (phase) {
            case 0 -> front;
            case 1 -> front.getClockWise();
            case 2 -> front.getOpposite();
            case 3 -> front.getCounterClockWise();
            default -> front;
        };

        // Neighbor asks us with dir == out.getOpposite()
        return (dir == out.getOpposite()) ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }
}