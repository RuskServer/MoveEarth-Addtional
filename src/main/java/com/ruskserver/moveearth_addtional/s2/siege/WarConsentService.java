package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_WarConsentPromptPacket;
import com.ruskserver.moveearth_addtional.s2.combat.RealPlayers;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of {@link WarConsentPolicy}: who confirmed which nation, the prompts sent, and their rate limit.
 *
 * <p>Consent is held per (player, target nation) in memory only, for {@link WarConsentPolicy#CONSENT_MILLIS}
 * of real time or until the player logs out, whichever comes first. A restart forgets it, which only means
 * one more prompt. All calls run on the server thread.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WarConsentService {
    private static final Map<Key, Consent> CONSENT_EXPIRES = new HashMap<>();
    private static final Map<Key, Long> PROMPTED_AT = new HashMap<>();
    private static final Map<UUID, Long> LAST_PROMPT = new HashMap<>();
    private static final int PRUNE_INTERVAL_TICKS = 1_200;

    private record Key(UUID player, UUID nation) { }

    /**
     * A confirmation and the confirming player's own nation then. A player who changes nation afterwards
     * must confirm again: the consent was given for the side they stood on.
     */
    private record Consent(long expires, UUID nationAtConfirm) { }

    private WarConsentService() { }

    /**
     * Whether a nation attack recorded for {@code attackerNation} on {@code defenderNation} must be refused
     * until {@code actorId} confirms it. A nationless or individual attacker never needs consent.
     */
    public static boolean required(MinecraftServer server, UUID attackerNation, boolean individualAttacker,
                                   UUID actorId, UUID defenderNation) {
        if (individualAttacker || attackerNation == null || defenderNation == null
                || attackerNation.equals(defenderNation)) return false;
        NationSavedData nations = NationSavedData.get(server);
        Consent consent = actorId == null ? null : CONSENT_EXPIRES.get(new Key(actorId, defenderNation));
        Long expires = consent == null || !java.util.Objects.equals(consent.nationAtConfirm(),
                nations.nationIdFor(actorId).orElse(null)) ? null : consent.expires();
        return WarConsentPolicy.decide(attackerNation, false, defenderNation, actorId,
                nations.isHostileFrom(attackerNation, defenderNation),
                nations.isAllied(attackerNation, defenderNation),
                SiegeSavedData.get(server).hasConflictBetween(attackerNation, defenderNation),
                expires != null && WarConsentPolicy.consentLive(expires, System.currentTimeMillis()))
                == WarConsentPolicy.Decision.REQUIRED;
    }

    /**
     * Refuses the hit: prompts the actor when they are online (rate-limited) and returns the action-bar
     * reason. Nation-only attribution gets the reason that hostility must be declared first.
     */
    public static Component refuse(MinecraftServer server, UUID actorId, UUID defenderNation) {
        String name = NationSavedData.get(server).nation(defenderNation)
                .map(NationSavedData.Nation::name).orElse("?");
        if (actorId != null) {
            ServerPlayer player = RealPlayers.real(server.getPlayerList().getPlayer(actorId));
            if (player != null) prompt(player, defenderNation, name);
        }
        return reason(server, actorId, defenderNation);
    }

    /** The refusal {@link #refuse} gives, without opening the consent prompt. */
    public static Component reason(MinecraftServer server, UUID actorId, UUID defenderNation) {
        String name = NationSavedData.get(server).nation(defenderNation)
                .map(NationSavedData.Nation::name).orElse("?");
        return Component.translatable(actorId == null
                ? "message.moveearth_addtional.war_consent.hostility_required"
                : "message.moveearth_addtional.war_consent.required", name);
    }

    private static void prompt(ServerPlayer player, UUID defenderNation, String name) {
        long now = System.currentTimeMillis();
        Long last = LAST_PROMPT.get(player.getUUID());
        if (!WarConsentPolicy.promptDue(last == null ? 0L : last, now)) return;
        LAST_PROMPT.put(player.getUUID(), now);
        PROMPTED_AT.put(new Key(player.getUUID(), defenderNation), now);
        long failedTicks = S2TerritoryConfig.siegeFailedCooldownTicks();
        PacketDistributor.sendToPlayer(player, new S2C_WarConsentPromptPacket(defenderNation, name,
                (int) (WarConsentPolicy.CONSENT_MILLIS / 60_000L),
                (int) Math.max(0L, (failedTicks + 1_199L) / 1_200L)));
    }

    /** Handles the confirm button. Accepted only for a nation this player was prompted about recently. */
    public static void confirm(ServerPlayer player, UUID targetNation) {
        if (RealPlayers.real(player) == null || targetNation == null) return;
        MinecraftServer server = player.server;
        NationSavedData nations = NationSavedData.get(server);
        UUID ownNation = nations.nationIdFor(player.getUUID()).orElse(null);
        NationSavedData.Nation target = nations.nation(targetNation).orElse(null);
        Key key = new Key(player.getUUID(), targetNation);
        long now = System.currentTimeMillis();
        Long promptedAt = PROMPTED_AT.remove(key);
        // The prompt itself proves the attack would be recorded for a nation (a nationless gunner of a
        // nation's cannon is asked too), so only own and allied targets are refused here.
        if (target == null || targetNation.equals(ownNation)
                || (ownNation != null && nations.isAllied(ownNation, targetNation))
                || !WarConsentPolicy.confirmable(promptedAt == null ? 0L : promptedAt, now)) {
            player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.war_consent.expired")));
            return;
        }
        CONSENT_EXPIRES.put(key, new Consent(now + WarConsentPolicy.CONSENT_MILLIS, ownNation));
        Moveearth_addtional.LOGGER.info("War consent: player={} ({}) nation={} target={} ({})",
                player.getGameProfile().getName(), player.getUUID(), ownNation, target.name(), targetNation);
        player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.war_consent.confirmed", target.name(),
                WarConsentPolicy.CONSENT_MILLIS / 60_000L)));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % PRUNE_INTERVAL_TICKS != 0) return;
        long now = System.currentTimeMillis();
        CONSENT_EXPIRES.values().removeIf(consent -> !WarConsentPolicy.consentLive(consent.expires(), now));
        PROMPTED_AT.values().removeIf(at -> !WarConsentPolicy.confirmable(at, now));
        LAST_PROMPT.values().removeIf(at -> WarConsentPolicy.promptDue(at, now));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        CONSENT_EXPIRES.keySet().removeIf(key -> key.player().equals(playerId));
        PROMPTED_AT.keySet().removeIf(key -> key.player().equals(playerId));
        LAST_PROMPT.remove(playerId);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CONSENT_EXPIRES.clear();
        PROMPTED_AT.clear();
        LAST_PROMPT.clear();
    }
}
