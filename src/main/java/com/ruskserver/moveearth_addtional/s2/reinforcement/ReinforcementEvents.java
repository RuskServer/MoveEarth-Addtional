package com.ruskserver.moveearth_addtional.s2.reinforcement;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureRecheckManager;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryCoreHealthService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.compat.cbc.CbcReinforcementCompat;
import com.ruskserver.moveearth_addtional.compat.warnautics.WarnauticsReinforcementCompat;
import com.ruskserver.moveearth_addtional.s2.siege.OfflineDefenseService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class ReinforcementEvents {
    private static final ReinforcementDamageLimiter DAMAGE_LIMITER = new ReinforcementDamageLimiter(5L);
    private static final Map<Explosion, Map<BlockPos, BlockState>> CBC_EXPLOSION_SNAPSHOTS =
            new WeakHashMap<>();

    private ReinforcementEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCbcExplosionStart(ExplosionEvent.Start event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !CbcReinforcementCompat.isCbcExplosion(event.getExplosion())
                || WarnauticsReinforcementCompat.handles(event.getExplosion())) return;
        BlockPos center = BlockPos.containing(event.getExplosion().center());
        int radius = com.ruskserver.moveearth_addtional.config.S2TerritoryConfig.cbcProtectedBlastRadius();
        Map<BlockPos, BlockState> snapshots = new LinkedHashMap<>();
        for (ReinforcementSavedData.LocatedEntry located : ReinforcementSavedData.get(level)
                .around(level, center, radius)) {
            snapshots.put(located.pos().immutable(), level.getBlockState(located.pos()));
        }
        if (!snapshots.isEmpty()) CBC_EXPLOSION_SNAPSHOTS.put(event.getExplosion(), snapshots);
    }

    /** A newly placed block must never inherit reinforcement left behind at an empty position. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        Set<BlockPos> stale = new java.util.LinkedHashSet<>();
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            for (BlockSnapshot snapshot : multiPlace.getReplacedBlockSnapshots()) {
                if (snapshot.getState().isAir()) stale.add(snapshot.getPos().immutable());
            }
        } else if (event.getBlockSnapshot().getState().isAir()) {
            stale.add(event.getPos().immutable());
        }
        if (stale.isEmpty()) return;
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        stale.removeIf(pos -> data.get(pos).isEmpty());
        if (stale.isEmpty()) return;
        stale.forEach(data::remove);
        TerritoryClosureRecheckManager.markPotentialOpenings(level, stale);
        ReinforcementService.syncChangedNearbyManagers(level, stale);
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
            ReinforcementService.syncChangedNearbyManagers(level, Set.of(event.getPos()));
            return;
        }
        if (!entry.enabled() || (player.isCreative() && ReinforcementService.canManage(player, event.getPos()))) {
            data.remove(event.getPos());
            if (entry.enabled() && entry.durability() > 0) {
                TerritoryClosureRecheckManager.markPotentialOpening(level, event.getPos());
            }
            ReinforcementService.syncChangedNearbyManagers(level, Set.of(event.getPos()));
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
        ReinforcementService.syncChangedNearbyManagers(level, Set.of(event.getPos()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // Warnautics publishes a later mutable blast-list event. Let that dedicated bridge
        // handle the blast once; otherwise generic protection would consume it first.
        if (WarnauticsReinforcementCompat.handles(event.getExplosion())) {
            CBC_EXPLOSION_SNAPSHOTS.remove(event.getExplosion());
            return;
        }
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        net.minecraft.world.entity.Entity source = event.getExplosion().getDirectSourceEntity();
        ServerPlayer attacker = SiegeService.attributablePlayer(source);
        boolean sourceIsCbc = CbcReinforcementCompat.isCbc(source);
        boolean cbc = sourceIsCbc || CbcReinforcementCompat.isCbcExplosion(event.getExplosion());
        CbcMunitionDamage.Kind munition = sourceIsCbc
                ? CbcReinforcementCompat.kind(source)
                : CbcReinforcementCompat.kind(event.getExplosion());
        net.minecraft.core.BlockPos explosionCenter = net.minecraft.core.BlockPos.containing(
                event.getExplosion().center());
        boolean preHandled = cbc && CbcReinforcementCompat.wasRecentlyPreHandled(
                source, level, explosionCenter);
        Set<net.minecraft.core.BlockPos> reinforcementChanges = new java.util.LinkedHashSet<>();
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
                reinforcementChanges.add(pos.immutable());
                return false;
            }
            long chunkKey = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            var penalty = penaltiesByChunk.computeIfAbsent(
                    chunkKey, ignored -> SiegeDamageService.penaltyAt(level, pos));
            if (!penalty.reinforcementProtectionEnabled()) {
                data.remove(pos);
                TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
                reinforcementChanges.add(pos.immutable());
                return false;
            }
            if (!cbc) return true;
            SiegeDamageService.ReinforcementDamage result = SiegeDamageService.damageReinforcement(
                    level, pos, entry, munition, penalty);
            if (result.appliedDamage() > 0) {
                SiegeService.recordAttack(attacker, level, pos, true);
                reinforcementChanges.add(pos.immutable());
            }
            return result.remains();
        });
        restoreCbcTransforms(level, data, event.getExplosion(), reinforcementChanges);
        ReinforcementService.syncChangedNearbyManagers(level, reinforcementChanges);
    }

    private static void restoreCbcTransforms(ServerLevel level, ReinforcementSavedData data,
                                             Explosion explosion, Set<BlockPos> changes) {
        Map<BlockPos, BlockState> snapshots = CBC_EXPLOSION_SNAPSHOTS.remove(explosion);
        if (snapshots == null || snapshots.isEmpty()) return;
        for (Map.Entry<BlockPos, BlockState> snapshot : snapshots.entrySet()) {
            BlockPos pos = snapshot.getKey();
            ReinforcementEntry remaining = data.get(pos).orElse(null);
            if (remaining == null || !remaining.enabled()) continue;
            BlockState original = snapshot.getValue();
            BlockState current = level.getBlockState(pos);
            if (current.equals(original)) continue;
            if (original.hasBlockEntity()) {
                // A state-only restore after a container was destroyed could duplicate its contents.
                data.remove(pos);
                TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
            } else {
                level.setBlock(pos, original, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            changes.add(pos.immutable());
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
            Set<net.minecraft.core.BlockPos> changes = new java.util.LinkedHashSet<>();
            changes.addAll(result.progressed());
            changes.addAll(result.activated());
            changes.addAll(result.completed());
            changes.addAll(result.removed());
            ReinforcementService.syncChangedNearbyManagers(level, changes);
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
        CBC_EXPLOSION_SNAPSHOTS.clear();
        WarnauticsReinforcementCompat.clearRuntimeState();
    }
}
