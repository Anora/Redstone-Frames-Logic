package com.anora.rfl;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = RFL.MODID, dist = Dist.CLIENT)
// IMPORTANT: lifecycle events like FMLClientSetupEvent MUST be on the MOD bus, not the NeoForge bus.
@EventBusSubscriber(modid = RFL.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class RFLClient {
    public RFLClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        RFL.LOGGER.info("HELLO FROM CLIENT SETUP");
        RFL.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
