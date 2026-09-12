package com.ruskserver.moveearth_addtional.s2.reinforcement;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureRecheckManager;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryCoreHealthService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.compat.cbc.CbcReinforcementCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashSet;
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
        ReinforcementEntry damaged = entry.damage(1);
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
        boolean cbc = CbcReinforcementCompat.isCbc(source);
        CbcMunitionDamage.Kind munition = CbcReinforcementCompat.kind(source);
        boolean[] reinforcementChanged = {false};
        event.getAffectedBlocks().removeIf(pos -> {
            TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                    .core(level.dimension().location(), pos).orElse(null);
            if (core != null) {
                if (cbc) TerritoryCoreHealthService.damage(level, pos,
                        SiegeDamageService.configuredDamage(munition));
                return true;
            }
            ReinforcementEntry entry = data.get(pos).orElse(null);
            if (entry == null) return false;
            if (!entry.enabled()) {
                data.remove(pos);
                reinforcementChanged[0] = true;
                return false;
            }
            if (!SiegeDamageService.penaltyAt(level, pos).reinforcementProtectionEnabled()) {
                data.remove(pos);
                TerritoryClosureRecheckManager.markPotentialOpening(level, pos);
                reinforcementChanged[0] = true;
                return false;
            }
            if (!cbc) return true;
            reinforcementChanged[0] = true;
            return SiegeDamageService.damageReinforcement(level, pos, entry, munition);
        });
        if (reinforcementChanged[0]) {
            ReinforcementService.syncNearbyManagers(level,
                    net.minecraft.core.BlockPos.containing(event.getExplosion().center()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getGameTime() % 20L != 0L) continue;
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
    public static void onServerStopped(ServerStoppedEvent event) {
        DAMAGE_LIMITER.clear();
        WeldingBrushServerState.clear();
    }
}
