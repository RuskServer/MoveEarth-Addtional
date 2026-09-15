package com.ruskserver.moveearth_addtional.s2.technology;

import com.ruskserver.moveearth_addtional.network.S2C_TechnologySnapshotPacket;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TechnologyViewService {
    private TechnologyViewService() { }

    public static void send(ServerPlayer player) {
        send(player, true);
    }

    public static void sync(ServerPlayer player) {
        send(player, false);
    }

    public static void syncNation(ServerPlayer actor, ResourceLocation technologyId) {
        TechnologyDefinition definition = TechnologyDefinitions.INSTANCE.get(technologyId).orElse(null);
        if (definition == null || definition.scope() == TechnologyDefinition.Scope.PERSONAL) {
            sync(actor); return;
        }
        NationSavedData data = NationSavedData.get(actor.server);
        UUID nationId = data.nationIdFor(actor.getUUID()).orElse(null);
        if (nationId == null) { sync(actor); return; }
        data.nation(nationId).ifPresent(nation -> nation.members().keySet().forEach(memberId -> {
            ServerPlayer online = actor.server.getPlayerList().getPlayer(memberId);
            if (online != null) sync(online);
        }));
    }

    private static void send(ServerPlayer player, boolean openScreen) {
        NationSavedData nationData = NationSavedData.get(player.server);
        UUID nationId = nationData.nationIdFor(player.getUUID()).orElse(null);
        NationTechnologySavedData technologyData = NationTechnologySavedData.get(player.server);
        if (nationId != null) technologyData.refreshComputed(player.server, nationId);
        Set<ResourceLocation> completed = technologyData.completedFor(player.getUUID(), nationId);
        List<TechnologySnapshot.Node> nodes = TechnologyDefinitions.INSTANCE.all().stream().map(definition -> {
            Map<String, Long> values = technologyData.objectiveProgress(player.getUUID(), nationId, definition);
            TechnologySnapshot.State state;
            if (definition.scope() == TechnologyDefinition.Scope.NATION && nationId == null) state = TechnologySnapshot.State.LOCKED;
            else if (completed.contains(definition.id())) state = TechnologySnapshot.State.COMPLETED;
            else if (!TechnologyProgressPolicy.prerequisitesMet(definition, completed)) state = TechnologySnapshot.State.LOCKED;
            else if (values.values().stream().anyMatch(value -> value > 0L)) state = TechnologySnapshot.State.IN_PROGRESS;
            else state = TechnologySnapshot.State.AVAILABLE;
            return new TechnologySnapshot.Node(definition.id(), definition.scope(), definition.category(), definition.chapter(),
                    definition.titleKey(), definition.descriptionKey(), definition.icon(), definition.x(), definition.y(), state,
                    technologyData.tracked(player.getUUID(), nationId, definition),
                    definition.objectives().stream().map(objective -> new TechnologySnapshot.Objective(
                            objective.descriptionKey(), values.getOrDefault(objective.id(), 0L),
                    objective.required())).toList(), definition.prerequisiteMode(), definition.prerequisites(),
                    definition.unlocks(), definition.jeiItems());
        }).toList();
        String nationName = nationId == null ? "" : nationData.nation(nationId).map(NationSavedData.Nation::name).orElse("");
        long viewRevision = technologyData.revision() * 31L + TechnologyDefinitions.INSTANCE.revision();
        PacketDistributor.sendToPlayer(player, new S2C_TechnologySnapshotPacket(new TechnologySnapshot(
                viewRevision, nationId != null, nationName, nodes), openScreen));
    }
}
