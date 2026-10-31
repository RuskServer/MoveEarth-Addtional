package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.NationRecoverySavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

import static com.ruskserver.moveearth_addtional.s2.territory.CapitalRelocationPolicy.Result;

public final class CapitalRelocationService {
    private CapitalRelocationService() { }

    private static Result authority(ServerPlayer actor, long expectedRevision) {
        var nations = NationSavedData.get(actor.server);
        var nation = nations.nationFor(actor.getUUID()).orElse(null);
        if (nation == null) return Result.OWNER_ONLY;
        var sieges = SiegeSavedData.get(actor.server);
        return CapitalRelocationPolicy.authority(nation.ownerId().equals(actor.getUUID()),
                expectedRevision == nations.revision(), sieges.isNationLocked(nation.id()),
                sieges.isNationSettlementProtected(nation.id())
                        || NationRecoverySavedData.get(actor.server).activeForNation(nation.id()).isPresent(),
                TerritorySavedData.get(actor.server).capitalRelocationRemaining(nation.id(), OpenTimeService.now(actor.server)));
    }

    public static CapitalRelocationView view(ServerPlayer actor, int requestId, Result operation) {
        var nations = NationSavedData.get(actor.server);
        var nation = nations.nationFor(actor.getUUID()).orElse(null);
        if (nation == null || !nation.ownerId().equals(actor.getUUID())) {
            return new CapitalRelocationView(requestId, nations.revision(), CapitalRelocationView.NONE,
                    0, 0, false, key(Result.OWNER_ONLY), java.util.List.of());
        }
        var territories = TerritorySavedData.get(actor.server);
        var capital = CapitalWallService.capital(actor.server, nation.id()).orElse(null);
        UUID capitalId = capital == null ? CapitalRelocationView.NONE : capital.id();
        Result gate = authority(actor, nations.revision());
        var cores = territories.cores().stream().filter(c -> c.nationId().equals(nation.id()))
                .sorted(Comparator.comparing((TerritorySavedData.CoreRecord c) -> c.type() != TerritorySavedData.CoreType.CAPITAL)
                        .thenComparing(c -> c.dimension().toString()).thenComparingInt(c -> c.pos().getX())
                        .thenComparingInt(c -> c.pos().getZ())).limit(CapitalRelocationView.MAX_CORES)
                .map(core -> {
                    Result reason = territories.capitalSwapCandidate(nation.id(), capitalId, core.id());
                    if (reason == Result.READY && (SiegeSavedData.get(actor.server).isCoreSettlementProtected(core.id())
                            || SiegeSavedData.get(actor.server).isCoreSettlementProtected(capitalId))) reason = Result.RECOVERY_LOCKED;
                    if (reason == Result.READY && (!loaded(actor, core) || capital == null || !loaded(actor, capital))) reason = Result.NOT_LOADED;
                    return new CapitalRelocationView.Core(core.id(), core.dimension().toString(), core.pos().getX(),
                            core.pos().getY(), core.pos().getZ(), core.radius(), core.type() == TerritorySavedData.CoreType.CAPITAL, key(reason));
                }).toList();
        return new CapitalRelocationView(requestId, nations.revision(), capitalId,
                territories.capitalRelocationRemaining(nation.id(), OpenTimeService.now(actor.server)),
                com.ruskserver.moveearth_addtional.config.S2TerritoryConfig.capitalRelocationCooldownTicks(),
                gate == Result.READY, key(operation == null ? gate : operation), cores);
    }

    public static Result relocate(ServerPlayer actor, long expectedRevision, UUID capitalId, UUID targetId) {
        Result gate = authority(actor, expectedRevision);
        if (gate != Result.READY) return gate;
        var nation = NationSavedData.get(actor.server).nationFor(actor.getUUID()).orElseThrow();
        var territories = TerritorySavedData.get(actor.server);
        Result candidate = territories.capitalSwapCandidate(nation.id(), capitalId, targetId);
        if (candidate != Result.READY) return candidate;
        var capital = territories.coreById(capitalId).orElseThrow();
        var target = territories.coreById(targetId).orElseThrow();
        var sieges = SiegeSavedData.get(actor.server);
        if (sieges.isCoreSettlementProtected(capitalId) || sieges.isCoreSettlementProtected(targetId)) return Result.RECOVERY_LOCKED;
        if (!loaded(actor, capital) || !loaded(actor, target)) return Result.NOT_LOADED;
        // Recheck the actual walls: ACTIVE can be stale while the delayed closure recheck is pending.
        for (var core : java.util.List.of(capital, target)) {
            if (!TerritoryClosureService.scan(level(actor, core), core.pos()).sealed()) {
                TerritoryClosureRecheckManager.scheduleCheck(actor.server, core);
                return Result.NOT_SEALED;
            }
        }
        Result result = territories.swapCapital(nation.id(), capitalId, targetId, OpenTimeService.now(actor.server));
        if (result != Result.SUCCESS) return result;
        for (UUID id : java.util.List.of(capitalId, targetId)) {
            var core = territories.coreById(id).orElseThrow();
            ((TerritoryCoreBlockEntity) level(actor, core).getBlockEntity(core.pos())).bind(core);
            CapitalWallService.capitalRoleChanged(actor.server, id);
            TerritoryClosureRecheckManager.scheduleCheck(actor.server, core);
        }
        String location = target.dimension() + " " + target.pos().getX() + ", " + target.pos().getY() + ", " + target.pos().getZ();
        for (UUID member : nation.members().keySet()) {
            var player = actor.server.getPlayerList().getPlayer(member);
            if (player != null) player.sendSystemMessage(MoveEarthMessage.success(Component.translatableWithFallback(
                    "message.moveearth_addtional.capital_relocation.completed",
                    player.clientInformation().language().startsWith("ja_")
                            ? "首都を %s に移転しました。旧首都は前哨基地になりました。"
                            : "The capital moved to %s. The old capital is now an outpost.", location)));
        }
        Moveearth_addtional.LOGGER.info("Nation {} capital swapped from {} to {} by {}", nation.id(), capitalId, targetId, actor.getUUID());
        return result;
    }

    private static ServerLevel level(ServerPlayer actor, TerritorySavedData.CoreRecord core) {
        return actor.server.getLevel(ResourceKey.create(Registries.DIMENSION, core.dimension()));
    }

    private static boolean loaded(ServerPlayer actor, TerritorySavedData.CoreRecord core) {
        var level = level(actor, core);
        return level != null && level.hasChunkAt(core.pos())
                && level.getBlockEntity(core.pos()) instanceof TerritoryCoreBlockEntity block
                && core.id().equals(block.coreId()) && core.nationId().equals(block.nationId());
    }

    public static String key(Result result) {
        return "screen.moveearth_addtional.capital_relocation.result." + result.name().toLowerCase(Locale.ROOT);
    }
}
