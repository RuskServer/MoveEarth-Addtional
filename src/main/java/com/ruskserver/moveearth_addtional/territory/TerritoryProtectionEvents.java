package com.ruskserver.moveearth_addtional.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import com.ruskserver.moveearth_addtional.territory.domain.ProtectionAction;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryProtectionService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TerritoryProtectionEvents {
    private static final long MESSAGE_COOLDOWN_TICKS = 40L;
    private static final Map<UUID, Long> LAST_DENIAL_MESSAGE = new HashMap<>();

    private TerritoryProtectionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || isPvpArena(level)) {
            return;
        }
        var decision = TerritoryProtectionService.authorize(
                level, event.getPos(), player, ProtectionAction.BLOCK_MODIFICATION);
        if (!decision.allowed()) {
            event.setCanceled(true);
            notifyDenied(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || isPvpArena(level)) {
            return;
        }
        var decision = TerritoryProtectionService.authorize(
                level, event.getPos(), player, ProtectionAction.BLOCK_MODIFICATION);
        if (!decision.allowed()) {
            event.setCanceled(true);
            notifyDenied(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || isPvpArena(level)
                || level.getBlockState(event.getPos()).getMenuProvider(level, event.getPos()) == null) {
            return;
        }
        var decision = TerritoryProtectionService.authorize(
                level, event.getPos(), player, ProtectionAction.CONTAINER_ACCESS);
        if (!decision.allowed()) {
            event.setCanceled(true);
            notifyDenied(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)
                || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || isPvpArena(victim.serverLevel())
                || attacker.hasPermissions(2)) {
            return;
        }
        if (TerritoryProtectionService.isProtected(
                victim.serverLevel(), victim.blockPosition(), ProtectionAction.PLAYER_DAMAGE)) {
            event.setCanceled(true);
            notifyDenied(attacker);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || isPvpArena(level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> TerritoryProtectionService.isProtected(
                level, pos, ProtectionAction.BLOCK_MODIFICATION));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_DENIAL_MESSAGE.clear();
    }

    private static void notifyDenied(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long last = LAST_DENIAL_MESSAGE.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2L);
        if (now - last < MESSAGE_COOLDOWN_TICKS) {
            return;
        }
        LAST_DENIAL_MESSAGE.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("message.moveearth_addtional.territory.protected"), true);
    }

    private static boolean isPvpArena(ServerLevel level) {
        return level.dimension().equals(PvpMatchManager.ARENA);
    }
}
