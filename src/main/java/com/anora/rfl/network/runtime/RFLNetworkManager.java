package com.anora.rfl.network.runtime;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.core.block.AndGateBlock;
import com.anora.rfl.core.block.NandGateBlock;
import com.anora.rfl.core.block.NotGateBlock;
import com.anora.rfl.core.block.OrGateBlock;
import com.anora.rfl.core.block.RepeaterBlock;
import com.anora.rfl.core.block.common.GroundRotatableBlock;
import com.anora.rfl.network.FileDelayStore;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkGraph;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.TwoInputNode;
import com.anora.rfl.network.node.AndGateNode;
import com.anora.rfl.network.node.InverterNode;
import com.anora.rfl.network.node.NandGateNode;
import com.anora.rfl.network.node.OrGateNode;
import com.anora.rfl.network.node.RepeaterNode;
import com.anora.rfl.network.util.DelayStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    public static final int[] REDPOWER_DELAYS = { 1, 2, 3, 4, 8, 16, 32, 64, 128 };
    private static final boolean FRONT_IS_FACING = true;
    private static final boolean DEBUG_CHAT = false;

    private final ServerLevel level;
    private final NetworkGraph graph = new NetworkGraph();

    private final Set<Long> repeaterPositions = new HashSet<>();
    private final Set<Long> notGatePositions = new HashSet<>();
    private final Set<Long> andGatePositions = new HashSet<>();
    private final Set<Long> orGatePositions = new HashSet<>();
    private final Set<Long> nandGatePositions = new HashSet<>();

    private final Map<Long, Boolean> lastInput = new HashMap<>();
    private final Map<Long, Boolean> lastOut = new HashMap<>();

    private final Map<Long, Integer> lastAndKey = new HashMap<>();
    private final Map<Long, Integer> lastOrKey = new HashMap<>();
    private final Map<Long, Integer> lastNandKey = new HashMap<>();

    private final Set<Long> dirty = new HashSet<>();

    private DelayStore delayStore;
    private boolean delayStoreLoaded = false;

    private RFLNetworkManager(ServerLevel level) {
        this.level = level;
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

    public void onAndGatePlaced(BlockPos pos) {
        long p = pos.asLong();
        andGatePositions.add(p);

        ensureDelayStore();
        ensureAndGateNode(pos);

        lastAndKey.remove(p);
        lastOut.remove(p);
        dirty.add(p);
    }

    public void onAndGateBroken(BlockPos pos) {
        long p = pos.asLong();
        andGatePositions.remove(p);
        dirty.remove(p);

        lastAndKey.remove(p);
        lastOut.remove(p);

        graph.removeNode(netPos(pos));
    }

    public void onOrGatePlaced(BlockPos pos) {
        long p = pos.asLong();
        orGatePositions.add(p);

        ensureDelayStore();
        ensureOrGateNode(pos);

        lastOrKey.remove(p);
        lastOut.remove(p);
        dirty.add(p);
    }

    public void onOrGateBroken(BlockPos pos) {
        long p = pos.asLong();
        orGatePositions.remove(p);
        dirty.remove(p);

        lastOrKey.remove(p);
        lastOut.remove(p);

        graph.removeNode(netPos(pos));
    }

    public void onNandGatePlaced(BlockPos pos) {
        long p = pos.asLong();
        nandGatePositions.add(p);

        ensureDelayStore();
        ensureNandGateNode(pos);

        lastNandKey.remove(p);
        lastOut.remove(p);
        dirty.add(p);
    }

    public void onNandGateBroken(BlockPos pos) {
        long p = pos.asLong();
        nandGatePositions.remove(p);
        dirty.remove(p);

        lastNandKey.remove(p);
        lastOut.remove(p);

        graph.removeNode(netPos(pos));
    }

    // ---------------------------------------------------------------------
    // Delay config API (repeater right-click)
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
    // Tick
    // ---------------------------------------------------------------------

    public void tick() {
        ensureDelayStoreIfNeededForRepeaters();

        processInputsForRepeaters();
        processInputsForNotGates();
        processInputsForAndGates();
        processInputsForOrGates();
        processInputsForNandGates();

        if (dirty.isEmpty()) return;

        graph.tick();
        graph.settleUntilStable(64);

        applyOutputsForRepeaters();
        applyOutputsForNotGates();
        applyOutputsForAndGates();
        applyOutputsForOrGates();
        applyOutputsForNandGates();

        shrinkDirty();
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

    private void processInputsForAndGates() {
        if (andGatePositions.isEmpty()) return;

        long[] positions = andGatePositions.stream().mapToLong(Long::longValue).toArray();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof AndGateBlock)) {
                andGatePositions.remove(packed);
                cleanupPos(pos);
                continue;
            }

            ensureAndGateNode(pos);

            NetPos np = netPos(pos);
            NetworkNode base = graph.getNode(np);
            if (!(base instanceof TwoInputNode two)) continue;

            SignalValue a = readLeftInput(state, pos);
            SignalValue b = readRightInput(state, pos);

            two.setInputs(a, b);
            graph.setExternalSingleIn(np, SignalValue.OFF);

            int key = keyOf(a, b);
            Integer prev = lastAndKey.get(packed);
            if (prev == null || prev != key) {
                lastAndKey.put(packed, key);
                dirty.add(packed);
            }
        }
    }

    private void processInputsForOrGates() {
        if (orGatePositions.isEmpty()) return;

        long[] positions = orGatePositions.stream().mapToLong(Long::longValue).toArray();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof OrGateBlock)) {
                orGatePositions.remove(packed);
                cleanupPos(pos);
                continue;
            }

            ensureOrGateNode(pos);

            NetPos np = netPos(pos);
            NetworkNode base = graph.getNode(np);
            if (!(base instanceof TwoInputNode two)) continue;

            SignalValue a = readLeftInput(state, pos);
            SignalValue b = readRightInput(state, pos);

            two.setInputs(a, b);
            graph.setExternalSingleIn(np, SignalValue.OFF);

            int key = keyOf(a, b);
            Integer prev = lastOrKey.get(packed);
            if (prev == null || prev != key) {
                lastOrKey.put(packed, key);
                dirty.add(packed);
            }
        }
    }

    private void processInputsForNandGates() {
        if (nandGatePositions.isEmpty()) return;

        long[] positions = nandGatePositions.stream().mapToLong(Long::longValue).toArray();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof NandGateBlock)) {
                nandGatePositions.remove(packed);
                cleanupPos(pos);
                continue;
            }

            ensureNandGateNode(pos);

            NetPos np = netPos(pos);
            NetworkNode base = graph.getNode(np);
            if (!(base instanceof TwoInputNode two)) continue;

            SignalValue a = readLeftInput(state, pos);
            SignalValue b = readRightInput(state, pos);

            two.setInputs(a, b);
            graph.setExternalSingleIn(np, SignalValue.OFF);

            int key = keyOf(a, b);
            Integer prev = lastNandKey.get(packed);
            if (prev == null || prev != key) {
                lastNandKey.put(packed, key);
                dirty.add(packed);
            }
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

    private void applyOutputsForAndGates() {
        applyTwoInputGateOutputs(AndGateBlock.class, AndGateNode.class);
    }

    private void applyOutputsForOrGates() {
        applyTwoInputGateOutputs(OrGateBlock.class, OrGateNode.class);
    }

    private void applyOutputsForNandGates() {
        applyTwoInputGateOutputs(NandGateBlock.class, NandGateNode.class);
    }

    private <B, N> void applyTwoInputGateOutputs(Class<?> blockClass, Class<?> nodeClass) {
        for (long packed : new HashSet<>(dirty)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!blockClass.isInstance(state.getBlock())) continue;

            NetworkNode node = graph.getNode(netPos(pos));
            if (!nodeClass.isInstance(node)) continue;

            boolean desiredOut = node.singleOut() == SignalValue.ON;

            // ✅ Logic gates use LogicGateBlock.POWERED
            boolean currentOut = state.getValue(
                    com.anora.rfl.core.block.common.LogicGateBlock.POWERED
            );

            if (desiredOut != currentOut) {
                BlockState newState = state.setValue(
                        com.anora.rfl.core.block.common.LogicGateBlock.POWERED,
                        desiredOut
                );
                level.setBlock(pos, newState, 3);
                notifyRedstoneBothSides(pos, newState);
            }

            lastOut.put(packed, desiredOut);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void cleanupPos(BlockPos pos) {
        long packed = pos.asLong();
        dirty.remove(packed);
        lastInput.remove(packed);
        lastOut.remove(packed);
        lastAndKey.remove(packed);
        lastOrKey.remove(packed);
        lastNandKey.remove(packed);
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

    private void ensureAndGateNode(BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing instanceof AndGateNode) return;

        ensureDelayStore();
        graph.putNode(np, new AndGateNode(np, delayStore));
    }

    private void ensureOrGateNode(BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing instanceof OrGateNode) return;

        ensureDelayStore();
        graph.putNode(np, new OrGateNode(np, delayStore));
    }

    private void ensureNandGateNode(BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing instanceof NandGateNode) return;

        ensureDelayStore();
        graph.putNode(np, new NandGateNode(np, delayStore));
    }

    private SignalValue readLeftInput(BlockState state, BlockPos pos) {
        Direction front = frontDir(state);
        Direction left = front.getCounterClockWise();
        int power = readInputPowerFromSide(pos, left);
        return power > 0 ? SignalValue.ON : SignalValue.OFF;
    }

    private SignalValue readRightInput(BlockState state, BlockPos pos) {
        Direction front = frontDir(state);
        Direction right = front.getClockWise();
        int power = readInputPowerFromSide(pos, right);
        return power > 0 ? SignalValue.ON : SignalValue.OFF;
    }

    private static int keyOf(SignalValue a, SignalValue b) {
        return (a == SignalValue.ON ? 1 : 0) | (b == SignalValue.ON ? 2 : 0);
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

    private void broadcastNearby(BlockPos pos, Component msg) {
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distManhattan(pos) <= 64) {
                p.sendSystemMessage(msg);
            }
        }
    }

    private void ensureDelayStoreIfNeededForRepeaters() {
        if (!repeaterPositions.isEmpty()) ensureDelayStore();
    }

    private void ensureDelayStore() {
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

    private void shrinkDirty() {
        Set<Long> stillDirty = new HashSet<>();

        // Repeaters can be mid-transition (delay), so they may need multiple ticks.
        for (long packed : repeaterPositions) {
            boolean in = lastInput.getOrDefault(packed, false);
            boolean out = lastOut.getOrDefault(packed, false);
            if (in != out) stillDirty.add(packed);
        }

        dirty.clear();
        dirty.addAll(stillDirty);
    }

}
