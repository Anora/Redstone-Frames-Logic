package com.anora.rfl.core.block;

import com.anora.rfl.core.block.common.LogicGateBlock;
import com.anora.rfl.network.runtime.RFLNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class RepeaterBlock extends LogicGateBlock {

    public static final IntegerProperty DELAY = IntegerProperty.create("delay", 1, 9);

    public RepeaterBlock(Properties properties) {
        super(properties);

        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(POWERED, false)
                        .setValue(DELAY, 1)
        );
    }

    // ----------------- Shape (primary dust fix) -----------------

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); // adds FACING + POWERED
        builder.add(DELAY);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.CONSUME;

        RFLNetworkManager mgr = RFLNetworkManager.get(sl);

        int currentTicks = mgr.getRepeaterDelayTicks(pos);
        int currentIndex = mgr.delayTicksToIndex(currentTicks);
        int nextIndex = (currentIndex >= 9) ? 1 : (currentIndex + 1);
        int nextTicks = mgr.indexToDelayTicks(nextIndex);

        mgr.setRepeaterDelayTicks(pos, nextTicks);

        BlockState newState = state.setValue(DELAY, nextIndex);
        level.setBlock(pos, newState, 3);

        level.playSound(null, pos, SoundType.STONE.getPlaceSound(),
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);

        if (player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal(
                    "[RFL] Repeater delay = " + nextTicks + " ticks (step " + nextIndex + "/9)"
            ));
        }

        Direction front = frontDir(newState);
        Direction back = backDir(newState);
        level.updateNeighbourForOutputSignal(pos, newState.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(front), newState.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(back), newState.getBlock());

        return InteractionResult.CONSUME;
    }

    // ----------------- Redstone connectivity/output -----------------

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

        // Minecraft queries power "toward the requester", so output side arrives as opposite(front)
        return dir == frontDir(state).getOpposite() ? 15 : 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction dir) {
        return getSignal(state, level, pos, dir);
    }
}

