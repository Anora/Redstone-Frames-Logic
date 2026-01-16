package com.anora.rfl;

import com.anora.rfl.core.block.RepeaterBlock;
import com.anora.rfl.network.runtime.RFLNetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = RFL.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RFLGameEvents {

    private RFLGameEvents() {}

    /**
     * Tick the RFL logic network once per server tick for every loaded dimension.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            RFLNetworkManager.get(level).tick();
        }
    }

    /**
     * Track repeater placement so the manager can create the node + persist delay state.
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (event.getPlacedBlock().getBlock() instanceof RepeaterBlock) {
            RFLNetworkManager.get(level).onRepeaterPlaced(event.getPos());
        }
    }

    /**
     * Track repeater break so the manager can remove the node + clear persisted state.
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        if (event.getState().getBlock() instanceof RepeaterBlock) {
            RFLNetworkManager.get(level).onRepeaterBroken(event.getPos());
        }
    }

    /**
     * Cleanup per-level manager instance when a level unloads (important in dev + server stops).
     */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RFLNetworkManager.clear(level);
        }
    }

}
