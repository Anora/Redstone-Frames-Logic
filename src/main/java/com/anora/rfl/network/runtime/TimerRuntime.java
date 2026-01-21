package com.anora.rfl.network.runtime;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.core.block.TimerBlock;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.node.TimerNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Extracted runtime for Timer blocks.
 *
 * Keeps time-driven ticking out of the main manager.
 */
final class TimerRuntime {

    // Timer rules (ticks)
    private static final int TIMER_MIN_TICKS = 4;       // 0.2s
    private static final int TIMER_DEFAULT_TICKS = 40;  // 2.0s
    private static final int TIMER_MAX_TICKS = 1200;    // 60s

    private final RFLNetworkManager host;

    private final Set<Long> timerPositions = new HashSet<>();
    private long lastDayTime = Long.MIN_VALUE;

    TimerRuntime(RFLNetworkManager host) {
        this.host = host;
    }

    boolean hasAny() {
        return !timerPositions.isEmpty();
    }

    boolean needsTick() {
        // Timers are time-driven: if any exist, we need the sim to run.
        return hasAny();
    }

    void addStillDirty(Set<Long> stillDirty) {
        // Timers must always continue ticking.
        stillDirty.addAll(timerPositions);
    }

    void forget(long packed) {
        timerPositions.remove(packed);
    }

    void onPlaced(BlockPos pos) {
        long p = pos.asLong();
        timerPositions.add(p);

        host.ensureDelayStore();
        ensureTimerNode(pos);

        // Initialize period if missing
        NetPos np = host.netPos(pos);
        int current = host.delayStore().getDelayTicks(np, TIMER_DEFAULT_TICKS);
        if (current <= 0) {
            host.delayStore().setDelayTicks(np, TIMER_DEFAULT_TICKS);
            host.delayStore().save();
            current = TIMER_DEFAULT_TICKS;
        }

        // Sync node to current world time
        NetworkNode node = host.graph().getNode(np);
        if (node instanceof TimerNode t) {
            t.setPeriodTicks(current);
            t.resyncToWorldTime(host.level().getDayTime());
        }

        host.clearLastOut(p);
        host.markDirty(p);
    }

    void onBroken(BlockPos pos) {
        long p = pos.asLong();
        timerPositions.remove(p);
        host.markDirty(p);
        host.clearLastOut(p);
        host.graph().removeNode(host.netPos(pos));
    }

    void cyclePeriod(BlockPos pos, boolean reverse) {
        host.ensureDelayStore();
        NetPos np = host.netPos(pos);

        int current = host.delayStore().getDelayTicks(np, TIMER_DEFAULT_TICKS);
        int step = 4;
        int next = reverse ? (current - step) : (current + step);

        if (next < TIMER_MIN_TICKS) next = TIMER_MIN_TICKS;
        if (next > TIMER_MAX_TICKS) next = TIMER_MIN_TICKS; // wrap

        host.delayStore().setDelayTicks(np, next);
        host.delayStore().save();

        NetworkNode node = host.graph().getNode(np);
        if (node instanceof TimerNode t) {
            t.setPeriodTicks(next);
            // changing period resets phase in a predictable way
            t.resyncToWorldTime(host.level().getDayTime());
        }

        long packed = pos.asLong();
        host.clearLastOut(packed);
        host.markDirty(packed);
    }

    void preTick() {
        if (timerPositions.isEmpty()) return;

        host.ensureDelayStore();

        long day = host.level().getDayTime();
        if (lastDayTime != Long.MIN_VALUE && day != lastDayTime + 1) {
            // resync all timers if time jumps (sleep, /time)
            for (long packed : new HashSet<>(timerPositions)) {
                BlockPos pos = BlockPos.of(packed);
                NetPos np = host.netPos(pos);

                NetworkNode node = host.graph().getNode(np);
                if (node instanceof TimerNode t) {
                    int period = host.delayStore().getDelayTicks(np, TIMER_DEFAULT_TICKS);
                    t.setPeriodTicks(period);
                    t.resyncToWorldTime(day);
                }

                host.markDirty(packed);
            }
        }
        lastDayTime = day;
    }

    void processInputs() {
        if (timerPositions.isEmpty()) return;

        for (long packed : new HashSet<>(timerPositions)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = host.level().getBlockState(pos);

            if (!(state.getBlock() instanceof TimerBlock)) {
                timerPositions.remove(packed);
                host.cleanupPos(pos);
                continue;
            }

            ensureTimerNode(pos);

            // Update inhibit input (BACK powers pause)
            int backPower = host.readInputPowerFromSide(pos, host.backDir(state));
            NetworkNode node = host.graph().getNode(host.netPos(pos));
            if (node instanceof TimerNode t) {
                t.setInhibited(backPower > 0);

                // refresh period from store (player may have changed)
                int period = host.delayStore().getDelayTicks(host.netPos(pos), TIMER_DEFAULT_TICKS);
                t.setPeriodTicks(period);
            }

            // always dirty so pulse can apply
            host.markDirty(packed);
        }
    }

    void applyOutputs() {
        if (timerPositions.isEmpty()) return;

        for (long packed : new HashSet<>(timerPositions)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = host.level().getBlockState(pos);
            if (!(state.getBlock() instanceof TimerBlock)) continue;

            NetPos np = host.netPos(pos);
            NetworkNode node = host.graph().getNode(np);
            if (!(node instanceof TimerNode t)) continue;

            boolean desiredOut = t.singleOut() == SignalValue.ON;
            boolean currentOut = state.getValue(TimerBlock.POWERED);

            if (desiredOut != currentOut) {
                BlockState newState = state.setValue(TimerBlock.POWERED, desiredOut);
                host.level().setBlock(pos, newState, 3);

                // Timer outputs: FRONT + LEFT + RIGHT (no output on back)
                Direction front = host.frontDir(newState);
                Direction left = front.getCounterClockWise();
                Direction right = front.getClockWise();

                host.level().updateNeighborsAt(pos.relative(front), newState.getBlock());
                host.level().updateNeighborsAt(pos.relative(left), newState.getBlock());
                host.level().updateNeighborsAt(pos.relative(right), newState.getBlock());

                host.level().updateNeighbourForOutputSignal(pos.relative(front), newState.getBlock());
                host.level().updateNeighbourForOutputSignal(pos.relative(left), newState.getBlock());
                host.level().updateNeighbourForOutputSignal(pos.relative(right), newState.getBlock());
            }

            host.setLastOut(packed, desiredOut);
        }
    }

    private void ensureTimerNode(BlockPos pos) {
        NetPos np = host.netPos(pos);
        NetworkNode existing = host.graph().getNode(np);
        if (existing instanceof TimerNode) return;

        host.ensureDelayStore();
        host.graph().putNode(np, new TimerNode(np, host.delayStore()));
    }
}
