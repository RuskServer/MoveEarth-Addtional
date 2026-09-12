package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.network.S2C_TerritoryCoreHealthPacket;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.OfflineDefenseService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TerritoryCoreHealthService {
    private TerritoryCoreHealthService() { }

    public static TerritorySavedData.CoreRecord damage(ServerLevel level, net.minecraft.core.BlockPos pos, int amount) {
        TerritorySavedData data = TerritorySavedData.get(level.getServer());
        TerritorySavedData.CoreRecord before = data.core(level.dimension().location(), pos).orElse(null);
        if (before == null || before.state() != TerritorySavedData.CoreState.EXPOSED || amount <= 0) return before;
        int appliedDamage = OfflineDefenseService.scale(level, pos, amount).appliedDamage();
        if (appliedDamage <= 0) return before;
        TerritorySavedData.CoreRecord after = data.damageCore(
                level.dimension().location(), pos, appliedDamage).orElse(before);
        syncLoadedBlock(level, after);
        syncNearby(level, after);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5D, pos.getY() + 0.7D,
                pos.getZ() + 0.5D, Math.min(24, 5 + appliedDamage / 4), 0.28D, 0.25D, 0.28D, 0.08D);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 3.5F,
                after.health() == 0 ? 0.55F : 0.85F);
        if (after.health() == 0 || crossedThreshold(before, after, 25) || crossedThreshold(before, after, 50)) {
            notifyNation(level, after);
        }
        return after;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().overworld().getGameTime() % 20L != 11L) return;
        TerritorySavedData data = TerritorySavedData.get(event.getServer());
        SiegeSavedData sieges = SiegeSavedData.get(event.getServer());
        for (TerritorySavedData.CoreRecord core : data.advanceCoreRegeneration(
                20L, sieges::isCoreRegenPaused)) {
            ServerLevel level = event.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, core.dimension()));
            if (level == null || !level.hasChunkAt(core.pos())) continue;
            syncLoadedBlock(level, core);
            syncNearby(level, core);
        }
    }

    private static boolean crossedThreshold(TerritorySavedData.CoreRecord before,
                                            TerritorySavedData.CoreRecord after, int percent) {
        return before.health() * 100 > before.maximumHealth() * percent
                && after.health() * 100 <= after.maximumHealth() * percent;
    }

    private static void notifyNation(ServerLevel level, TerritorySavedData.CoreRecord core) {
        NationSavedData nations = NationSavedData.get(level.getServer());
        NationSavedData.Nation nation = nations.nation(core.nationId()).orElse(null);
        if (nation == null) return;
        Component body = core.health() == 0
                ? Component.translatable("message.moveearth_addtional.territory_core.depleted",
                core.pos().getX(), core.pos().getY(), core.pos().getZ())
                : Component.translatable("message.moveearth_addtional.territory_core.damaged",
                core.pos().getX(), core.pos().getY(), core.pos().getZ(), core.health(), core.maximumHealth());
        for (UUID member : nation.members().keySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null) player.sendSystemMessage(MoveEarthMessage.warning(body));
        }
    }

    private static void syncLoadedBlock(ServerLevel level, TerritorySavedData.CoreRecord core) {
        if (level.getBlockEntity(core.pos()) instanceof TerritoryCoreBlockEntity blockEntity) blockEntity.bind(core);
    }

    private static void syncNearby(ServerLevel level, TerritorySavedData.CoreRecord core) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(core.pos().getCenter()) <= 32.0D * 32.0D) {
                PacketDistributor.sendToPlayer(player,
                        new S2C_TerritoryCoreHealthPacket(core.pos(), core.health(), core.maximumHealth()));
            }
        }
    }

    public static void syncCore(net.minecraft.server.MinecraftServer server,
                                TerritorySavedData.CoreRecord core) {
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, core.dimension()));
        if (level == null || !level.hasChunkAt(core.pos())) return;
        syncLoadedBlock(level, core);
        syncNearby(level, core);
    }
}
