package com.anora.rfl.network.runtime;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.core.block.BufferGateBlock;
import com.anora.rfl.core.block.NotGateBlock;
import com.anora.rfl.core.block.RepeaterBlock;
import com.anora.rfl.core.block.TimerBlock;
import com.anora.rfl.core.block.common.GroundRotatableBlock;
import com.anora.rfl.network.FileDelayStore;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkGraph;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.node.BufferGateNode;
import com.anora.rfl.network.node.InverterNode;
import com.anora.rfl.network.node.RepeaterNode;
import com.anora.rfl.network.node.TimerNode;
import com.anora.rfl.network.util.DelayStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RFLNetworkManager {

    private static final Map<ServerLevel, RFLNetworkManager> INSTANCES = new HashMap<>();

    public static RFLNetworkManager get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, RFLNetworkManager::new);
    }

    public static void clear(ServerLevel level) {
        INSTANCES.remove(level);
    }

    /** RedPower ladder (ticks). */
    public static final int[] REDPOWER_DELAYS = { 1, 2, 3, 4, 8, 16, 32, 64, 128 };

    private static final boolean FRONT_IS_FACING = true;

    // Timer rules
    private static final int TIMER_MIN_TICKS = 4;       // 0.2s
    private static final int TIMER_DEFAULT_TICKS = 40;  // 2s
    private static final int TIMER_MAX_TICKS = 1200;    // 60s

    private final ServerLevel level;
    private final NetworkGraph graph = new NetworkGraph();

    private final Set<Long> repeaterPositions = new HashSet<>();
    private final Set<Long> notGatePositions = new HashSet<>();
    private final Set<Long> bufferGatePositions = new HashSet<>();
    private final Set<Long> timerPositions = new HashSet<>();

    private final Map<Long, Boolean> lastInput = new HashMap<>();
    private final Map<Long, Boolean> lastOut = new HashMap<>();
    private final Set<Long> dirty = new HashSet<>();

    private DelayStore delayStore;
    private boolean delayStoreLoaded = false;

    private final TwoInputGatesRuntime twoInputRuntime;

    private long lastDayTime = Long.MIN_VALUE;

    private RFLNetworkManager(ServerLevel level) {
        this.level = level;

        this.twoInputRuntime = new TwoInputGatesRuntime(
                level,
                graph,
                new TwoInputGatesRuntime.DelayStoreProvider() {
                    @Override public void ensureDelayStore() { RFLNetworkManager.this.ensureDelayStore(); }
                    @Override public DelayStore getDelayStore() { return RFLNetworkManager.this.delayStore; }
                },
                dirty,
                lastOut
        );
    }

    // ---------------------------------------------------------------------
    // Placement hooks
    // ---------------------------------------------------------------------

    public void onRepeaterPlaced(BlockPos pos) {
        long p = pos.asLong();
        repeaterPositions.add(p);

        ensureDelayStore();
        ensureRepeaterNode(pos);

        NetPos np = netPos(pos);
        int current = delayStore.getDelayTicks(np, REDPOWER_DELAYS[0]);
        if (current <= 0) {
            delayStore.setDelayTicks(np, REDPOWER_DELAYS[0]);
            delayStore.save();
        }

        lastInput.remove(p);
        lastOut.remove(p);
        dirty.add(p);
    }

    public void onRepeaterBroken(BlockPos pos) {
        long p = pos.asLong();
        repeaterPositions.remove(p);
        dirty.remove(p);
        lastInput.remove(p);
        lastOut.remove(p);

        graph.removeNode(netPos(pos));

        if (delayStore != null) {
            delayStore.clearRepeaterState(netPos(pos));
            delayStore.save();
        }
    }

    public void onNotGatePlaced(BlockPos pos) {
        long p = pos.asLong();
        notGatePositions.add(p);

        ensureDelayStore();
        ensureInverterNode(pos);

        lastInput.remove(p);
        lastOut.remove(p);
        dirty.add(p);
    }

    public void onNotGateBroken(BlockPos pos) {
        long p = pos.asLong();
        notGatePositions.remove(p);
        dirty.remove(p);
        lastInput.remove(p);
        lastOut.remove(p);

        graph.removeNode(netPos(pos));
    }

    public void onBufferGatePlaced(BlockPos pos) {
        long p = pos.asLong();
        bufferGatePositions.add(p);

        ensureDelayStore();
        ensureBufferNode(pos);

        lastInput.remove(p);
        lastOut.remove(p);
        dirty.add(p);
    }

    public void onBufferGateBroken(BlockPos pos) {
        long p = pos.asLong();
        bufferGatePositions.remove(p);
        dirty.remove(p);
        lastInput.remove(p);
        lastOut.remove(p);

        graph.removeNode(netPos(pos));
    }

    public void onTimerPlaced(BlockPos pos) {
        long p = pos.asLong();
        timerPositions.add(p);

        ensureDelayStore();
        ensureTimerNode(pos);

        NetPos np = netPos(pos);
        int current = delayStore.getDelayTicks(np, TIMER_DEFAULT_TICKS);
        if (current <= 0) {
            delayStore.setDelayTicks(np, TIMER_DEFAULT_TICKS);
            delayStore.save();
            current = TIMER_DEFAULT_TICKS;
        }

        NetworkNode node = graph.getNode(np);
        if (node instanceof TimerNode t) {
            t.setPeriodTicks(current);
            t.resyncToWorldTime(level.getDayTime());
        }

        lastOut.remove(p);
        dirty.add(p);
    }

    public void onTimerBroken(BlockPos pos) {
        long p = pos.asLong();
        timerPositions.remove(p);
        dirty.remove(p);
        lastOut.remove(p);

        graph.removeNode(netPos(pos));
    }

    // ---------------------------------------------------------------------
    // Two-input gate hooks (delegated)
    // ---------------------------------------------------------------------

    public void onAndGatePlaced(BlockPos pos) { twoInputRuntime.onPlaced(TwoInputGatesRuntime.GateType.AND, pos); }
    public void onAndGateBroken(BlockPos pos) { twoInputRuntime.onBroken(TwoInputGatesRuntime.GateType.AND, pos); }

    public void onOrGatePlaced(BlockPos pos) { twoInputRuntime.onPlaced(TwoInputGatesRuntime.GateType.OR, pos); }
    public void onOrGateBroken(BlockPos pos) { twoInputRuntime.onBroken(TwoInputGatesRuntime.GateType.OR, pos); }

    public void onNandGatePlaced(BlockPos pos) { twoInputRuntime.onPlaced(TwoInputGatesRuntime.GateType.NAND, pos); }
    public void onNandGateBroken(BlockPos pos) { twoInputRuntime.onBroken(TwoInputGatesRuntime.GateType.NAND, pos); }

    public void onNorGatePlaced(BlockPos pos) { twoInputRuntime.onPlaced(TwoInputGatesRuntime.GateType.NOR, pos); }
    public void onNorGateBroken(BlockPos pos) { twoInputRuntime.onBroken(TwoInputGatesRuntime.GateType.NOR, pos); }

    public void onXorGatePlaced(BlockPos pos) { twoInputRuntime.onPlaced(TwoInputGatesRuntime.GateType.XOR, pos); }
    public void onXorGateBroken(BlockPos pos) { twoInputRuntime.onBroken(TwoInputGatesRuntime.GateType.XOR, pos); }

    public void onXnorGatePlaced(BlockPos pos) { twoInputRuntime.onPlaced(TwoInputGatesRuntime.GateType.XNOR, pos); }
    public void onXnorGateBroken(BlockPos pos) { twoInputRuntime.onBroken(TwoInputGatesRuntime.GateType.XNOR, pos); }

    // ---------------------------------------------------------------------
    // Repeater delay API (unchanged behavior)
    // ---------------------------------------------------------------------

    public int getRepeaterDelayTicks(BlockPos pos) {
        ensureDelayStore();
        return delayStore.getDelayTicks(netPos(pos), REDPOWER_DELAYS[0]);
    }

    public void setRepeaterDelayTicks(BlockPos pos, int ticks) {
        ensureDelayStore();
        delayStore.setDelayTicks(netPos(pos), ticks);
        delayStore.save();
        dirty.add(pos.asLong());
    }

    public int delayTicksToIndex(int ticks) {
        for (int i = 0; i < REDPOWER_DELAYS.length; i++) {
            if (REDPOWER_DELAYS[i] == ticks) return i + 1;
        }
        return 1;
    }

    public int indexToDelayTicks(int index1to9) {
        int idx = Math.max(1, Math.min(9, index1to9)) - 1;
        return REDPOWER_DELAYS[idx];
    }

    // ---------------------------------------------------------------------
    // Timer period API
    // ---------------------------------------------------------------------

    public void cycleTimerPeriod(BlockPos pos, boolean reverse) {
        ensureDelayStore();
        NetPos np = netPos(pos);

        int current = delayStore.getDelayTicks(np, TIMER_DEFAULT_TICKS);
        int step = 4;
        int next = reverse ? (current - step) : (current + step);

        if (next < TIMER_MIN_TICKS) next = TIMER_MIN_TICKS;
        if (next > TIMER_MAX_TICKS) next = TIMER_MIN_TICKS;

        delayStore.setDelayTicks(np, next);
        delayStore.save();

        NetworkNode node = graph.getNode(np);
        if (node instanceof TimerNode t) {
            t.setPeriodTicks(next);
            // RP2-ish: changing period resets phase
            t.resyncToWorldTime(level.getDayTime());
        }

        long packed = pos.asLong();
        lastOut.remove(packed);
        dirty.add(packed);
    }

    // ---------------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------------

    public void tick() {
        ensureDelayStoreIfNeeded();

        // RP2 behavior: timers reset phase if dayTime jumps (sleep, /time)
        long day = level.getDayTime();
        if (lastDayTime != Long.MIN_VALUE && day != lastDayTime + 1) {
            resyncAllTimers(day);
        }
        lastDayTime = day;

        processInputsForRepeaters();
        processInputsForNotGates();
        processInputsForBufferGates();
        processTimers(); // inhibition + keep ticking
        twoInputRuntime.processInputs();

        boolean needSim = !dirty.isEmpty() || !timerPositions.isEmpty();
        if (!needSim) return;

        graph.tick();
        graph.settleUntilStable(64);

        applyOutputsForRepeaters();
        applyOutputsForNotGates();
        applyOutputsForBufferGates();
        applyOutputsForTimers();
        twoInputRuntime.applyOutputs();

        shrinkDirty();
    }

    private void resyncAllTimers(long dayTime) {
        if (timerPositions.isEmpty()) return;
        ensureDelayStore();

        for (long packed : timerPositions) {
            BlockPos pos = BlockPos.of(packed);
            ensureTimerNode(pos);

            NetPos np = netPos(pos);
            int period = delayStore.getDelayTicks(np, TIMER_DEFAULT_TICKS);

            NetworkNode node = graph.getNode(np);
            if (node instanceof TimerNode t) {
                t.setPeriodTicks(period);
                t.resyncToWorldTime(dayTime);
            }

            dirty.add(packed);
        }
    }

    // ---------------------------------------------------------------------
    // Input processing
    // ---------------------------------------------------------------------

    private void processInputsForRepeaters() {
        if (repeaterPositions.isEmpty()) return;

        long[] positions = repeaterPositions.stream().mapToLong(Long::longValue).toArray();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof RepeaterBlock)) {
                repeaterPositions.remove(packed);
                cleanupPos(pos);
                continue;
            }

            ensureRepeaterNode(pos);

            int backPower = readInputPowerFromSide(pos, backDir(state));
            boolean hasInput = backPower > 0;

            edgeChanged(packed, hasInput);
            graph.setExternalSingleIn(netPos(pos), hasInput ? SignalValue.ON : SignalValue.OFF);
        }
    }

    private void processInputsForNotGates() {
        if (notGatePositions.isEmpty()) return;

        long[] positions = notGatePositions.stream().mapToLong(Long::longValue).toArray();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof NotGateBlock)) {
                notGatePositions.remove(packed);
                cleanupPos(pos);
                continue;
            }

            ensureInverterNode(pos);

            int backPower = readInputPowerFromSide(pos, backDir(state));
            boolean hasInput = backPower > 0;

            edgeChanged(packed, hasInput);
            graph.setExternalSingleIn(netPos(pos), hasInput ? SignalValue.ON : SignalValue.OFF);
        }
    }

    private void processInputsForBufferGates() {
        if (bufferGatePositions.isEmpty()) return;

        long[] positions = bufferGatePositions.stream().mapToLong(Long::longValue).toArray();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof BufferGateBlock)) {
                bufferGatePositions.remove(packed);
                cleanupPos(pos);
                continue;
            }

            ensureBufferNode(pos);

            int backPower = readInputPowerFromSide(pos, backDir(state));
            boolean hasInput = backPower > 0;

            edgeChanged(packed, hasInput);
            graph.setExternalSingleIn(netPos(pos), hasInput ? SignalValue.ON : SignalValue.OFF);
        }
    }

    /**
     * Timers have no normal input, but they DO have an inhibit input (BACK).
     * Also: keep timers dirty so pulse state is applied.
     */
    private void processTimers() {
        if (timerPositions.isEmpty()) return;

        for (long packed : timerPositions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof TimerBlock)) {
                timerPositions.remove(packed);
                cleanupPos(pos);
                continue;
            }

            ensureTimerNode(pos);

            NetPos np = netPos(pos);
            NetworkNode node = graph.getNode(np);
            if (!(node instanceof TimerNode t)) continue;

            // Update period from store (player may have changed it)
            int period = delayStore.getDelayTicks(np, TIMER_DEFAULT_TICKS);
            if (t.periodTicks() != period) {
                t.setPeriodTicks(period);
            }

            // Inhibit input: BACK powered => pause timer
            int backPower = readInputPowerFromSide(pos, backDir(state));
            t.setInhibited(backPower > 0);

            // Always apply (pulses are time-driven)
            dirty.add(packed);
        }
    }

    private boolean edgeChanged(long packed, boolean newVal) {
        Boolean prev = lastInput.get(packed);
        if (prev == null || prev != newVal) {
            lastInput.put(packed, newVal);
            dirty.add(packed);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Output apply
    // ---------------------------------------------------------------------

    private void applyOutputsForRepeaters() {
        for (long packed : new HashSet<>(dirty)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof RepeaterBlock)) continue;

            NetworkNode node = graph.getNode(netPos(pos));
            if (!(node instanceof RepeaterNode)) continue;

            boolean desiredOut = node.singleOut() == SignalValue.ON;
            boolean currentOut = state.getValue(RepeaterBlock.POWERED);

            if (desiredOut != currentOut) {
                BlockState newState = state.setValue(RepeaterBlock.POWERED, desiredOut);
                level.setBlock(pos, newState, 3);
                notifyRedstoneBothSides(pos, newState);
            }

            lastOut.put(packed, desiredOut);
        }
    }

    private void applyOutputsForNotGates() {
        for (long packed : new HashSet<>(dirty)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof NotGateBlock)) continue;

            NetworkNode node = graph.getNode(netPos(pos));
            if (!(node instanceof InverterNode)) continue;

            boolean desiredOut = node.singleOut() == SignalValue.ON;
            boolean currentOut = state.getValue(NotGateBlock.POWERED);

            if (desiredOut != currentOut) {
                BlockState newState = state.setValue(NotGateBlock.POWERED, desiredOut);
                level.setBlock(pos, newState, 3);
                notifyRedstoneBothSides(pos, newState);
            }

            lastOut.put(packed, desiredOut);
        }
    }

    private void applyOutputsForBufferGates() {
        for (long packed : new HashSet<>(dirty)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof BufferGateBlock)) continue;

            NetworkNode node = graph.getNode(netPos(pos));
            if (!(node instanceof BufferGateNode)) continue;

            boolean desiredOut = node.singleOut() == SignalValue.ON;
            boolean currentOut = state.getValue(BufferGateBlock.POWERED);

            if (desiredOut != currentOut) {
                BlockState newState = state.setValue(BufferGateBlock.POWERED, desiredOut);
                level.setBlock(pos, newState, 3);
                notifyThreeOutputs(pos, newState);
            }

            lastOut.put(packed, desiredOut);
        }
    }

    private void applyOutputsForTimers() {
        for (long packed : new HashSet<>(dirty)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof TimerBlock)) continue;

            NetPos np = netPos(pos);
            NetworkNode node = graph.getNode(np);
            if (!(node instanceof TimerNode t)) continue;

            boolean desiredOut = t.singleOut() == SignalValue.ON;
            boolean currentOut = state.getValue(TimerBlock.POWERED);

            if (desiredOut != currentOut) {
                BlockState newState = state.setValue(TimerBlock.POWERED, desiredOut);
                level.setBlock(pos, newState, 3);
                notifyThreeOutputs(pos, newState);
            }

            lastOut.put(packed, desiredOut);
        }
    }

    private void notifyThreeOutputs(BlockPos pos, BlockState state) {
        Direction front = frontDir(state);
        Direction left = front.getCounterClockWise();
        Direction right = front.getClockWise();

        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(pos.relative(front), state.getBlock());
        level.updateNeighborsAt(pos.relative(left), state.getBlock());
        level.updateNeighborsAt(pos.relative(right), state.getBlock());

        level.updateNeighbourForOutputSignal(pos, state.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(front), state.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(left), state.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(right), state.getBlock());
    }

    private void shrinkDirty() {
        Set<Long> stillDirty = new HashSet<>();

        // Repeater behavior unchanged: stay dirty while input != output
        for (long packed : repeaterPositions) {
            boolean in = lastInput.getOrDefault(packed, false);
            boolean out = lastOut.getOrDefault(packed, false);
            if (in != out) stillDirty.add(packed);
        }

        // Timers must keep applying pulses
        stillDirty.addAll(timerPositions);

        dirty.clear();
        dirty.addAll(stillDirty);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void cleanupPos(BlockPos pos) {
        long packed = pos.asLong();
        dirty.remove(packed);
        lastInput.remove(packed);
        lastOut.remove(packed);
        repeaterPositions.remove(packed);
        notGatePositions.remove(packed);
        bufferGatePositions.remove(packed);
        timerPositions.remove(packed);
        graph.removeNode(netPos(pos));
    }

    private void ensureRepeaterNode(BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing instanceof RepeaterNode) return;

        ensureDelayStore();
        graph.putNode(np, new RepeaterNode(np, delayStore));
    }

    private void ensureInverterNode(BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing instanceof InverterNode) return;

        ensureDelayStore();
        graph.putNode(np, new InverterNode(np, delayStore));
    }

    private void ensureBufferNode(BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing instanceof BufferGateNode) return;

        ensureDelayStore();
        graph.putNode(np, new BufferGateNode(np, delayStore));
    }

    private void ensureTimerNode(BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing instanceof TimerNode) return;

        ensureDelayStore();
        graph.putNode(np, new TimerNode(np, delayStore));
    }

    private int readInputPowerFromSide(BlockPos logicPos, Direction side) {
        BlockPos neighbor = logicPos.relative(side);
        Direction towardThis = side.getOpposite();

        int weak = level.getSignal(neighbor, towardThis);
        int direct = level.getDirectSignal(neighbor, towardThis);
        int best = level.getBestNeighborSignal(neighbor);

        return Math.max(Math.max(weak, direct), best);
    }

    private void notifyRedstoneBothSides(BlockPos pos, BlockState state) {
        Direction front = frontDir(state);
        Direction back = backDir(state);

        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(pos.relative(front), state.getBlock());
        level.updateNeighborsAt(pos.relative(back), state.getBlock());

        level.updateNeighbourForOutputSignal(pos, state.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(front), state.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(back), state.getBlock());
    }

    private void ensureDelayStoreIfNeeded() {
        if (!repeaterPositions.isEmpty() || !timerPositions.isEmpty()) ensureDelayStore();
    }

    void ensureDelayStore() {
        if (delayStore != null) return;

        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT);
        Path file = worldRoot.resolve("rfl_delays.properties");

        delayStore = new FileDelayStore(file);
        graph.setDelayStore(delayStore);

        if (!delayStoreLoaded) {
            try { delayStore.load(); } catch (Exception ignored) {}
            delayStoreLoaded = true;
        }
    }

    private NetPos netPos(BlockPos pos) {
        int dim = level.dimension().location().hashCode();
        return new NetPos(pos.getX(), pos.getY(), pos.getZ(), dim);
    }

    private static Direction frontDir(BlockState state) {
        Direction facing = state.getValue(GroundRotatableBlock.FACING);
        return FRONT_IS_FACING ? facing : facing.getOpposite();
    }

    private static Direction backDir(BlockState state) {
        return frontDir(state).getOpposite();
    }
}

