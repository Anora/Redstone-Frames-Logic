package com.anora.rfl.network.runtime;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.core.block.BufferGateBlock;
import com.anora.rfl.core.block.NotGateBlock;
import com.anora.rfl.core.block.RepeaterBlock;
import com.anora.rfl.core.block.common.GroundRotatableBlock;
import com.anora.rfl.network.FileDelayStore;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkGraph;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.node.BufferGateNode;
import com.anora.rfl.network.node.InverterNode;
import com.anora.rfl.network.node.RepeaterNode;
import com.anora.rfl.network.util.DelayStore;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class RFLNetworkManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean DEBUG_REPEATER = Boolean.getBoolean("rfl.debugRepeater");

    private static final Map<ServerLevel, RFLNetworkManager> INSTANCES = new HashMap<>();

    public static RFLNetworkManager get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, RFLNetworkManager::new);
    }

    public static void clear(ServerLevel level) {
        INSTANCES.remove(level);
    }

    /** RedPower delay ladder (ticks). */
    public static final int[] REDPOWER_DELAYS = { 1, 2, 3, 4, 8, 16, 32, 64, 128 };

    /** Must match the manager. */
    private static final boolean FRONT_IS_FACING = true;

    private final ServerLevel level;
    private final NetworkGraph graph = new NetworkGraph();

    private final Set<Long> repeaterPositions = new HashSet<>();
    private final Set<Long> notGatePositions = new HashSet<>();
    private final Set<Long> bufferGatePositions = new HashSet<>();

    private final Map<Long, Boolean> lastInput = new HashMap<>();
    private final Map<Long, Boolean> lastOut = new HashMap<>();
    private final Set<Long> dirty = new HashSet<>();

    // IMPORTANT: runtimes expect to be able to access this (some use host.delayStore directly).
    // So it must NOT be private.
    DelayStore delayStore;
    boolean delayStoreLoaded = false;

    private final TwoInputGatesRuntime twoInputRuntime;

    private final TimerRuntime timerRuntime;
    private final ToggleLatchRuntime toggleLatchRuntime;
    private final SequencerRuntime sequencerRuntime;
    private final RSLatchRuntime rslatchRuntime;

    // Debug de-spam: only print when something changes
    private final Map<Long, Integer> dbgLastCountdown = new HashMap<>();
    private final Map<Long, SignalValue> dbgLastPending = new HashMap<>();
    private final Map<Long, SignalValue> dbgLastSeen = new HashMap<>();
    private final Map<Long, SignalValue> dbgLastOut = new HashMap<>();

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

        this.timerRuntime = new TimerRuntime(this);
        this.toggleLatchRuntime = new ToggleLatchRuntime(this);
        this.sequencerRuntime = new SequencerRuntime(this);
        this.rslatchRuntime = new RSLatchRuntime(this);
    }

    // ---------------------------------------------------------------------
    // Host API (runtimes rely on these names + signatures)
    // ---------------------------------------------------------------------

    ServerLevel level() { return level; }
    NetworkGraph graph() { return graph; }

    DelayStore delayStore() {
        ensureDelayStore();
        return delayStore;
    }

    void markDirty(long packed) { dirty.add(packed); }
    void markDirty(BlockPos pos) { dirty.add(pos.asLong()); }

    void clearLastOut(long packed) { lastOut.remove(packed); }
    void setLastOut(long packed, boolean value) { lastOut.put(packed, value); }

    void cleanupPos(BlockPos pos) {
        long packed = pos.asLong();
        dirty.remove(packed);
        lastInput.remove(packed);
        lastOut.remove(packed);
        repeaterPositions.remove(packed);
        notGatePositions.remove(packed);
        bufferGatePositions.remove(packed);
        graph.removeNode(netPos(pos));
    }

    // ---------------------------------------------------------------------
    // Placement / break hooks
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

        dbgLastCountdown.remove(p);
        dbgLastPending.remove(p);
        dbgLastSeen.remove(p);
        dbgLastOut.remove(p);
    }

    public void onNotGatePlaced(BlockPos pos) {
        long p = pos.asLong();
        notGatePositions.add(p);

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

    public void onToggleLatchPlaced(BlockPos pos) { toggleLatchRuntime.onPlaced(pos); }
    public void onToggleLatchBroken(BlockPos pos) { toggleLatchRuntime.onBroken(pos); }

    public void onRSLatchPlaced(BlockPos pos) { rslatchRuntime.onPlaced(pos); }
    public void onRSLatchBroken(BlockPos pos) { rslatchRuntime.onBroken(pos); }

    public void onTimerPlaced(BlockPos pos) { timerRuntime.onPlaced(pos); }
    public void onTimerBroken(BlockPos pos) { timerRuntime.onBroken(pos); }

    public void onSequencerPlaced(BlockPos pos) { sequencerRuntime.onPlaced(pos); }
    public void onSequencerBroken(BlockPos pos) { sequencerRuntime.onBroken(pos); }

    // Block interaction helpers used by blocks
    public void cycleTimerPeriod(BlockPos pos) { timerRuntime.cyclePeriod(pos, false); }
    public void cycleTimerPeriod(BlockPos pos, boolean reverse) { timerRuntime.cyclePeriod(pos, reverse); }

    public void cycleSequencerStep(BlockPos pos) { sequencerRuntime.cycleStep(pos, false); }
    public void cycleSequencerStep(BlockPos pos, boolean reverse) { sequencerRuntime.cycleStep(pos, reverse); }

    public void toggleLatchManual(BlockPos pos) { toggleLatchRuntime.toggleManual(pos); }
    public void toggleLatchManual(BlockPos pos, boolean ignored) { toggleLatchRuntime.toggleManual(pos); }

    // Two input gates (this matches your current setup)
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
    // Tick orchestration
    // ---------------------------------------------------------------------

    public void tick() {
        ensureDelayStoreIfNeeded();

        timerRuntime.preTick();
        sequencerRuntime.preTick();

        processInputsForRepeaters();
        processInputsForNotGates();
        processInputsForBufferGates();

        timerRuntime.processInputs();
        toggleLatchRuntime.processInputs();
        rslatchRuntime.processInputs();
        sequencerRuntime.processInputs();
        twoInputRuntime.processInputs();

        boolean needSim = !dirty.isEmpty()
                || !repeaterPositions.isEmpty()
                || timerRuntime.needsTick()
                || sequencerRuntime.needsTick();

        if (!needSim) return;

        graph.tick();
        graph.settleUntilStable(64);

        // ✅ IMPORTANT CHANGE:
        // Repeaters can change output when their countdown hits 0 without new input edges,
        // so they might not be "dirty". We MUST apply outputs for ALL repeaters.
        applyOutputsForRepeatersAll();

        applyOutputsForNotGates();
        applyOutputsForBufferGates();

        timerRuntime.applyOutputs();
        toggleLatchRuntime.applyOutputs();
        rslatchRuntime.applyOutputs();
        sequencerRuntime.applyOutputs();
        twoInputRuntime.applyOutputs();

        if (DEBUG_REPEATER) debugRepeaters();

        shrinkDirty();
    }

    private void shrinkDirty() {
        Set<Long> stillDirty = new HashSet<>();
        timerRuntime.addStillDirty(stillDirty);
        sequencerRuntime.addStillDirty(stillDirty);

        dirty.clear();
        dirty.addAll(stillDirty);
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

    boolean edgeChanged(long packed, boolean newVal) {
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

    private void applyOutputsForRepeatersAll() {
        if (repeaterPositions.isEmpty()) return;

        for (long packed : repeaterPositions) {
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
                notifyRedstone(pos, newState);
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
                notifyRedstone(pos, newState);
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
                notifyRedstone(pos, newState);
            }

            lastOut.put(packed, desiredOut);
        }
    }

    // ---------------------------------------------------------------------
    // Node ensure
    // ---------------------------------------------------------------------

    void ensureRepeaterNode(BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing instanceof RepeaterNode) return;

        ensureDelayStore();
        graph.putNode(np, new RepeaterNode(np, delayStore));
    }

    void ensureInverterNode(BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing instanceof InverterNode) return;

        ensureDelayStore();
        graph.putNode(np, new InverterNode(np, delayStore));
    }

    void ensureBufferNode(BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing instanceof BufferGateNode) return;

        ensureDelayStore();
        graph.putNode(np, new BufferGateNode(np, delayStore));
    }

    // ---------------------------------------------------------------------
    // Delay store
    // ---------------------------------------------------------------------

    void ensureDelayStoreIfNeeded() {
        if (delayStoreLoaded) return;
        ensureDelayStore();
    }

    void ensureDelayStore() {
        if (delayStore != null) return;

        Path dir = level.getServer().getWorldPath(LevelResource.ROOT);
        delayStore = new FileDelayStore(dir.resolve("rfl_delays.dat"));
        delayStore.load();
        delayStoreLoaded = true;
    }

    // ---------------------------------------------------------------------
    // NetPos + direction helpers
    // ---------------------------------------------------------------------

    NetPos netPos(BlockPos pos) {
        int dim = level.dimension().location().hashCode();
        return new NetPos(pos.getX(), pos.getY(), pos.getZ(), dim);
    }

    Direction frontDir(BlockState state) {
        Direction facing = state.getValue(GroundRotatableBlock.FACING);
        return FRONT_IS_FACING ? facing : facing.getOpposite();
    }

    Direction backDir(BlockState state) {
        return frontDir(state).getOpposite();
    }

    Direction leftDir(BlockState state) {
        return frontDir(state).getCounterClockWise();
    }

    Direction rightDir(BlockState state) {
        return frontDir(state).getClockWise();
    }

    int readInputPowerFromSide(BlockPos pos, Direction side) {
        BlockPos neighbor = pos.relative(side);
        Direction towardThis = side.getOpposite();

        int weak = level.getSignal(neighbor, towardThis);
        int direct = level.getDirectSignal(neighbor, towardThis);
        int best = level.getBestNeighborSignal(neighbor);

        return Math.max(Math.max(weak, direct), best);
    }

    private void notifyRedstone(BlockPos pos, BlockState state) {
        Direction front = frontDir(state);
        Direction back = backDir(state);

        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(pos.relative(front), state.getBlock());
        level.updateNeighborsAt(pos.relative(back), state.getBlock());

        level.updateNeighbourForOutputSignal(pos, state.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(front), state.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(back), state.getBlock());
    }

    // ---------------------------------------------------------------------
    // Repeater debug
    // ---------------------------------------------------------------------

    private void debugRepeaters() {
        if (!DEBUG_REPEATER) return;
        if (delayStore == null) return;

        for (long packed : repeaterPositions) {
            NetPos np = netPos(BlockPos.of(packed));
            DelayStore.RepeaterState st = delayStore.getRepeaterState(np);
            if (st == null) continue;

            int cd = st.countdown();
            SignalValue pending = st.pendingTarget();
            SignalValue seen = st.lastSeenInput();
            SignalValue out = st.singleOut();

            Integer lastCd = dbgLastCountdown.get(packed);
            SignalValue lastP = dbgLastPending.get(packed);
            SignalValue lastS = dbgLastSeen.get(packed);
            SignalValue lastO = dbgLastOut.get(packed);

            boolean changed = lastCd == null || lastCd != cd
                    || lastP == null || lastP != pending
                    || lastS == null || lastS != seen
                    || lastO == null || lastO != out;

            if (changed) {
                BlockPos pos = BlockPos.of(packed);
                int delay = delayStore.getDelayTicks(np, 8);
                boolean mgrIn = lastInput.getOrDefault(packed, false);
                boolean mgrOut = lastOut.getOrDefault(packed, false);

                String msg = "[RFL][RepeaterDBG] " + pos +
                        " delay=" + delay +
                        " cd=" + cd +
                        " seen=" + seen +
                        " pending=" + pending +
                        " out=" + out +
                        " mgrIn=" + mgrIn +
                        " mgrOut=" + mgrOut;

                LOGGER.info(msg);
                System.out.println(msg);

                dbgLastCountdown.put(packed, cd);
                dbgLastPending.put(packed, pending);
                dbgLastSeen.put(packed, seen);
                dbgLastOut.put(packed, out);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Repeater delay helpers (used by RepeaterBlock UI)
    // ---------------------------------------------------------------------

    public int getRepeaterDelayTicks(BlockPos pos) {
        ensureDelayStore();
        return delayStore.getDelayTicks(netPos(pos), REDPOWER_DELAYS[0]);
    }

    public void setRepeaterDelayTicks(BlockPos pos, int ticks) {
        ensureDelayStore();

        int idx = delayTicksToIndex(ticks);
        int clamped = indexToDelayTicks(idx);

        NetPos np = netPos(pos);
        delayStore.clearRepeaterState(np);
        delayStore.setDelayTicks(np, clamped);
        delayStore.save();

        dirty.add(pos.asLong());
    }

    public int delayTicksToIndex(int ticks) {
        int bestIdx = 1;
        int bestDiff = Integer.MAX_VALUE;

        for (int i = 0; i < REDPOWER_DELAYS.length; i++) {
            int diff = Math.abs(REDPOWER_DELAYS[i] - ticks);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestIdx = i + 1;
            }
        }
        return bestIdx;
    }

    public int indexToDelayTicks(int index) {
        int i = Math.max(1, Math.min(REDPOWER_DELAYS.length, index)) - 1;
        return REDPOWER_DELAYS[i];
    }
}
