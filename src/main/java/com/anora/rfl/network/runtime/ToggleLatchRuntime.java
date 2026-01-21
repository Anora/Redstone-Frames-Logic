package com.anora.rfl.network.runtime;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.core.block.ToggleLatchBlock;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.node.ToggleLatchNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Extracted runtime for Toggle Latch blocks.
 *
 * Input: BACK
 * Outputs (one-hot): LEFT/RIGHT via blockstate POWERED.
 * Passthrough (BACK->FRONT) is handled purely by the block's getSignal.
 */
final class ToggleLatchRuntime {

    private final RFLNetworkManager host;
    private final Set<Long> positions = new HashSet<>();

    ToggleLatchRuntime(RFLNetworkManager host) {
        this.host = host;
    }

    boolean hasAny() {
        return !positions.isEmpty();
    }

    void forget(long packed) {
        positions.remove(packed);
    }

    void onPlaced(BlockPos pos) {
        long p = pos.asLong();
        positions.add(p);

        host.ensureDelayStore();
        ensureNode(pos);

        BlockState state = host.level().getBlockState(pos);
        NetworkNode n = host.graph().getNode(host.netPos(pos));
        if (n instanceof ToggleLatchNode t) {
            t.setLeftOn(state.getValue(ToggleLatchBlock.POWERED));
        }

        host.clearLastOut(p);
        host.markDirty(p);
    }

    void onBroken(BlockPos pos) {
        long p = pos.asLong();
        positions.remove(p);
        host.clearLastOut(p);
        host.markDirty(p);
        host.graph().removeNode(host.netPos(pos));
    }

    void toggleManual(BlockPos pos) {
        long p = pos.asLong();
        BlockState state = host.level().getBlockState(pos);
        if (!(state.getBlock() instanceof ToggleLatchBlock)) return;

        boolean newLeftOn = !state.getValue(ToggleLatchBlock.POWERED);
        BlockState newState = state.setValue(ToggleLatchBlock.POWERED, newLeftOn);
        host.level().setBlock(pos, newState, 3);

        NetworkNode n = host.graph().getNode(host.netPos(pos));
        if (n instanceof ToggleLatchNode t) {
            t.setLeftOn(newLeftOn);
        }

        host.clearLastOut(p);
        host.markDirty(p);
    }

    void processInputs() {
        if (positions.isEmpty()) return;

        long[] arr = new long[positions.size()];
        int idx = 0;
        for (Long v : positions) arr[idx++] = v;

        for (long packed : arr) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = host.level().getBlockState(pos);

            if (!(state.getBlock() instanceof ToggleLatchBlock)) {
                positions.remove(packed);
                host.cleanupPos(pos);
                continue;
            }

            ensureNode(pos);

            int backPower = host.readInputPowerFromSide(pos, host.backDir(state));
            boolean hasInput = backPower > 0;

            // Use shared edge tracking to only dirty when input changes
            host.edgeChanged(packed, hasInput);
            host.graph().setExternalSingleIn(host.netPos(pos), hasInput ? SignalValue.ON : SignalValue.OFF);
        }
    }

    void applyOutputs() {
        if (positions.isEmpty()) return;

        for (long packed : new HashSet<>(positions)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = host.level().getBlockState(pos);
            if (!(state.getBlock() instanceof ToggleLatchBlock)) continue;

            NetworkNode node = host.graph().getNode(host.netPos(pos));
            if (!(node instanceof ToggleLatchNode t)) continue;

            boolean leftOn = t.singleOut() == SignalValue.ON;
            boolean current = state.getValue(ToggleLatchBlock.POWERED);

            if (leftOn != current) {
                BlockState newState = state.setValue(ToggleLatchBlock.POWERED, leftOn);
                host.level().setBlock(pos, newState, 3);

                // Notify LEFT/RIGHT outputs only (passthrough depends on world input)
                Direction front = host.frontDir(newState);
                Direction left = front.getCounterClockWise();
                Direction right = front.getClockWise();

                host.level().updateNeighborsAt(pos.relative(left), newState.getBlock());
                host.level().updateNeighborsAt(pos.relative(right), newState.getBlock());
                host.level().updateNeighbourForOutputSignal(pos.relative(left), newState.getBlock());
                host.level().updateNeighbourForOutputSignal(pos.relative(right), newState.getBlock());
            }

            host.setLastOut(packed, leftOn);
        }
    }

    private void ensureNode(BlockPos pos) {
        NetPos np = host.netPos(pos);
        NetworkNode existing = host.graph().getNode(np);
        if (existing instanceof ToggleLatchNode) return;

        host.ensureDelayStore();
        host.graph().putNode(np, new ToggleLatchNode(np, host.delayStore()));
    }
}
