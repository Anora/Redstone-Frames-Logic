package com.anora.rfl.network.runtime;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.core.block.SequencerBlock;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.node.SequencerNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

final class SequencerRuntime {

    // ticks per side
    private static final int MIN_STEP_TICKS = 4;
    private static final int DEFAULT_STEP_TICKS = 20;
    private static final int MAX_STEP_TICKS = 6000; // 5 minutes per side

    private final RFLNetworkManager host;

    private final Set<Long> positions = new HashSet<>();
    private long lastDayTime = Long.MIN_VALUE;

    SequencerRuntime(RFLNetworkManager host) {
        this.host = host;
    }

    boolean hasAny() {
        return !positions.isEmpty();
    }

    boolean needsTick() {
        return hasAny();
    }

    void addStillDirty(Set<Long> stillDirty) {
        stillDirty.addAll(positions);
    }

    void forget(long packed) {
        positions.remove(packed);
    }

    void onPlaced(BlockPos pos) {
        long p = pos.asLong();
        positions.add(p);

        host.ensureDelayStore();
        ensureNode(pos);

        NetPos np = host.netPos(pos);
        int step = host.delayStore().getDelayTicks(np, DEFAULT_STEP_TICKS);
        if (step <= 0) {
            host.delayStore().setDelayTicks(np, DEFAULT_STEP_TICKS);
            host.delayStore().save();
            step = DEFAULT_STEP_TICKS;
        }

        NetworkNode node = host.graph().getNode(np);
        if (node instanceof SequencerNode s) {
            s.setStepTicks(step);
            s.resyncToWorldTime(host.level().getDayTime());
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

    void cycleStep(BlockPos pos, boolean reverse) {
        host.ensureDelayStore();
        NetPos np = host.netPos(pos);

        int current = host.delayStore().getDelayTicks(np, DEFAULT_STEP_TICKS);
        int step = 4;
        int next = reverse ? (current - step) : (current + step);

        if (next < MIN_STEP_TICKS) next = MIN_STEP_TICKS;
        if (next > MAX_STEP_TICKS) next = MIN_STEP_TICKS;

        host.delayStore().setDelayTicks(np, next);
        host.delayStore().save();

        NetworkNode node = host.graph().getNode(np);
        if (node instanceof SequencerNode s) {
            s.setStepTicks(next);
            s.resyncToWorldTime(host.level().getDayTime());
        }

        long packed = pos.asLong();
        host.clearLastOut(packed);
        host.markDirty(packed);
    }

    void preTick() {
        if (positions.isEmpty()) return;

        long day = host.level().getDayTime();
        if (lastDayTime != Long.MIN_VALUE && day != lastDayTime + 1) {
            // time jump: reset all sequencers
            for (long packed : new HashSet<>(positions)) {
                BlockPos pos = BlockPos.of(packed);
                NetPos np = host.netPos(pos);

                NetworkNode node = host.graph().getNode(np);
                if (node instanceof SequencerNode s) {
                    int stepTicks = host.delayStore().getDelayTicks(np, DEFAULT_STEP_TICKS);
                    s.setStepTicks(stepTicks);
                    s.resyncToWorldTime(day);
                }
                host.markDirty(packed);
            }
        }
        lastDayTime = day;
    }

    void processInputs() {
        if (positions.isEmpty()) return;

        for (long packed : new HashSet<>(positions)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = host.level().getBlockState(pos);

            if (!(state.getBlock() instanceof SequencerBlock)) {
                positions.remove(packed);
                host.cleanupPos(pos);
                continue;
            }

            ensureNode(pos);

            // no inputs; keep dirty so phase/pulse applies
            host.markDirty(packed);
        }
    }

    void applyOutputs() {
        if (positions.isEmpty()) return;

        for (long packed : new HashSet<>(positions)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = host.level().getBlockState(pos);
            if (!(state.getBlock() instanceof SequencerBlock)) continue;

            NetPos np = host.netPos(pos);
            NetworkNode node = host.graph().getNode(np);
            if (!(node instanceof SequencerNode s)) continue;

            boolean pulsing = s.pulsing();
            int phase = s.phase();

            boolean curPow = state.getValue(SequencerBlock.POWERED);
            int curPhase = state.getValue(SequencerBlock.PHASE);

            if (curPow != pulsing || curPhase != phase) {
                BlockState newState = state
                        .setValue(SequencerBlock.POWERED, pulsing)
                        .setValue(SequencerBlock.PHASE, phase);

                host.level().setBlock(pos, newState, 3);

                // Notify all four horizontal neighbors (simple and safe)
                Direction front = host.frontDir(newState);
                Direction back = front.getOpposite();
                Direction left = front.getCounterClockWise();
                Direction right = front.getClockWise();

                host.level().updateNeighborsAt(pos.relative(front), newState.getBlock());
                host.level().updateNeighborsAt(pos.relative(back), newState.getBlock());
                host.level().updateNeighborsAt(pos.relative(left), newState.getBlock());
                host.level().updateNeighborsAt(pos.relative(right), newState.getBlock());

                host.level().updateNeighbourForOutputSignal(pos.relative(front), newState.getBlock());
                host.level().updateNeighbourForOutputSignal(pos.relative(back), newState.getBlock());
                host.level().updateNeighbourForOutputSignal(pos.relative(left), newState.getBlock());
                host.level().updateNeighbourForOutputSignal(pos.relative(right), newState.getBlock());
            }

            host.setLastOut(packed, pulsing);
        }
    }

    private void ensureNode(BlockPos pos) {
        NetPos np = host.netPos(pos);
        NetworkNode existing = host.graph().getNode(np);
        if (existing instanceof SequencerNode) return;

        host.ensureDelayStore();
        host.graph().putNode(np, new SequencerNode(np, host.delayStore()));
    }
}
