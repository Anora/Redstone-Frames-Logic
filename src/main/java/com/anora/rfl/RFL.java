// File: src/main/java/com/anora/rfl/RFL.java
package com.anora.rfl;

import com.anora.rfl.core.init.RFLBlocks;
import com.anora.rfl.core.init.RFLItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(RFL.MODID)
public class RFL {
    public static final String MODID = "rfl";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RFL(IEventBus modBus, ModContainer modContainer) {
        // Register ALL deferred registers here
        RFLBlocks.BLOCKS.register(modBus);
        RFLItems.ITEMS.register(modBus);

        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
