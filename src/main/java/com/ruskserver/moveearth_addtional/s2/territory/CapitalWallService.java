package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Counts the wall layers of each capital after its closure check comes out sealed, and warns its nation
 * when the thinnest point has fewer than {@link S2TerritoryConfig#capitalWallLayerTarget()}: in the hub,
 * once at login to those who manage reinforcement, and at once when a siege breaks a layer. Only capitals:
 * losing one is what costs a nation its standing, and outposts would multiply the warnings.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class CapitalWallService {
    private static final int CELLS_PER_TICK = 8_192;
    /** Getting more than this far above or below the core counts as having gone over or under the wall. */
    static final int VERTICAL_RANGE = 48;
    private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();

    private CapitalWallService() { }

    static void capitalRoleChanged(MinecraftServer server, UUID coreId) {
        PENDING.remove(coreId);
        CapitalWallSavedData.get(server).remove(coreId);
    }

    private record Pending(ResourceLocation dimension, BlockPos pos, TerritoryWallLayerSearch.Session session) { }

    /** Called with each finished closure check. */
    static void onClosureResult(ServerLevel level, TerritorySavedData.CoreRecord core,
                                TerritoryClosureScanner.Result result) {
        if (core.type() != TerritorySavedData.CoreType.CAPITAL) return;
        switch (result.status()) {
            case SEALED -> PENDING.put(core.id(), new Pending(core.dimension(), core.pos(),
                    TerritoryClosureService.beginWallLayers(level, core.pos(), horizontalRange(core.radius()),
                            VERTICAL_RANGE, S2TerritoryConfig.capitalWallLayerTarget())));
            case OPEN -> {
                PENDING.remove(core.id());
                // An open wall is reported by the closure check itself; this only keeps the hub current.
                CapitalWallSavedData.get(level.getServer()).put(core.id(),
                        new CapitalWallSavedData.Wall(0, false, List.of(), System.currentTimeMillis()));
            }
            default -> { }
        }
    }

    /** Advances one pending count by a bounded amount of work. */
    static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Pending>> iterator = PENDING.entrySet().iterator();
        if (!iterator.hasNext()) return;
        Map.Entry<UUID, Pending> entry = iterator.next();
        TerritoryWallLayerSearch.Result result = entry.getValue().session().advance(CELLS_PER_TICK);
        if (result == null) return;
        iterator.remove();
        if (result.status() != TerritoryWallLayerSearch.Status.COUNTED) return;
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(server)
                .core(entry.getValue().dimension(), entry.getValue().pos()).orElse(null);
        if (core == null || !core.id().equals(entry.getKey())) return;
        List<BlockPos> weak = result.weakPoints().stream()
                .map(cell -> new BlockPos(cell.x(), cell.y(), cell.z())).toList();
        CapitalWallSavedData.Wall wall = new CapitalWallSavedData.Wall(result.layers(), result.atLeast(), weak,
                System.currentTimeMillis());
        Optional<CapitalWallSavedData.Wall> previous = CapitalWallSavedData.get(server).put(core.id(), wall);
        int target = S2TerritoryConfig.capitalWallLayerTarget();
        boolean thinner = previous.isPresent() && !wall.atLeast()
                && (previous.get().atLeast() || wall.layers() < previous.get().layers());
        SiegeSavedData sieges = SiegeSavedData.get(server);
        if (thinner && wall.layers() > 0 && wall.below(target)
                && (sieges.hasNationSiegeOn(core.id()) || sieges.hasRollingSiege(core.id()))) {
            warnNation(server, core, wall, "message.moveearth_addtional.capital_wall.breached", false);
        }
    }

    /**
     * How far out to look: to the edge of the capital's territory, since no wall can be reinforced past it,
     * but never less than the closure check's range nor more than the configured limit.
     */
    static int horizontalRange(int territoryRadiusChunks) {
        int toTerritoryEdge = (Math.max(0, territoryRadiusChunks) + 1) * 16;
        return Math.max(TerritoryClosureScanner.SEARCH_RANGE,
                Math.min(S2TerritoryConfig.capitalWallSearchRange(), toTerritoryEdge));
    }

    /** The capital of {@code nationId}, if it has one. */
    public static Optional<TerritorySavedData.CoreRecord> capital(MinecraftServer server, UUID nationId) {
        return TerritorySavedData.get(server).cores().stream()
                .filter(core -> core.nationId().equals(nationId) && core.type() == TerritorySavedData.CoreType.CAPITAL)
                .findFirst();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (nationId == null) return;
        TerritorySavedData.CoreRecord capital = capital(player.server, nationId).orElse(null);
        if (capital == null || capital.state() == TerritorySavedData.CoreState.CONFIGURING) return;
        CapitalWallSavedData.Wall wall = CapitalWallSavedData.get(player.server).get(capital.id()).orElse(null);
        if (wall == null) {
            // Never counted, as for a capital sealed before counting existed: count it now.
            TerritoryClosureRecheckManager.scheduleCheck(player.server, capital);
            return;
        }
        if (!wall.below(S2TerritoryConfig.capitalWallLayerTarget()) || wall.layers() == 0
                || !nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT)) return;
        player.sendSystemMessage(MoveEarthMessage.warning(message(
                "message.moveearth_addtional.capital_wall.thin", wall)));
    }

    static void clear() {
        PENDING.clear();
    }

    private static void warnNation(MinecraftServer server, TerritorySavedData.CoreRecord core,
                                   CapitalWallSavedData.Wall wall, String key, boolean managersOnly) {
        NationSavedData nations = NationSavedData.get(server);
        Component body = message(key, wall);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!nations.nationIdFor(player.getUUID()).filter(core.nationId()::equals).isPresent()) continue;
            if (managersOnly && !nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT)) continue;
            player.sendSystemMessage(MoveEarthMessage.warning(body));
        }
    }

    private static Component message(String key, CapitalWallSavedData.Wall wall) {
        Component weak = wall.weakPoints().isEmpty() ? Component.empty()
                : Component.translatable("message.moveearth_addtional.capital_wall.weak_point",
                wall.weakPoints().getFirst().getX(), wall.weakPoints().getFirst().getY(),
                wall.weakPoints().getFirst().getZ());
        return Component.translatable(key, wall.layers(), S2TerritoryConfig.capitalWallLayerTarget(), weak);
    }
}
