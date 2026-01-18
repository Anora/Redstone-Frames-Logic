package com.anora.rfl.network.runtime;

import com.anora.rfl.core.SignalValue;
import com.anora.rfl.core.block.AndGateBlock;
import com.anora.rfl.core.block.NandGateBlock;
import com.anora.rfl.core.block.NorGateBlock;
import com.anora.rfl.core.block.OrGateBlock;
import com.anora.rfl.core.block.XnorGateBlock;
import com.anora.rfl.core.block.XorGateBlock;
import com.anora.rfl.core.block.common.GroundRotatableBlock;
import com.anora.rfl.core.block.common.LogicGateBlock;
import com.anora.rfl.network.NetPos;
import com.anora.rfl.network.NetworkGraph;
import com.anora.rfl.network.NetworkNode;
import com.anora.rfl.network.TwoInputNode;
import com.anora.rfl.network.node.AndGateNode;
import com.anora.rfl.network.node.NandGateNode;
import com.anora.rfl.network.node.NorGateNode;
import com.anora.rfl.network.node.OrGateNode;
import com.anora.rfl.network.node.XnorGateNode;
import com.anora.rfl.network.node.XorGateNode;
import com.anora.rfl.network.util.DelayStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

final class TwoInputGatesRuntime {

    enum GateType {
        AND,
        OR,
        NAND,
        NOR,
        XOR,
        XNOR
    }

    private static final boolean FRONT_IS_FACING = true;

    private final ServerLevel level;
    private final NetworkGraph graph;
    private final DelayStoreProvider delayStoreProvider;

    private final Set<Long> dirty;
    private final Map<Long, Boolean> lastOut;

    private final EnumMap<GateType, GateSpec> specs = new EnumMap<>(GateType.class);

    TwoInputGatesRuntime(ServerLevel level,
                         NetworkGraph graph,
                         DelayStoreProvider delayStoreProvider,
                         Set<Long> dirty,
                         Map<Long, Boolean> lastOut) {

        this.level = level;
        this.graph = graph;
        this.delayStoreProvider = delayStoreProvider;
        this.dirty = dirty;
        this.lastOut = lastOut;

        specs.put(GateType.AND, new GateSpec(
                AndGateBlock.class,
                AndGateNode.class,
                (np, ds) -> new AndGateNode(np, ds)
        ));

        specs.put(GateType.OR, new GateSpec(
                OrGateBlock.class,
                OrGateNode.class,
                (np, ds) -> new OrGateNode(np, ds)
        ));

        specs.put(GateType.NAND, new GateSpec(
                NandGateBlock.class,
                NandGateNode.class,
                (np, ds) -> new NandGateNode(np, ds)
        ));

        specs.put(GateType.NOR, new GateSpec(
                NorGateBlock.class,
                NorGateNode.class,
                (np, ds) -> new NorGateNode(np, ds)
        ));

        specs.put(GateType.XOR, new GateSpec(
                XorGateBlock.class,
                XorGateNode.class,
                (np, ds) -> new XorGateNode(np, ds)
        ));

        specs.put(GateType.XNOR, new GateSpec(
                XnorGateBlock.class,
                XnorGateNode.class,
                (np, ds) -> new XnorGateNode(np, ds)
        ));
    }

    void onPlaced(GateType type, BlockPos pos) {
        GateSpec spec = specs.get(type);
        long p = pos.asLong();

        spec.positions.add(p);

        delayStoreProvider.ensureDelayStore();
        ensureNode(spec, pos);

        spec.lastKey.remove(p);
        lastOut.remove(p);
        dirty.add(p);
    }

    void onBroken(GateType type, BlockPos pos) {
        GateSpec spec = specs.get(type);
        long p = pos.asLong();

        spec.positions.remove(p);
        dirty.remove(p);

        spec.lastKey.remove(p);
        lastOut.remove(p);

        graph.removeNode(netPos(pos));
    }

    void processInputs() {
        for (GateSpec spec : specs.values()) {
            processInputsForSpec(spec);
        }
    }

    void applyOutputs() {
        for (GateSpec spec : specs.values()) {
            applyOutputsForSpec(spec);
        }
    }

    private void processInputsForSpec(GateSpec spec) {
        if (spec.positions.isEmpty()) return;

        long[] positions = spec.positions.stream().mapToLong(Long::longValue).toArray();
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!spec.blockClass.isInstance(state.getBlock())) {
                spec.positions.remove(packed);
                cleanupPos(packed, pos, spec);
                continue;
            }

            delayStoreProvider.ensureDelayStore();
            ensureNode(spec, pos);

            NetPos np = netPos(pos);
            NetworkNode base = graph.getNode(np);
            if (!(base instanceof TwoInputNode two)) continue;

            Direction front = frontDir(state);
            Direction left = front.getCounterClockWise();
            Direction right = front.getClockWise();

            SignalValue a = readPowerAsSignal(pos, left);
            SignalValue b = readPowerAsSignal(pos, right);

            two.setInputs(a, b);

            graph.setExternalSingleIn(np, SignalValue.OFF);

            int key = keyOf(a, b);
            Integer prev = spec.lastKey.get(packed);
            spec.lastKey.put(packed, key);

            if (prev == null || prev != key || !lastOut.containsKey(packed)) {
                dirty.add(packed);
            }
        }
    }

    private void applyOutputsForSpec(GateSpec spec) {
        for (long packed : new HashSet<>(dirty)) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = level.getBlockState(pos);

            if (!spec.blockClass.isInstance(state.getBlock())) continue;

            NetPos np = netPos(pos);
            NetworkNode node = graph.getNode(np);
            if (!spec.nodeClass.isInstance(node)) continue;

            boolean desiredOut = node.singleOut() == SignalValue.ON;

            if (!state.hasProperty(LogicGateBlock.POWERED)) continue;

            boolean currentOut = state.getValue(LogicGateBlock.POWERED);

            if (desiredOut != currentOut) {
                BlockState newState = state.setValue(LogicGateBlock.POWERED, desiredOut);
                level.setBlock(pos, newState, 3);
                notifyRedstoneNeighbors(pos, newState);
            }

            lastOut.put(packed, desiredOut);
        }
    }

    private void ensureNode(GateSpec spec, BlockPos pos) {
        NetPos np = netPos(pos);
        NetworkNode existing = graph.getNode(np);
        if (existing != null && spec.nodeClass.isInstance(existing)) return;

        DelayStore ds = delayStoreProvider.getDelayStore();
        graph.putNode(np, spec.nodeFactory.apply(np, ds));
    }

    private void cleanupPos(long packed, BlockPos pos, GateSpec spec) {
        dirty.remove(packed);
        spec.lastKey.remove(packed);
        lastOut.remove(packed);
        graph.removeNode(netPos(pos));
    }

    private SignalValue readPowerAsSignal(BlockPos logicPos, Direction side) {
        int p = readInputPowerFromSide(logicPos, side);
        return p > 0 ? SignalValue.ON : SignalValue.OFF;
    }

    private int readInputPowerFromSide(BlockPos logicPos, Direction side) {
        BlockPos neighbor = logicPos.relative(side);
        Direction towardThis = side.getOpposite();

        int weak = level.getSignal(neighbor, towardThis);
        int direct = level.getDirectSignal(neighbor, towardThis);
        int best = level.getBestNeighborSignal(neighbor);

        return Math.max(Math.max(weak, direct), best);
    }

    private void notifyRedstoneNeighbors(BlockPos pos, BlockState state) {
        Direction front = frontDir(state);
        Direction back = backDir(state);

        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighborsAt(pos.relative(front), state.getBlock());
        level.updateNeighborsAt(pos.relative(back), state.getBlock());

        level.updateNeighbourForOutputSignal(pos, state.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(front), state.getBlock());
        level.updateNeighbourForOutputSignal(pos.relative(back), state.getBlock());
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

    private static int keyOf(SignalValue a, SignalValue b) {
        return (a == SignalValue.ON ? 1 : 0) | (b == SignalValue.ON ? 2 : 0);
    }

    interface DelayStoreProvider {
        void ensureDelayStore();
        DelayStore getDelayStore();
    }

    private static final class GateSpec {
        final Class<? extends Block> blockClass;
        final Class<? extends NetworkNode> nodeClass;
        final BiFunction<NetPos, DelayStore, NetworkNode> nodeFactory;

        final Set<Long> positions = new HashSet<>();
        final Map<Long, Integer> lastKey = new HashMap<>();

        GateSpec(Class<? extends Block> blockClass,
                 Class<? extends NetworkNode> nodeClass,
                 BiFunction<NetPos, DelayStore, NetworkNode> nodeFactory) {
            this.blockClass = blockClass;
            this.nodeClass = nodeClass;
            this.nodeFactory = nodeFactory;
        }
    }
}