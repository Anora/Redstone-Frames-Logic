package com.anora.rfl.network.runtime;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.core.block.RepeaterBlock;
import com.anora.rfl.core.block.common.GroundRotatableBlock;
import com.anora.rfl.network.FileDelayStore;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkGraph;
import com.anora.rfl.network.NetworkNode;
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

    /** RedPower ladder (ticks). */
    public static final int[] REDPOWER_DELAYS = { 1, 2, 3, 4, 8, 16, 32, 64, 128 };

    /** MUST match RepeaterBlock.java */
    private static final boolean FRONT_IS_FACING = true;

    /** Flip to false when you’re done debugging. */
    private static final boolean DEBUG_CHAT = false;

    private final ServerLevel level;
    private final NetworkGraph graph = new NetworkGraph();

    // repeaters tracked by placement/break events
    private final Set<Long> repeaterPositions = new HashSet<>();

    // last known input/output per repeater to detect edges and avoid spam
    private final Map<Long, Boolean> lastInput = new HashMap<>();
    private final Map<Long, Boolean> lastOut = new HashMap<>();

    // repeaters that might still be transitioning (delay pending)
    private final Set<Long> dirty = new HashSet<>();

    private DelayStore delayStore;
    private boolean delayStoreLoaded = false;

    private RFLNetworkManager(ServerLevel level) {
        this.level = level;
    }

    // ---------------------------------------------------------------------
    // Placement hooks (called from your RFLGameEvents)
    // ---------------------------------------------------------------------

    public void onRepeaterPlaced(BlockPos pos) {
        long packed = pos.asLong();
        repeaterPositions.add(packed);

        ensureDelayStore();
        ensureRepeaterNode(pos);

        // Default delay if none stored
        NetPos np = netPos(pos);
        int current = delayStore.getDelayTicks(np, REDPOWER_DELAYS[0]);
        if (current <= 0) {
            delayStore.setDelayTicks(np, REDPOWER_DELAYS[0]);
            delayStore.save();
        }

        lastInput.remove(packed);
        lastOut.remove(packed);
        dirty.add(packed);
    }

    public void onRepeaterBroken(BlockPos pos) {
        long packed = pos.asLong();
        repeaterPositions.remove(packed);
        dirty.remove(packed);
        lastInput.remove(packed);
        lastOut.remove(packed);

        graph.removeNode(netPos(pos));

        if (delayStore != null) {
            delayStore.clearRepeaterState(netPos(pos));
            delayStore.save();
        }
    }

    // ---------------------------------------------------------------------
    // Delay config (called by RepeaterBlock right-click)
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
    // Main tick (called each server tick)
    // ---------------------------------------------------------------------

    public void tick() {
        ensureDelayStore();

        if (repeaterPositions.isEmpty()) return;

        long[] positions = repeaterPositions.stream().mapToLong(Long::longValue).toArray();

        // 1) Read inputs and inject into graph. Mark dirty only on input edges.
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof RepeaterBlock)) {
                // replaced without break event (commands etc.)
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
                                    + " FACING=" + state.getValue(GroundRotatableBlock.FACING)
                                    + " delay=" + delayTicks + "t step " + step + "/9"
                    ));
                }
            }

            graph.setExternalSingleIn(netPos(pos), hasInput ? SignalValue.ON : SignalValue.OFF);
        }

        // If nothing is transitioning and no edges happened, skip simulation work.
        if (dirty.isEmpty()) return;

        // 2) Run the node simulation.
        graph.tick();                 // drives RepeaterNode countdown
        graph.settleUntilStable(64);  // propagates changes

        // 3) Mirror node outputs -> blockstate POWERED and notify neighbors.
        Set<Long> stillDirty = new HashSet<>();

        for (long packed : dirty) {
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
                    broadcastNearby(pos, Component.literal(
                            "[RFL] Repeater @" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                                    + " applied " + (desiredOut ? "ON" : "OFF")
                    ));
                }
            }

            // Track “still transitioning”: if input != output, delay is in flight.
            boolean input = lastInput.getOrDefault(packed, false);
            if (input != desiredOut) {
                stillDirty.add(packed);
            }

            lastOut.put(packed, desiredOut);
        }

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

    /**
     * Robustly reads input power from the BACK neighbor (toward this repeater).
     * Uses weak + direct + bestNeighbor to handle dust/torch/lever variations.
     */
    private int readBackInputPower(BlockState repeaterState, BlockPos repeaterPos) {
        Direction back = backDir(repeaterState);
        BlockPos neighbor = repeaterPos.relative(back);

        // The direction we ask the neighbor about is “toward the repeater”
        Direction towardRepeater = back.getOpposite();

        int weak = level.getSignal(neighbor, towardRepeater);
        int direct = level.getDirectSignal(neighbor, towardRepeater);
        int best = level.getBestNeighborSignal(neighbor);

        return Math.max(Math.max(weak, direct), best);
    }

    private void notifyRedstoneBothSides(BlockPos pos, BlockState state) {
        Direction front = frontDir(state);
        Direction back = backDir(state);

        // General neighbor updates
        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(pos.relative(front), state.getBlock());
        level.updateNeighborsAt(pos.relative(back), state.getBlock());

        // Output-signal updates (dust is picky)
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

    public static void clear(ServerLevel level) {
        INSTANCES.remove(level);
    }
}
