package com.ruskserver.moveearth_addtional.s2.reinforcement;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureRecheckManager;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryCoreHealthService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.compat.cbc.CbcReinforcementCompat;
import com.ruskserver.moveearth_addtional.s2.siege.OfflineDefenseService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class ReinforcementEvents {
    private static final ReinforcementDamageLimiter DAMAGE_LIMITER = new ReinforcementDamageLimiter(5L);

    private ReinforcementEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) return;
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        ReinforcementEntry entry = data.get(event.getPos()).orElse(null);
        if (entry == null) return;
        if (SiegeService.peaceTruceBlocks(player, level, event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.moveearth_addtional.peace.truce_protected"), true);
            return;
        }
        if (OfflineDefenseService.settlementProtected(level, event.getPos())) {
            event.setCanceled(true);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.moveearth_addtional.siege.settlement_truce"), true);
            return;
        }
        SiegeService.recordAttack(player, level, event.getPos(), false);
        if (!SiegeDamageService.penaltyAt(level, event.getPos()).reinforcementProtectionEnabled()) {
            data.remove(event.getPos());
            TerritoryClosureRecheckManager.markPotentialOpening(level, event.getPos());
            ReinforcementService.syncNearbyManagers(level, event.getPos());
            return;
        }
        if (!entry.enabled() || (player.isCreative() && ReinforcementService.canManage(player, event.getPos()))) {
            data.remove(event.getPos());
            if (entry.enabled() && entry.durability() > 0) {
                TerritoryClosureRecheckManager.markPotentialOpening(level, event.getPos());
            }
            return;
        }
        event.setCanceled(true);
        if (!DAMAGE_LIMITER.tryDamage(player.getUUID(), level.dimension().location().toString(),
                event.getPos().asLong(), level.getGameTime())) return;
        var scaled = OfflineDefenseService.scale(level, event.getPos(), 1);
        if (scaled.appliedDamage() <= 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.moveearth_addtional.reinforcement.offline_defense",
                    scaled.carriedUnits(), scaled.divisor()), true);
            return;
        }
        ReinforcementEntry damaged = entry.damage(scaled.appliedDamage());
        SiegeService.recordAttack(player, level, event.getPos(), true);
        if (damaged.durability() <= 0) {
            data.remove(event.getPos());
            TerritoryClosureRecheckManager.markPotentialOpening(level, event.getPos());
            player.sendSystemMessage(com.ruskserver.moveearth_addtional.ui.MoveEarthMessage.warning(
                    "補強を破壊しました。もう一度採掘するとブロックを破壊できます。"));
        } else {
            data.put(event.getPos(), damaged);
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "補強に阻まれた  •  -1"), true);
        }
        ReinforcementService.syncNearbyManagers(level, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        net.minecraft.world.entity.Entity source = event.getExplosion().getDirectSourceEntity();
        ServerPlayer attacker = SiegeService.attributablePlayer(source);
        boolean cbc = CbcReinforcementCompat.isCbc(source);
        CbcMunitionDamage.Kind munition = CbcReinforcementCompat.kind(source);
        net.minecraft.core.BlockPos explosionCenter = net.minecraft.core.BlockPos.containing(
                event.getExplosion().center());
        boolean preHandled = cbc && CbcReinforcementCompat.wasRecentlyPreHandled(
                source, level, explosionCenter);
        boolean[] reinforcementChanged = {false};
        Map<Long, com.ruskserver.moveearth_addtional.s2.territory.UpkeepPenalty> penaltiesByChunk =
                new HashMap<>();
        event.getAffectedBlocks().removeIf(pos -> {
            if (SiegeService.peaceTruceBlocks(attacker, level, pos)) return true;
            TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                    .core(level.dimension().location(), pos).orElse(null);
            if (core != null) {
                if (preHandled) return true;
                SiegeService.recordAttack(attacker, level, pos, false);
                if (cbc) {
                    int beforeHealth = core.health();
                    TerritorySavedData.CoreRecord after = TerritoryCoreHealthService.damage(level, pos,
                            SiegeDamageService.configuredCoreDamage(munition));
                    if (after != null && after.health() < beforeHealth) {
                        SiegeService.recordAttack(attacker, level, pos, true);
                    }
                }
                return true;
            }
            ReinforcementEntry entry = data.get(pos).orElse(null);
            if (entry == null) return false;
            if (preHandled) return true;
            SiegeService.recordAttack(attacker, level, pos, false);
            if (!entry.enabled()) {
                data.remove(pos);
                reinforcementChanged[0] = true;
                return false;
            }
            long chunkKey = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            var penalty = penaltiesByChunk.computeIfAbsent(
                    chunkKey, ignored -> SiegeDamageService.penaltyAt(level, pos));
            if (!penalty.reinforcementProtectionEnabled()) {
                data.remove(pos);
                TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
                reinforcementChanged[0] = true;
                return false;
            }
            if (!cbc) return true;
            reinforcementChanged[0] = true;
            SiegeDamageService.ReinforcementDamage result = SiegeDamageService.damageReinforcement(
                    level, pos, entry, munition, penalty);
            if (result.appliedDamage() > 0) {
                SiegeService.recordAttack(attacker, level, pos, true);
            }
            return result.remains();
        });
        if (reinforcementChanged[0]) {
            ReinforcementService.syncNearbyManagers(level,
                    explosionCenter);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ReinforcementService.flushPendingScans(event.getServer());
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getGameTime() % 20L != 3L) continue;
            ReinforcementSavedData.AdvanceResult result = ReinforcementSavedData.get(level)
                    .advance(level, level.getGameTime());
            playClusteredSound(level, result.activated(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                    2.5F, 1.25F);
            playClusteredSound(level, result.completed(), net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                    2.8F, 1.55F);
            TerritoryClosureRecheckManager.markPotentialSeal(level, result.activated());
            TerritoryClosureRecheckManager.markPotentialOpenings(level, result.removed());
        }
    }

    private static void playClusteredSound(ServerLevel level, java.util.List<net.minecraft.core.BlockPos> positions,
                                           net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Set<Long> soundedChunks = new HashSet<>();
        for (var pos : positions) {
            long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            if (soundedChunks.add(chunk)) {
                level.playSound(null, pos, sound, net.minecraft.sounds.SoundSource.BLOCKS, volume, pitch);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ReinforcementService.clearScanCache(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DAMAGE_LIMITER.clear();
        WeldingBrushServerState.clear();
        ReinforcementService.clearScanCache();
        CbcReinforcementCompat.clearRuntimeState();
    }
}
