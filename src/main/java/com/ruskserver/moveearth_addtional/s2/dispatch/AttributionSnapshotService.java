package com.ruskserver.moveearth_addtional.s2.dispatch;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeParticipationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.UUID;

/** Freezes weapon ownership when a delayed projectile/explosive enters the server world. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AttributionSnapshotService {
    private static final String ACTOR = "moveearth_dispatch_actor";
    private static final String NATION = "moveearth_dispatch_nation";
    private static final String CONTRACT = "moveearth_dispatch_contract";
    private static final String SIEGE = "moveearth_dispatch_siege";

    private AttributionSnapshotService() { }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        Entity owner = entity instanceof Projectile projectile ? projectile.getOwner()
                : entity instanceof PrimedTnt tnt ? tnt.getOwner() : null;
        if (owner instanceof ServerPlayer player) write(entity, capture(player));
    }

    public static Snapshot capture(ServerPlayer player) {
        UUID home = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        SiegeParticipationSavedData.Participation participation = SiegeParticipationSavedData.get(player.server)
                .forPlayer(player.getUUID()).orElse(null);
        if (participation == null || participation.contractId() == null) {
            return new Snapshot(player.getUUID(), home, null, null);
        }
        DispatchContractSavedData.Contract contract = DispatchContractSavedData.get(player.server)
                .byId(participation.contractId()).orElse(null);
        if (contract == null || contract.state() != DispatchContractSavedData.State.ACTIVE
                || !participation.siegeId().equals(contract.siegeId())) {
            return new Snapshot(player.getUUID(), home, null, null);
        }
        return new Snapshot(player.getUUID(), participation.combatNation(), contract.id(), participation.siegeId());
    }

    public static void write(Entity entity, Snapshot snapshot) {
        if (entity == null || snapshot == null || snapshot.actorId() == null) return;
        var data = entity.getPersistentData();
        data.putUUID(ACTOR, snapshot.actorId());
        if (snapshot.nationId() != null) data.putUUID(NATION, snapshot.nationId());
        if (snapshot.contractId() != null) data.putUUID(CONTRACT, snapshot.contractId());
        if (snapshot.siegeId() != null) data.putUUID(SIEGE, snapshot.siegeId());
    }

    public static SiegeService.AttackAttribution attribution(Entity entity, String source) {
        if (entity == null) return null;
        var data = entity.getPersistentData();
        if (!data.hasUUID(ACTOR)) return null;
        return new SiegeService.AttackAttribution(data.hasUUID(NATION) ? data.getUUID(NATION) : null,
                data.getUUID(ACTOR), source, data.hasUUID(CONTRACT) ? data.getUUID(CONTRACT) : null,
                data.hasUUID(SIEGE) ? data.getUUID(SIEGE) : null, true);
    }

    public record Snapshot(UUID actorId, UUID nationId, UUID contractId, UUID siegeId) { }
}
