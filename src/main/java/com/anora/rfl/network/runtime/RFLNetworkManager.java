package com.anora.rfl.network.runtime;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.core.block.NotGateBlock;
import com.anora.rfl.core.block.RepeaterBlock;
import com.anora.rfl.core.block.common.GroundRotatableBlock;
import com.anora.rfl.network.FileDelayStore;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkGraph;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.node.InverterNode;
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

    /** RedPower ladder (ticks). */
    public static final int[] REDPOWER_DELAYS = { 1, 2, 3, 4, 8, 16, 32, 64, 128 };

    /** Must match block code. */
    private static final boolean FRONT_IS_FACING = true;

    /** Flip off later. */
    private static final boolean DEBUG_CHAT = true;

    private final ServerLevel level;
    private final NetworkGraph graph = new NetworkGraph();

    private final Set<Long> repeaterPositions = new HashSet<>();
    private final Set<Long> notGatePositions = new HashSet<>();

    private final Map<Long, Boolean> lastInput = new HashMap<>();
    private final Map<Long, Boolean> lastOut = new HashMap<>();

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

    // ---------------------------------------------------------------------
    // Delay config API (used by repeater right-click)
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

        // Process inputs (edges) for both block types
        processInputsForRepeaters();
        processInputsForNotGates();

        if (dirty.isEmpty()) return;

        // Run logic
        graph.tick();
        graph.settleUntilStable(64);

        // Apply outputs
        applyOutputsForRepeaters();
        applyOutputsForNotGates();

        // Keep dirty only for repeaters that are mid-transition
        shrinkDirty();
    }

    private void processInputsForRepeaters() {
        if (repeaterPositions.isEmpty()) return;

        long[] positions = repeaterPositions.stream().mapToLong(Long::longValue).toArray();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof RepeaterBlock)) {
                repeaterPositions.remove(packed);
                dirty.remove(packed);
                lastInput.remove(packed);
                lastOut.remove(packed);
                graph.removeNode(netPos(pos));
                continue;
            }

            ensureRepeaterNode(pos);

            int backPower = readBackInputPower(state, pos);
            boolean hasInput = backPower > 0;

            Boolean prev = lastInput.get(packed);
            if (prev == null || prev != hasInput) {
                lastInput.put(packed, hasInput);
                dirty.add(packed);

                if (DEBUG_CHAT) {
                    int delayTicks = delayStore.getDelayTicks(netPos(pos), REDPOWER_DELAYS[0]);
                    int step = delayTicksToIndex(delayTicks);
                    broadcastNearby(pos, Component.literal(
                            "[RFL] Repeater @" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                                    + " input=" + (hasInput ? "ON" : "OFF")
                                    + " backPower=" + backPower
                                    + " BACK=" + backDir(state)
                                    + " FRONT=" + frontDir(state)
                                    + " delay=" + delayTicks + "t step " + step + "/9"
                    ));
                }
            }

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
                dirty.remove(packed);
                lastInput.remove(packed);
                lastOut.remove(packed);
                graph.removeNode(netPos(pos));
                continue;
            }

            ensureInverterNode(pos);

            int backPower = readBackInputPower(state, pos);
            boolean hasInput = backPower > 0;

            Boolean prev = lastInput.get(packed);
            if (prev == null || prev != hasInput) {
                lastInput.put(packed, hasInput);
                dirty.add(packed);

                if (DEBUG_CHAT) {
                    broadcastNearby(pos, Component.literal(
                            "[RFL] NOT @" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                                    + " input=" + (hasInput ? "ON" : "OFF")
                                    + " backPower=" + backPower
                                    + " BACK=" + backDir(state)
                                    + " FRONT=" + frontDir(state)
                    ));
                }
            }

            graph.setExternalSingleIn(netPos(pos), hasInput ? SignalValue.ON : SignalValue.OFF);
        }
    }

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

                if (DEBUG_CHAT) {
                    broadcastNearby(pos, Component.literal("[RFL] Repeater @" + pos + " applied " + (desiredOut ? "ON" : "OFF")));
                }
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

                if (DEBUG_CHAT) {
                    broadcastNearby(pos, Component.literal("[RFL] NOT @" + pos + " applied " + (desiredOut ? "ON" : "OFF")));
                }
            }

            lastOut.put(packed, desiredOut);
        }
    }

    private void shrinkDirty() {
        Set<Long> stillDirty = new HashSet<>();

        // Repeaters can be mid-transition (delay), keep them dirty if input != output
        for (long packed : repeaterPositions) {
            boolean in = lastInput.getOrDefault(packed, false);
            boolean out = lastOut.getOrDefault(packed, false);
            if (in != out) stillDirty.add(packed);
        }

        // NOT gates are combinational; no need to keep dirty once applied
        dirty.clear();
        dirty.addAll(stillDirty);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

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

    private int readBackInputPower(BlockState logicState, BlockPos logicPos) {
        Direction back = backDir(logicState);
        BlockPos neighbor = logicPos.relative(back);
        Direction towardThis = back.getOpposite();

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
}