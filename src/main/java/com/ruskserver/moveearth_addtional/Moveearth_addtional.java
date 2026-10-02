package com.ruskserver.moveearth_addtional;

import com.mojang.logging.LogUtils;
import com.ruskserver.moveearth_addtional.config.RegionResourceConfig;
import com.ruskserver.moveearth_addtional.config.DelayedChunkCacheConfig;
import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.config.DiscordBotConfig;
import com.ruskserver.moveearth_addtional.config.TipConfig;
import com.ruskserver.moveearth_addtional.config.MarketConfig;
import com.ruskserver.moveearth_addtional.config.LocalChatConfig;
import com.ruskserver.moveearth_addtional.config.RecoveryDispatchConfig;
import com.ruskserver.moveearth_addtional.config.StartupClientConfig;
import com.ruskserver.moveearth_addtional.config.ConfigFileLayout;
import com.ruskserver.moveearth_addtional.config.CreateIndustryConfig;
import com.ruskserver.moveearth_addtional.config.MekanismBalanceConfig;
import com.ruskserver.moveearth_addtional.config.WaterWheelBalanceConfig;
import com.ruskserver.moveearth_addtional.compat.cbc.CbcReinforcementCompat;
import com.ruskserver.moveearth_addtional.compat.warnautics.WarnauticsReinforcementCompat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Moveearth_addtional.MODID)
public class Moveearth_addtional {
    public static final String MODID = "moveearth_addtional";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Moveearth_addtional(IEventBus modEventBus, ModContainer modContainer) {
        // Before any registerConfig: startup configs are read the moment they are registered.
        ConfigFileLayout.migrateGameDirectories(FMLPaths.CONFIGDIR.get(),
                        FMLPaths.GAMEDIR.get().resolve("defaultconfigs"))
                .forEach(line -> LOGGER.info("[MoveEarth] Config layout: {}", line));
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            // Before registering: a startup config is loaded on the spot.
            modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class,
                    DiscordBotConfig::restrictOwnerAccess);
            modEventBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Reloading.class,
                    DiscordBotConfig::restrictOwnerAccess);
            modContainer.registerConfig(
                    ModConfig.Type.STARTUP,
                    DiscordBotConfig.SPEC,
                    ConfigFileLayout.SERVER_OPS + "discord.toml"
            );
        }
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT,
                    com.ruskserver.moveearth_addtional.client.upscale.UpscaleClientConfig.SPEC,
                    ConfigFileLayout.CLIENT + "upscale.toml");
            modContainer.registerConfig(ModConfig.Type.CLIENT,
                    com.ruskserver.moveearth_addtional.client.particles.CbcParticleConfig.SPEC,
                    ConfigFileLayout.CLIENT + "particles.toml");
            modContainer.registerConfig(ModConfig.Type.CLIENT,
                    com.ruskserver.moveearth_addtional.client.scope.ScopePipConfig.SPEC,
                    ConfigFileLayout.CLIENT + "scope-pip.toml");
            modContainer.registerConfig(
                    ModConfig.Type.CLIENT,
                    StartupClientConfig.SPEC,
                    ConfigFileLayout.CLIENT + "startup.toml"
            );
        }
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                DelayedChunkCacheConfig.SPEC,
                ConfigFileLayout.WORLD + "dcc.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                RegionResourceConfig.SPEC,
                ConfigFileLayout.WORLD + "regions.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                AeronauticsSwivelConfig.SPEC,
                ConfigFileLayout.WORLD + "aeronautics.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                S2TerritoryConfig.SPEC,
                ConfigFileLayout.WORLD + "s2-territory.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                TipConfig.SPEC,
                ConfigFileLayout.WORLD + "tips.toml"
        );
        modContainer.registerConfig(ModConfig.Type.SERVER, MarketConfig.SPEC,
                ConfigFileLayout.WORLD + "market.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER,
                com.ruskserver.moveearth_addtional.config.EconomyGuardConfig.SPEC,
                ConfigFileLayout.WORLD + "economy-guard.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER, LocalChatConfig.SPEC,
                ConfigFileLayout.WORLD + "chat.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER,
                com.ruskserver.moveearth_addtional.config.ScheduleConfig.SPEC,
                ConfigFileLayout.WORLD + "schedule.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER,
                com.ruskserver.moveearth_addtional.nether.NetherGateConfig.SPEC,
                ConfigFileLayout.WORLD + "nether-gate.toml");
        modContainer.registerConfig(ModConfig.Type.SERVER,
                com.ruskserver.moveearth_addtional.compat.sentry.SentryTurretConfig.SPEC,
                ConfigFileLayout.WORLD + "sentry.toml");
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                RecoveryDispatchConfig.SPEC,
                ConfigFileLayout.WORLD + "recovery-dispatch.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                WaterWheelBalanceConfig.SPEC,
                ConfigFileLayout.WORLD + "water-wheels.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                CreateIndustryConfig.SPEC,
                ConfigFileLayout.WORLD + "create-industry.toml"
        );
        modContainer.registerConfig(
                ModConfig.Type.SERVER,
                MekanismBalanceConfig.SPEC,
                ConfigFileLayout.WORLD + "mekanism.toml"
        );

        CbcReinforcementCompat.registerIfPresent();
        WarnauticsReinforcementCompat.registerIfPresent();
        com.ruskserver.moveearth_addtional.advancement.PlayerReviveAdvancementCompat.registerIfPresent();

        // Register Sounds
        ModSounds.SOUND_EVENTS.register(modEventBus);
        com.ruskserver.moveearth_addtional.advancement.ModCriteria.TRIGGERS.register(modEventBus);

        // Register Blocks, Items, BlockEntities, CreativeModeTabs
        com.ruskserver.moveearth_addtional.block.ModBlocks.BLOCKS.register(modEventBus);
        com.ruskserver.moveearth_addtional.nether.NetherGateRegistry.register(modEventBus);
        com.ruskserver.moveearth_addtional.item.ModItems.ITEMS.register(modEventBus);
        com.ruskserver.moveearth_addtional.region.worldgen.RegionWorldgen
                .PLACEMENT_MODIFIERS.register(modEventBus);
        com.ruskserver.moveearth_addtional.region.worldgen.RegionWorldgen
                .BIOME_MODIFIERS.register(modEventBus);
        com.ruskserver.moveearth_addtional.warehouse.WarehouseWorldgen.STRUCTURES.register(modEventBus);
        com.ruskserver.moveearth_addtional.warehouse.WarehouseWorldgen.PIECES.register(modEventBus);
        com.ruskserver.moveearth_addtional.warehouse.WarehouseWorldgen.PLACEMENTS.register(modEventBus);
        com.ruskserver.moveearth_addtional.block.entity.ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        com.ruskserver.moveearth_addtional.item.ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        com.ruskserver.moveearth_addtional.entity.ModEntities.ENTITY_TYPES.register(modEventBus);

        // Register the terrain density function type and load tiles before worldgen starts
        com.ruskserver.moveearth_addtional.terrain.TerrainRegistration.register(modEventBus);
        com.ruskserver.moveearth_addtional.worldgen.WorldgenRegistration.register(modEventBus);
        com.ruskserver.moveearth_addtional.terrain.TerrainEvents.register();

        // Register Config
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.ruskserver.moveearth_addtional.oxygen.OxygenConfig.SPEC,
                ConfigFileLayout.WORLD + "oxygen.toml");
    }
}
