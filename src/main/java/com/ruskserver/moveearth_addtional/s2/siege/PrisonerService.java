package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Captures downed enemy combatants and confines them to the holding nation's vault chunk. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PrisonerService {
    private PrisonerService() { }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPlayerInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer captor)
                || !(event.getTarget() instanceof ServerPlayer captive)
                || event.getHand() != InteractionHand.MAIN_HAND || !captor.isShiftKeyDown()
                || !captor.getMainHandItem().isEmpty() || !CompatEventHandler.isPlayerDown(captive)) return;
        NationSavedData nations = NationSavedData.get(captor.server);
        UUID captorNation = nations.nationIdFor(captor.getUUID()).orElse(null);
        UUID captiveNation = nations.nationIdFor(captive.getUUID()).orElse(null);
        if (captorNation == null || captiveNation == null || captorNation.equals(captiveNation)
                || !SiegeSavedData.get(captor.server).hasConflictBetween(captorNation, captiveNation)) return;
        PrisonerSavedData prisoners = PrisonerSavedData.get(captor.server);
        if (prisoners.prisoner(captor.getUUID()).isPresent()) return;
        if (prisoners.prisoner(captive.getUUID()).isPresent()) {
            captor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.prisoner.already_held")));
            event.setCanceled(true);
            return;
        }
        Destination holding = destination(captor.server, captorNation).orElse(null);
        if (holding == null) {
            captor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.prisoner.no_holding_destination")));
            event.setCanceled(true);
            return;
        }
        if (!CompatEventHandler.revivePlayer(captive)) {
            captor.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.prisoner.revive_failed")));
            event.setCanceled(true);
            return;
        }
        PrisonerSavedData.CaptureResult result = prisoners.capture(
                captive.getUUID(), captiveNation, captorNation, captor.getUUID(), System.currentTimeMillis());
        if (result == PrisonerSavedData.CaptureResult.CAPTURED) {
            teleport(captive, holding);
            captor.server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.prisoner.captured", captive.getGameProfile().getName(),
                    nations.nation(captorNation).map(NationSavedData.Nation::name).orElse("?"))), false);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        PrisonerSavedData data = PrisonerSavedData.get(player.server);
        PrisonerSavedData.Prisoner prisoner = data.prisoner(player.getUUID()).orElse(null);
        if (prisoner == null) return;
        Destination destination = destination(player.server, prisoner.holdingNation()).orElse(null);
        if (destination == null) {
            releaseOrphan(player, data, prisoner);
            return;
        }
        if (!destination.contains(player)) teleport(player, destination);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && PrisonerSavedData.get(player.server).prisoner(player.getUUID()).isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && PrisonerSavedData.get(player.server).prisoner(player.getUUID()).isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOutgoingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && PrisonerSavedData.get(player.server).prisoner(player.getUUID()).isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PrisonerSavedData data = PrisonerSavedData.get(player.server);
        UUID homeNation = data.pendingReleaseHome(player.getUUID()).orElse(null);
        if (homeNation != null) {
            destination(player.server, homeNation).ifPresent(destination -> teleport(player, destination));
            data.acknowledgeRelease(player.getUUID());
            player.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "message.moveearth_addtional.prisoner.released")));
        }
    }

    public static boolean canReturnAll(net.minecraft.server.MinecraftServer server,
                                       UUID firstNation, UUID secondNation) {
        NationSavedData nations = NationSavedData.get(server);
        return PrisonerSavedData.get(server).between(firstNation, secondNation).stream()
                .allMatch(prisoner -> nations.nation(prisoner.homeNation()).isPresent()
                        && nations.nation(prisoner.holdingNation()).isPresent()
                        && destination(server, prisoner.homeNation()).isPresent());
    }

    public static int returnAll(net.minecraft.server.MinecraftServer server,
                                UUID firstNation, UUID secondNation) {
        PrisonerSavedData data = PrisonerSavedData.get(server);
        List<PrisonerSavedData.Prisoner> released = data.releaseBetween(firstNation, secondNation);
        for (PrisonerSavedData.Prisoner prisoner : released) {
            ServerPlayer online = server.getPlayerList().getPlayer(prisoner.playerId());
            if (online == null) continue;
            destination(server, prisoner.homeNation()).ifPresent(destination -> teleport(online, destination));
            data.acknowledgeRelease(prisoner.playerId());
            online.sendSystemMessage(MoveEarthMessage.success(Component.translatable(
                    "message.moveearth_addtional.prisoner.released")));
        }
        return released.size();
    }

    private static void releaseOrphan(ServerPlayer player, PrisonerSavedData data,
                                      PrisonerSavedData.Prisoner prisoner) {
        data.releaseBetween(prisoner.homeNation(), prisoner.holdingNation());
        data.acknowledgeRelease(player.getUUID());
        player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.prisoner.holding_lost")));
    }

    private static Optional<Destination> destination(net.minecraft.server.MinecraftServer server, UUID nationId) {
        TerritorySavedData territories = TerritorySavedData.get(server);
        TerritorySavedData.VaultChunk vault = territories.vaultChunk(nationId).orElse(null);
        if (vault != null) {
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, vault.dimension()));
            if (level != null) return Optional.of(atSurface(level, vault.chunkX(), vault.chunkZ()));
        }
        return territories.cores().stream()
                .filter(core -> core.nationId().equals(nationId)
                        && core.type() == TerritorySavedData.CoreType.CAPITAL)
                .findFirst().flatMap(core -> {
                    ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION, core.dimension()));
                    return level == null ? Optional.empty()
                            : Optional.of(atSurface(level, core.pos().getX() >> 4, core.pos().getZ() >> 4));
                });
    }

    private static Destination atSurface(ServerLevel level, int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        return new Destination(level, chunkX, chunkZ, new BlockPos(x, y, z));
    }

    private static void teleport(ServerPlayer player, Destination destination) {
        player.teleportTo(destination.level, destination.pos.getX() + 0.5D,
                destination.pos.getY(), destination.pos.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
    }

    private record Destination(ServerLevel level, int chunkX, int chunkZ, BlockPos pos) {
        private boolean contains(ServerPlayer player) {
            return player.level() == level && (player.blockPosition().getX() >> 4) == chunkX
                    && (player.blockPosition().getZ() >> 4) == chunkZ;
        }
    }
}
