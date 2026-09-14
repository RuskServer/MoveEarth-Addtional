package com.ruskserver.moveearth_addtional;

import com.mojang.logging.LogUtils;
import com.ruskserver.moveearth_addtional.config.DelayedChunkCacheConfig;
import com.ruskserver.moveearth_addtional.config.TpaConfig;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.config.DiscordBotConfig;
import com.ruskserver.moveearth_addtional.config.TipConfig;
import com.ruskserver.moveearth_addtional.compat.cbc.CbcReinforcementCompat;
import com.ruskserver.moveearth_addtional.compat.warnautics.WarnauticsReinforcementCompat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Moveearth_addtional.MODID)
public class Moveearth_addtional {
    public static final String MODID = "moveearth_addtional";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Moveearth_addtional(IEventBus modEventBus, ModContainer modContainer) {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            modContainer.registerConfig(
                    ModConfig.Type.STARTUP,
                    DiscordBotConfig.SPEC,
                    "moveearth_addtional-discord.toml"
            );
        }
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                DelayedChunkCacheConfig.SPEC,
                "moveearth_addtional-dcc.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                TpaConfig.SPEC,
                "moveearth_addtional-tpa.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                S2TerritoryConfig.SPEC,
                "moveearth_addtional-s2-territory.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                TipConfig.SPEC,
                "moveearth_addtional-tips.toml"
        );

        CbcReinforcementCompat.registerIfPresent();
        WarnauticsReinforcementCompat.registerIfPresent();

        // Register Sounds
        ModSounds.SOUND_EVENTS.register(modEventBus);

        // Register Blocks, Items, BlockEntities, CreativeModeTabs
        com.ruskserver.moveearth_addtional.block.ModBlocks.BLOCKS.register(modEventBus);
        com.ruskserver.moveearth_addtional.item.ModItems.ITEMS.register(modEventBus);
        com.ruskserver.moveearth_addtional.block.entity.ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        com.ruskserver.moveearth_addtional.item.ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        com.ruskserver.moveearth_addtional.entity.ModEntities.ENTITY_TYPES.register(modEventBus);

        // Register Config
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.ruskserver.moveearth_addtional.oxygen.OxygenConfig.SPEC);
    }
}
