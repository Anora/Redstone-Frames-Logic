package com.anora.rfl.network.runtime;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.core.block.RSLatchBlock;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.TwoInputNode;
import com.anora.rfl.network.node.RSLatchNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class RSLatchRuntime {

    private final RFLNetworkManager host;

    private final Set<Long> positions = new HashSet<>();
    private final Map<Long, Integer> lastKey = new HashMap<>();

    RSLatchRuntime(RFLNetworkManager host) {
        this.host = host;
    }

    boolean hasAny() {
        return !positions.isEmpty();
    }

    void forget(long packed) {
        positions.remove(packed);
        lastKey.remove(packed);
    }

    void onPlaced(BlockPos pos) {
        long p = pos.asLong();
        positions.add(p);

        host.ensureDelayStore();
        ensureNode(pos);

        BlockState state = host.level().getBlockState(pos);
        NetworkNode n = host.graph().getNode(host.netPos(pos));
        if (n instanceof RSLatchNode rs) {
            rs.setLeftOutputs(state.getValue(RSLatchBlock.POWERED));
        }

        lastKey.remove(p);
        host.clearLastOut(p);
        host.markDirty(p);
    }

    void onBroken(BlockPos pos) {
        long p = pos.asLong();
        positions.remove(p);
        lastKey.remove(p);
        host.clearLastOut(p);
        host.markDirty(p);
        host.graph().removeNode(host.netPos(pos));
    }

    void processInputs() {
        if (positions.isEmpty()) return;

        long[] arr = new long[positions.size()];
        int idx = 0;
        for (Long v : positions) arr[idx++] = v;

        for (long packed : arr) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = host.level().getBlockState(pos);

            if (!(state.getBlock() instanceof RSLatchBlock)) {
                positions.remove(packed);
                lastKey.remove(packed);
                host.cleanupPos(pos);
                continue;
            }

            ensureNode(pos);

            Direction front = host.frontDir(state);
            Direction left = front.getCounterClockWise();
            Direction right = front.getClockWise();

            int lp = host.readInputPowerFromSide(pos, left);
            int rp = host.readInputPowerFromSide(pos, right);

            SignalValue a = (lp > 0) ? SignalValue.ON : SignalValue.OFF;
            SignalValue b = (rp > 0) ? SignalValue.ON : SignalValue.OFF;

            NetPos np = host.netPos(pos);
            NetworkNode node = host.graph().getNode(np);
            if (node instanceof TwoInputNode two) {
                two.setInputs(a, b);
            }

            // We are feeding inputs directly, so keep external input OFF
            host.graph().setExternalSingleIn(np, SignalValue.OFF);

            int key = (a == SignalValue.ON ? 1 : 0) | (b == SignalValue.ON ? 2 : 0);
            Integer prev = lastKey.get(packed);
            if (prev == null || prev != key) {
                lastKey.put(packed, key);
                host.markDirty(packed);
            }
        }
    }

    void applyOutputs() {
        if (positions.isEmpty()) return;

        for (long packed : new HashSet<>(positions)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = host.level().getBlockState(pos);
            if (!(state.getBlock() instanceof RSLatchBlock)) continue;

            NetPos np = host.netPos(pos);
            NetworkNode node = host.graph().getNode(np);
            if (!(node instanceof RSLatchNode rs)) continue;

            boolean leftOutputs = (rs.singleOut() == SignalValue.ON);
            boolean current = state.getValue(RSLatchBlock.POWERED);

            if (leftOutputs != current) {
                BlockState newState = state.setValue(RSLatchBlock.POWERED, leftOutputs);
                host.level().setBlock(pos, newState, 3);

                // Notify both latch sides (roles swap)
                Direction front = host.frontDir(newState);
                Direction left = front.getCounterClockWise();
                Direction right = front.getClockWise();

                host.level().updateNeighborsAt(pos.relative(left), newState.getBlock());
                host.level().updateNeighborsAt(pos.relative(right), newState.getBlock());
                host.level().updateNeighbourForOutputSignal(pos.relative(left), newState.getBlock());
                host.level().updateNeighbourForOutputSignal(pos.relative(right), newState.getBlock());
            }

            host.setLastOut(packed, leftOutputs);
        }
    }

    private void ensureNode(BlockPos pos) {
        NetPos np = host.netPos(pos);
        NetworkNode existing = host.graph().getNode(np);
        if (existing instanceof RSLatchNode) return;

        host.ensureDelayStore();
        host.graph().putNode(np, new RSLatchNode(np, host.delayStore()));
    }
}