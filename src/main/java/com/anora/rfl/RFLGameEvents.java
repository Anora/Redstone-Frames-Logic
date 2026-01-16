package com.anora.rfl;

import com.anora.rfl.core.block.NotGateBlock;
import com.anora.rfl.core.block.RepeaterBlock;
import com.anora.rfl.network.runtime.RFLNetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (event.getState().getBlock() instanceof RepeaterBlock) {
            RFLNetworkManager.get(level).onRepeaterBroken(event.getPos());
        } else if (event.getState().getBlock() instanceof NotGateBlock) {
            RFLNetworkManager.get(level).onNotGateBroken(event.getPos());
        }
    }

    /**
     * Key fix: when a chunk loads, scan it for RFL blocks and register them.
     * This makes repeaters/gates work after you reload a world.
     */
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

        // Scan all blocks in the chunk.
        // (We can optimize later using section palettes; this is the simplest correct version.)
        for (int y = minY; y < maxY; y++) {
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    mp.set(baseX + dx, y, baseZ + dz);
                    var state = chunk.getBlockState(mp);

                    if (state.getBlock() instanceof RepeaterBlock) {
                        mgr.onRepeaterPlaced(mp.immutable());
                    } else if (state.getBlock() instanceof NotGateBlock) {
                        mgr.onNotGatePlaced(mp.immutable());
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
