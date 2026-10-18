package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_JoinCinematicPacket;
import com.ruskserver.moveearth_addtional.s2.combat.CombatTagService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tells each joining client whether its join cinematic may play. A few seconds spent looking down from the sky
 * are harmless at home and dangerous in someone else's land, so it plays only for a player who is not
 * combat-tagged and stands in the wilderness, their own nation's land or an ally's. Decided half a second after
 * login, once a first join's random spawn has moved the player to where they will actually start.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class JoinCinematicService {
    private static final int DELAY_TICKS = 10;
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private JoinCinematicService() { }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PENDING.put(player.getUUID(), player.server.getTickCount() + DELAY_TICKS);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        int now = event.getServer().getTickCount();
        Iterator<Map.Entry<UUID, Integer>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (entry.getValue() > now) continue;
            iterator.remove();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            try {
                PacketDistributor.sendToPlayer(player, new S2C_JoinCinematicPacket(safe(player)));
            } catch (RuntimeException failure) {
                Moveearth_addtional.LOGGER.warn("Join cinematic decision failed for {}", player.getGameProfile().getName(), failure);
            }
        }
    }

    private static boolean safe(ServerPlayer player) {
        if (CombatTagService.isTagged(player) || player.isSpectator() || !player.isAlive()) return false;
        UUID territory = TerritorySavedData.get(player.server)
                .controllingNation(player.server, player.serverLevel().dimension().location(), player.blockPosition())
                .orElse(null);
        if (territory == null) return true;
        NationSavedData nations = NationSavedData.get(player.server);
        UUID own = nations.nationIdFor(player.getUUID()).orElse(null);
        return territory.equals(own) || nations.relation(own, territory) == NationSavedData.DiplomacyRelation.ALLIED;
    }
}
