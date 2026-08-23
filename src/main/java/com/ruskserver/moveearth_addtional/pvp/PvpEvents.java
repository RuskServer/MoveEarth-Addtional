package com.ruskserver.moveearth_addtional.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.Map;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PvpEvents {
    private static final Map<BlockPos, BlockState> PENDING_BLOCK_RESTORES = new LinkedHashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobSpawnPositionCheck(MobSpawnEvent.PositionCheck event) {
        if (!event.getLevel().getLevel().dimension().equals(PvpMatchManager.ARENA)) return;
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.COMMAND && type != MobSpawnType.SPAWN_EGG && type != MobSpawnType.BUCKET) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(PvpMatchManager.ARENA)
                && (PvpMatchManager.INSTANCE.phase() == PvpPhase.RUNNING
                || PvpMatchManager.INSTANCE.phase() == PvpPhase.FINISHED)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(PvpMatchManager.ARENA)
                && (PvpMatchManager.INSTANCE.phase() == PvpPhase.RUNNING
                || PvpMatchManager.INSTANCE.phase() == PvpPhase.FINISHED)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTaczAmmoHitBlock(AmmoHitBlockEvent event) {
        if (!event.getLevel().dimension().equals(PvpMatchManager.ARENA)) return;
        // Cancelling this TaCZ event keeps the projectile alive. Restore the struck block next tick instead,
        // so bullets still discard/explode normally while fragile terrain remains intact.
        PENDING_BLOCK_RESTORES.putIfAbsent(event.getHitResult().getBlockPos().immutable(), event.getState());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel arena = event.getServer().getLevel(PvpMatchManager.ARENA);
        if (arena != null && !PENDING_BLOCK_RESTORES.isEmpty()) {
            PENDING_BLOCK_RESTORES.forEach((position, state) -> arena.setBlock(position, state, 3));
            PENDING_BLOCK_RESTORES.clear();
        }
        PvpMatchManager.INSTANCE.tick(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void balancePvpGunDamage(EntityHurtByGunEvent.Pre event) {
        if (!(event.getAttacker() instanceof ServerPlayer attacker)
                || !(event.getHurtEntity() instanceof ServerPlayer victim)) return;

        PvpMatchManager manager = PvpMatchManager.INSTANCE;
        if (manager.phase() != PvpPhase.RUNNING
                || !manager.isActive(attacker)
                || !manager.isActive(victim)
                || manager.team(attacker) == manager.team(victim)) return;

        Float multiplier = PvpWeaponBalance.damageMultiplier(event.getGunId());
        if (multiplier == null) return;
        event.setBaseAmount(event.getBaseAmount() * multiplier);
        event.setHeadshotMultiplier(event.isHeadShot() ? PvpWeaponBalance.HEADSHOT_MULTIPLIER : 1.0F);
        var armoredSource = event.getDamageSource(GunDamageSourcePart.NON_ARMOR_PIERCING);
        if (armoredSource != null) {
            event.setDamageSource(GunDamageSourcePart.ARMOR_PIERCING, armoredSource);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        PvpMatchManager manager = PvpMatchManager.INSTANCE;
        ServerPlayer attacker = event.getSource().getEntity() instanceof ServerPlayer player ? player : null;
        ServerPlayer victim = event.getEntity() instanceof ServerPlayer player ? player : null;

        if (attacker != null && manager.isActive(attacker)) {
            if (victim == null || !manager.isActive(victim) || manager.team(attacker) == manager.team(victim)) {
                event.setCanceled(true);
                return;
            }
        }
        if (victim != null && manager.isActive(victim) && attacker != null && !manager.isActive(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFinalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        PvpMatchManager manager = PvpMatchManager.INSTANCE;
        if (!manager.isActive(victim) || manager.phase() != PvpPhase.RUNNING) return;
        if (event.getNewDamage() < victim.getHealth() + victim.getAbsorptionAmount()) return;

        event.setNewDamage(0.0F);
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer player ? player : null;
        manager.eliminate(victim, killer);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeathFallback(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        PvpMatchManager manager = PvpMatchManager.INSTANCE;
        if (!manager.isActive(victim) || manager.phase() != PvpPhase.RUNNING) return;
        event.setCanceled(true);
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer player ? player : null;
        manager.eliminate(victim, killer);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && PvpMatchManager.INSTANCE.isActive(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player && PvpMatchManager.INSTANCE.isActive(player)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof ServerPlayer player && PvpMatchManager.INSTANCE.isActive(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !player.level().dimension().equals(PvpMatchManager.ARENA)
                || PvpMatchManager.INSTANCE.phase() != PvpPhase.RUNNING) return;
        if (event.getLevel().getBlockState(event.getPos()).getMenuProvider(event.getLevel(), event.getPos()) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        PvpMatchManager.INSTANCE.tickNonParticipant(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PvpMatchManager.INSTANCE.recoverIfNeeded(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PvpMatchManager.INSTANCE.leave(player);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension().equals(PvpMatchManager.ARENA)) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_BLOCK_RESTORES.clear();
        PvpMatchManager.INSTANCE.serverStopped();
    }
}
