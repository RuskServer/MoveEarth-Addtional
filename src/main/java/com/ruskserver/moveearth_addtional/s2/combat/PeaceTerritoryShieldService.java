package com.ruskserver.moveearth_addtional.s2.combat;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import com.ruskserver.moveearth_addtional.s2.dispatch.AttributionSnapshotService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.provenance.ActionContext;
import com.ruskserver.moveearth_addtional.s2.provenance.ActionSource;
import com.ruskserver.moveearth_addtional.s2.provenance.WorldMutationGuard;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cancels before armor/absorption, combat tags and shield energy are spent. No retaliation state exists. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class PeaceTerritoryShieldService {
    private static final TagKey<DamageType> GUN_DAMAGE = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("tacz", "bullets"));
    private static final Map<UUID, Integer> LAST_NOTICE = new HashMap<>();
    private PeaceTerritoryShieldService() { }

    private record Origin(ActionSource source, SiegeService.AttackAttribution snapshot) { }

    private static Origin origin(Object cause) {
        ActionSource source = WorldMutationGuard.source(cause);
        var snapshot = cause instanceof Entity entity
                ? AttributionSnapshotService.attribution(entity, "peace_shield") : null;
        return new Origin(source, snapshot);
    }

    public static boolean blockDamage(ServerPlayer victim, DamageSource damage, float amount) {
        if (amount <= 0 || RealPlayers.real(victim) == null) return false;
        var key = damage.typeHolder().unwrapKey().map(value -> value.location()).orElse(null);
        boolean ranged = PeaceTerritoryShieldPolicy.rangedType(damage.is(DamageTypeTags.IS_PROJECTILE),
                damage.is(DamageTypeTags.IS_EXPLOSION), damage.getDirectEntity() instanceof Projectile,
                damage.is(GUN_DAMAGE), key == null ? "" : key.getNamespace(), key == null ? "" : key.getPath());
        if (!ranged) return false;
        Origin direct = origin(damage.getDirectEntity());
        if (direct.source().kind() != ActionSource.Kind.UNKNOWN) return block(victim, direct);
        Origin indirect = origin(damage.getEntity());
        if (indirect.source().kind() != ActionSource.Kind.UNKNOWN) return block(victim, indirect);
        // An explicitly unidentified projectile must not borrow an unrelated caller's identity.
        if (damage.getDirectEntity() != null || damage.getEntity() != null) return block(victim, origin(null));
        return block(victim, origin(ActionContext.current()));
    }

    private static boolean block(ServerPlayer victim, Origin origin) {
        var manager = PvpMatchManager.INSTANCE;
        if (manager.isActive(victim) || victim.level().dimension().equals(PvpMatchManager.ARENA)) return false;
        ActionSource source = origin.source();
        if (source.kind() == ActionSource.Kind.MOB || source.kind() == ActionSource.Kind.NATURAL) return false;
        ServerPlayer attacker = source.actor() == null ? null : victim.server.getPlayerList().getPlayer(source.actor());
        if (attacker != null && manager.isActive(attacker)) return false;
        NationSavedData nations = NationSavedData.get(victim.server);
        UUID home = nations.nationIdFor(victim.getUUID()).orElse(null);
        if (home == null) return false;
        var territories = TerritorySavedData.get(victim.server);
        var dimension = victim.level().dimension().location();
        var position = victim.blockPosition();
        UUID land = territories.controllingNation(victim.server, dimension, position).orElse(null);
        if (!home.equals(land)) return false;
        // A valid frozen dispatch contract supplies the combat side, just like existing vehicle damage.
        UUID attackingNation = origin.snapshot() != null && origin.snapshot().frozen()
                && origin.snapshot().contractId() != null && origin.snapshot().nationId() != null
                ? origin.snapshot().nationId()
                : source.actor() != null ? nations.nationIdFor(source.actor()).orElse(null) : source.nation();
        if (!PeaceTerritoryShieldPolicy.blocks(victim.getUUID(), home, land, source.actor(), attackingNation,
                true, false, false, false)) return false;
        boolean combat = SiegeSavedData.get(victim.server).combatAt(home, dimension, position,
                coreId -> territories.coreById(coreId).map(core -> NationUpkeepService.effectiveTerritoryRadius(
                        victim.server, core.nationId(), core.radius())).orElse(-1));
        if (combat) return false;
        if (attacker != null) explain(attacker);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGunHit(EntityHurtByGunEvent.Pre event) {
        if (!(event.getHurtEntity() instanceof ServerPlayer victim)) return;
        Object cause = event.getBullet() != null ? event.getBullet() : event.getAttacker();
        Origin origin = origin(cause);
        if (!origin.source().allowed() && event.getAttacker() != null) origin = origin(event.getAttacker());
        if (block(victim, origin)) event.setCanceled(true);
    }

    /** Remove protected players before explosion damage AND knockback are applied. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel)) return;
        Origin origin = origin(WorldMutationGuard.explosionOrigin(event.getExplosion()));
        event.getAffectedEntities().removeIf(entity -> entity instanceof ServerPlayer player
                && RealPlayers.real(player) != null && block(player, origin));
    }

    private static void explain(ServerPlayer attacker) {
        int now = attacker.server.getTickCount();
        Integer previous = LAST_NOTICE.get(attacker.getUUID());
        if (previous != null && now >= previous && now - previous < 100) return;
        LAST_NOTICE.put(attacker.getUUID(), now);
        boolean ja = attacker.clientInformation().language().startsWith("ja");
        attacker.sendSystemMessage(MoveEarthMessage.warning(Component.translatableWithFallback(
                "message.moveearth_addtional.territory.peace_ranged_blocked", ja
                        ? "平時の自国領土内にいる国民には、他国の人や無所属の人による射撃・砲撃・爆風ダメージが入りません。対象領域でSiegeが始まると、この保護は解除されます。"
                        : "Citizens in their own peaceful territory are protected from foreign ranged attacks and explosions. This protection ends in the area of an active siege.")));
    }

    @SubscribeEvent public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_NOTICE.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void onStop(ServerStoppedEvent event) { LAST_NOTICE.clear(); }
}
