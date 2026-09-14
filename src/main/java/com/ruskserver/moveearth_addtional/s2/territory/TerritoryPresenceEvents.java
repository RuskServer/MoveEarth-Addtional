package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

/** Displays a title only when a player crosses between controlled territory and wilderness. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TerritoryPresenceEvents {
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final TerritoryTransitionTracker TRACKER = new TerritoryTransitionTracker();

    private TerritoryPresenceEvents() { }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int phase = Math.floorMod(player.getUUID().hashCode(), CHECK_INTERVAL_TICKS);
        if ((player.tickCount + phase) % CHECK_INTERVAL_TICKS != 0) return;
        observe(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) initialize(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) TRACKER.forget(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKER.clear();
    }

    private static void initialize(ServerPlayer player) {
        TRACKER.observe(player.getUUID(), controllingNation(player));
    }

    private static void observe(ServerPlayer player) {
        UUID nationId = controllingNation(player);
        if (TRACKER.observe(player.getUUID(), nationId)) showTitle(player, nationId);
    }

    private static UUID controllingNation(ServerPlayer player) {
        return TerritorySavedData.get(player.server)
                .controllingNation(player.server, player.serverLevel().dimension().location(), player.blockPosition())
                .orElse(null);
    }

    private static void showTitle(ServerPlayer player, UUID controllingNationId) {
        NationSavedData nations = NationSavedData.get(player.server);
        NationSavedData.Nation territoryNation = controllingNationId == null
                ? null : nations.nation(controllingNationId).orElse(null);

        Component title;
        Component subtitle;
        if (territoryNation == null) {
            title = Component.translatable("title.moveearth_addtional.territory.wilderness")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD);
            subtitle = Component.translatable("title.moveearth_addtional.territory.wilderness.subtitle")
                    .withStyle(ChatFormatting.DARK_GRAY);
        } else {
            TerritoryStyle style = territoryStyle(player, nations, controllingNationId);
            title = Component.literal(territoryNation.name()).withStyle(style.color(), ChatFormatting.BOLD);
            subtitle = Component.translatable(style.subtitleKey()).withStyle(style.color());
        }

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private static TerritoryStyle territoryStyle(ServerPlayer player, NationSavedData nations, UUID territoryNationId) {
        UUID viewerNationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (territoryNationId.equals(viewerNationId)) {
            return new TerritoryStyle(ChatFormatting.GREEN,
                    "title.moveearth_addtional.territory.relation.own");
        }
        return switch (nations.relation(viewerNationId, territoryNationId)) {
            case ALLIED -> new TerritoryStyle(ChatFormatting.AQUA,
                    "title.moveearth_addtional.territory.relation.allied");
            case HOSTILE -> new TerritoryStyle(ChatFormatting.RED,
                    "title.moveearth_addtional.territory.relation.hostile");
            default -> new TerritoryStyle(ChatFormatting.GOLD,
                    "title.moveearth_addtional.territory.relation.foreign");
        };
    }

    private record TerritoryStyle(ChatFormatting color, String subtitleKey) { }
}
