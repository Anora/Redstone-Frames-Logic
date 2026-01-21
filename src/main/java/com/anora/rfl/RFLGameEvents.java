package com.anora.rfl;

import com.anora.rfl.core.block.*;
import com.anora.rfl.network.runtime.RFLNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = RFL.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RFLGameEvents {

    private RFLGameEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            RFLNetworkManager.get(level).tick();
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (event.getPlacedBlock().getBlock() instanceof RepeaterBlock) {
            RFLNetworkManager.get(level).onRepeaterPlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof NotGateBlock) {
            RFLNetworkManager.get(level).onNotGatePlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof AndGateBlock) {
            RFLNetworkManager.get(level).onAndGatePlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof OrGateBlock) {
            RFLNetworkManager.get(level).onOrGatePlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof NandGateBlock) {
            RFLNetworkManager.get(level).onNandGatePlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof NorGateBlock) {
            RFLNetworkManager.get(level).onNorGatePlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof XorGateBlock) {
            RFLNetworkManager.get(level).onXorGatePlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof XnorGateBlock) {
            RFLNetworkManager.get(level).onXnorGatePlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof BufferGateBlock) {
            RFLNetworkManager.get(level).onBufferGatePlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof TimerBlock) {
            RFLNetworkManager.get(level).onTimerPlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof ToggleLatchBlock) {
            RFLNetworkManager.get(level).onToggleLatchPlaced(event.getPos());
        } else if (event.getPlacedBlock().getBlock() instanceof RSLatchBlock) {
            RFLNetworkManager.get(level).onRSLatchPlaced(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (event.getState().getBlock() instanceof RepeaterBlock) {
            RFLNetworkManager.get(level).onRepeaterBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof NotGateBlock) {
            RFLNetworkManager.get(level).onNotGateBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof AndGateBlock) {
            RFLNetworkManager.get(level).onAndGateBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof OrGateBlock) {
            RFLNetworkManager.get(level).onOrGateBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof NandGateBlock) {
            RFLNetworkManager.get(level).onNandGateBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof NorGateBlock) {
            RFLNetworkManager.get(level).onNorGateBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof XorGateBlock) {
            RFLNetworkManager.get(level).onXorGateBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof XnorGateBlock) {
            RFLNetworkManager.get(level).onXnorGateBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof BufferGateBlock) {
            RFLNetworkManager.get(level).onBufferGateBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof TimerBlock) {
            RFLNetworkManager.get(level).onTimerBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof ToggleLatchBlock) {
            RFLNetworkManager.get(level).onToggleLatchBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof RSLatchBlock) {
            RFLNetworkManager.get(level).onRSLatchBroken(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        RFLNetworkManager mgr = RFLNetworkManager.get(level);

        int minY = level.getMinY();
        int maxY = level.getMaxY();

        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int y = minY; y < maxY; y++) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    mp.set(baseX + dx, y, baseZ + dz);
                    var state = chunk.getBlockState(mp);

                    if (state.getBlock() instanceof RepeaterBlock) {
                        mgr.onRepeaterPlaced(mp.immutable());
                    } else if (state.getBlock() instanceof NotGateBlock) {
                        mgr.onNotGatePlaced(mp.immutable());
                    } else if (state.getBlock() instanceof AndGateBlock) {
                        mgr.onAndGatePlaced(mp.immutable());
                    } else if (state.getBlock() instanceof OrGateBlock) {
                        mgr.onOrGatePlaced(mp.immutable());
                    } else if (state.getBlock() instanceof NandGateBlock) {
                        mgr.onNandGatePlaced(mp.immutable());
                    } else if (state.getBlock() instanceof NorGateBlock) {
                        mgr.onNorGatePlaced(mp.immutable());
                    } else if (state.getBlock() instanceof XorGateBlock) {
                        mgr.onXorGatePlaced(mp.immutable());
                    } else if (state.getBlock() instanceof XnorGateBlock) {
                        mgr.onXnorGatePlaced(mp.immutable());
                    } else if (state.getBlock() instanceof BufferGateBlock) {
                        mgr.onBufferGatePlaced(mp.immutable());
                    } else if (state.getBlock() instanceof TimerBlock) {
                        mgr.onTimerPlaced(mp.immutable());
                    } else if (state.getBlock() instanceof ToggleLatchBlock) {
                        mgr.onToggleLatchPlaced(mp.immutable());
                    } else if (state.getBlock() instanceof RSLatchBlock) {
                        mgr.onRSLatchPlaced(mp.immutable());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RFLNetworkManager.clear(level);
        }
    }
}
