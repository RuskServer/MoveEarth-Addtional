package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import com.ruskserver.moveearth_addtional.s2.combat.RealPlayers;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.NationRecoverySavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A nation under a rebuilding truce that strikes first loses the truce ({@link TruceAggressionPolicy}): a hit
 * on another nation's player or vehicle, anywhere, ends the truce and the rebuilding protection together and
 * is announced to everyone. Waiving the rebuilding protection ends the truce too ({@link #waived}), so no
 * nation can open Sieges while its own land is still shielded. Who last hit whom is kept by nation, so a
 * nation answering an attack on any of its members is defending, not starting a fight.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TruceAggressionService {
    private static final Map<Pair, Long> LAST_HIT = new HashMap<>();
    private static final int MAX_PAIRS = 4096;

    /** {@code from} hit {@code to}. */
    private record Pair(UUID from, UUID to) { }

    private TruceAggressionService() {
    }

    /** Damage that went through, between players of two nations. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || event.getAmount() <= 0.0F) return;
        ServerPlayer attacker = RealPlayers.attacker(event.getSource());
        if (attacker == null || attacker == victim) return;
        PvpMatchManager pvp = PvpMatchManager.INSTANCE;
        if (pvp.isActive(attacker) || pvp.isActive(victim)) return;
        NationSavedData nations = NationSavedData.get(attacker.server);
        UUID attackerNation = nations.nationIdFor(attacker.getUUID()).orElse(null);
        UUID victimNation = nations.nationIdFor(victim.getUUID()).orElse(null);
        // Driving someone off the nation's own land is defence, whoever struck first.
        UUID land = com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData.get(attacker.server)
                .controllingNation(attacker.server, victim.level().dimension().location(), victim.blockPosition())
                .orElse(null);
        if (attackerNation != null && attackerNation.equals(land)) {
            record(attackerNation, victimNation);
            return;
        }
        hit(attacker.server, attacker, attackerNation, victimNation);
    }

    /** A hit that wore down another nation's vehicle. */
    public static void vehicleHit(MinecraftServer server, UUID actorId, UUID attackerNation, UUID vehicleNation) {
        ServerPlayer actor = actorId == null ? null : server.getPlayerList().getPlayer(actorId);
        hit(server, actor, attackerNation, vehicleNation);
    }

    private static void hit(MinecraftServer server, ServerPlayer actor, UUID attackerNation, UUID victimNation) {
        if (attackerNation == null || victimNation == null || attackerNation.equals(victimNation)) return;
        long now = System.currentTimeMillis();
        NationSavedData nations = NationSavedData.get(server);
        SiegeSavedData sieges = SiegeSavedData.get(server);
        Long lastHitByVictim = LAST_HIT.get(new Pair(victimNation, attackerNation));
        if (TruceAggressionPolicy.endsTruce(sieges.isNationSettlementProtected(attackerNation), attackerNation,
                victimNation, nations.isAllied(attackerNation, victimNation),
                lastHitByVictim == null ? 0L : lastHitByVictim, now)) {
            forfeit(server, attackerNation, victimNation, actor);
        }
        record(attackerNation, victimNation);
    }

    private static void record(UUID attackerNation, UUID victimNation) {
        if (attackerNation == null || victimNation == null || attackerNation.equals(victimNation)) return;
        long now = System.currentTimeMillis();
        if (LAST_HIT.size() >= MAX_PAIRS) {
            LAST_HIT.values().removeIf(at -> !TruceAggressionPolicy.defending(at, now));
            if (LAST_HIT.size() >= MAX_PAIRS) LAST_HIT.clear();
        }
        LAST_HIT.put(new Pair(attackerNation, victimNation), now);
    }

    /**
     * Truces from before waiving ended them, or whose protection was waived some other way: a nation that gave
     * up its rebuilding protection keeps no truce. Checked every half minute.
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 600 != 0) return;
        SiegeSavedData sieges = SiegeSavedData.get(server);
        NationRecoverySavedData recoveries = NationRecoverySavedData.get(server);
        long now = OpenTimeService.now(server);
        for (UUID nationId : sieges.nationsUnderTruce()) {
            boolean waived = recoveries.eligibleForNation(nationId, now)
                    .map(episode -> recoveries.protectionWaived(episode.id())).orElse(false);
            if (waived) waived(server, nationId);
        }
    }

    /** The nation struck first while shielded: its truce and its rebuilding protection both end. */
    private static void forfeit(MinecraftServer server, UUID nationId, UUID victimNation, ServerPlayer actor) {
        SiegeSavedData.get(server).endNationSettlementTruce(nationId);
        waiveRecovery(server, nationId);
        NationSavedData nations = NationSavedData.get(server);
        String name = nations.nation(nationId).map(NationSavedData.Nation::name).orElse("?");
        String victim = nations.nation(victimNation).map(NationSavedData.Nation::name).orElse("?");
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.siege.truce_forfeited", name, victim,
                actor == null ? "?" : actor.getGameProfile().getName())), false);
        Moveearth_addtional.LOGGER.info("Rebuilding truce of {} forfeited by attacking {}", name, victim);
    }

    /** Waiving the rebuilding protection to attack again ends the truce on the nation's land with it. */
    public static void waived(MinecraftServer server, UUID nationId) {
        if (!SiegeSavedData.get(server).endNationSettlementTruce(nationId)) return;
        String name = NationSavedData.get(server).nation(nationId).map(NationSavedData.Nation::name).orElse("?");
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.siege.truce_waived", name)), false);
    }

    private static void waiveRecovery(MinecraftServer server, UUID nationId) {
        NationRecoverySavedData recoveries = NationRecoverySavedData.get(server);
        recoveries.eligibleForNation(nationId, OpenTimeService.now(server))
                .ifPresent(episode -> recoveries.waiveProtection(episode.id()));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_HIT.clear();
    }
}
