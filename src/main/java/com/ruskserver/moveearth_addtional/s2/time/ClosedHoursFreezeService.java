package com.ruskserver.moveearth_addtional.s2.time;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ServerSchedule;
import com.ruskserver.moveearth_addtional.config.ScheduleConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Stops the day/night and weather cycles while the dedicated server is closed.
 * Ecliptic Seasons advances its solar term day whenever the overworld's day time
 * passes a day boundary, so stopping the day/night cycle also stops season progression;
 * no dependency on that mod is needed.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class ClosedHoursFreezeService {
    private static MinecraftServer activeServer;
    private static boolean frozen;

    private ClosedHoursFreezeService() { }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // One-time migration from the former gamerule-mutating implementation.
        // Repeating this restoration after a crash is safe: it writes the same original values.
        MinecraftServer server = event.getServer();
        ClosedHoursFreezeSavedData legacy = ClosedHoursFreezeSavedData.get(server);
        if (legacy.frozen()) {
            GameRules rules = server.getGameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(legacy.savedDaylightCycle(), server);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(legacy.savedWeatherCycle(), server);
            // Keep the legacy recovery marker until level.dat contains the restored rules.
            // Otherwise a crash between the two files would recreate the old save-order bug.
            server.saveAllChunks(true, true, true);
            try {
                var persisted = NbtIo.readCompressed(server.getWorldPath(LevelResource.LEVEL_DATA_FILE),
                        NbtAccounter.create(64L * 1024 * 1024));
                if (legacy.matchesRestoredRules(persisted.getCompound("Data").getCompound("GameRules"))) {
                    legacy.release();
                } else {
                    Moveearth_addtional.LOGGER.error("[MoveEarth] Legacy cycle restoration was not saved; retaining recovery record");
                }
            } catch (java.io.IOException | RuntimeException failure) {
                Moveearth_addtional.LOGGER.error("[MoveEarth] Could not verify legacy cycle restoration; retaining recovery record", failure);
            }
        }
        activeServer = server;
        apply(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (activeServer == event.getServer()) {
            activeServer = null;
            frozen = false;
        }
    }

    /** Runtime state only; no gamerule or SavedData write is needed for a transition. */
    public static boolean isFrozen(MinecraftServer server) {
        return activeServer == server && frozen;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 11) return;
        apply(event.getServer());
    }

    private static void apply(MinecraftServer server) {
        boolean next = ClosedHoursFreeze.shouldFreeze(ScheduleConfig.freezeWorldWhileClosed(),
                server.isDedicatedServer(), ServerSchedule.isOpenNow());
        if (next != frozen) {
            frozen = next;
            Moveearth_addtional.LOGGER.info("[MoveEarth] Day/night and weather cycles {}",
                    next ? "paused for closed hours" : "resumed according to operator gamerules");
        }
    }
}
