package com.ruskserver.moveearth_addtional.s2.reinforcement;

import com.ruskserver.moveearth_addtional.network.S2C_ReinforcementSnapshotPacket;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public final class ReinforcementService {
    public static final int SCAN_RADIUS = 64;
    private static final long FORCED_FULL_SYNC_TICKS = 10L * 20L;
    private static final Map<UUID, ScanSignature> LAST_SCANS = new HashMap<>();
    private static final Set<UUID> PENDING_SCANS = new HashSet<>();

    private ReinforcementService() {
    }

    public static InteractionResult apply(ServerPlayer player, BlockPos pos, Direction clickedFace) {
        if (!canManage(player, pos)) {
            player.sendSystemMessage(MoveEarthMessage.error(
                    "自国の予約領土内で補強を管理する権限が必要です。"));
            return InteractionResult.FAIL;
        }
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 36.0D) {
            return InteractionResult.FAIL;
        }
        ItemStack materialStack = player.getOffhandItem();
        ReinforcementMaterial material = materialFor(materialStack);
        if (material == null) {
            player.sendSystemMessage(MoveEarthMessage.warning(
                    "オフハンドに丸石、銅、鉄、金、ダイヤのいずれかを持ってください。"));
            return InteractionResult.FAIL;
        }

        ServerLevel level = player.serverLevel();
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        int brushRadius = WeldingBrushServerState.radius(player.getUUID());
        ReinforcementBrushPattern.Axis axis = switch (clickedFace.getAxis()) {
            case X -> ReinforcementBrushPattern.Axis.X;
            case Y -> ReinforcementBrushPattern.Axis.Y;
            case Z -> ReinforcementBrushPattern.Axis.Z;
        };
        int reinforced = 0;
        int repaired = 0;
        int skipped = 0;
        for (ReinforcementBrushPattern.Offset offset : ReinforcementBrushPattern.offsets(axis, brushRadius)) {
            BlockPos target = pos.offset(offset.x(), offset.y(), offset.z());
            if (!canManage(player, target)) {
                skipped++;
                continue;
            }
            var state = level.getBlockState(target);
            if (state.isAir() || state.getDestroySpeed(level, target) < 0.0F) {
                skipped++;
                continue;
            }
            ReinforcementEntry existing = data.get(target).orElse(null);
            ReinforcementEntry updated;
            if (existing == null) {
                updated = ReinforcementEntry.pending(material, level.getGameTime());
                reinforced++;
            } else if (existing.material() == material && existing.enabled()
                    && existing.activatesAt() == 0L && existing.damaged()) {
                updated = existing.repair();
                repaired++;
            } else {
                skipped++;
                continue;
            }
            if (!player.getAbilities().instabuild && materialStack.isEmpty()) {
                if (existing == null) reinforced--; else repaired--;
                skipped++;
                break;
            }
            data.put(target, updated);
            if (!player.getAbilities().instabuild) materialStack.shrink(1);
            player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            if (player.getMainHandItem().isEmpty()) break;
        }

        int changed = reinforced + repaired;
        if (changed == 0) {
            player.sendSystemMessage(MoveEarthMessage.warning(
                    net.minecraft.network.chat.Component.translatable(
                            "message.moveearth_addtional.welding.batch_none", skipped)));
            return InteractionResult.SUCCESS;
        }
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS,
                Math.min(3.5F, 2.2F + changed * 0.055F), 1.35F);
        player.sendSystemMessage(MoveEarthMessage.success(
                net.minecraft.network.chat.Component.translatable(
                        "message.moveearth_addtional.welding.batch_result",
                        ReinforcementBrushPattern.size(brushRadius), ReinforcementBrushPattern.size(brushRadius),
                        reinforced, repaired, skipped)));
        syncNearbyManagers(level, pos);
        return InteractionResult.SUCCESS;
    }

    public static void sendScan(ServerPlayer player, int requestedRadius) {
        int radius = Math.max(1, Math.min(SCAN_RADIUS, requestedRadius));
        NationSavedData nations = NationSavedData.get(player.server);
        java.util.UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        boolean allowed = nationId != null && canManage(player, player.blockPosition());
        TerritorySavedData territories = TerritorySavedData.get(player.server);
        SiegeSavedData sieges = SiegeSavedData.get(player.server);
        List<S2C_ReinforcementSnapshotPacket.Entry> entries = allowed
                ? ReinforcementSavedData.get(player.serverLevel())
                .around(player.serverLevel(), player.blockPosition(), radius).stream()
                .filter(value -> territories.ownsChunk(nationId,
                        player.level().dimension().location(), value.pos()))
                .map(value -> new S2C_ReinforcementSnapshotPacket.Entry(value.pos(),
                        value.entry().material(), value.entry().durability(), value.entry().enabled(),
                        (int) Math.min(Integer.MAX_VALUE,
                                value.entry().activationTicksRemaining(player.serverLevel().getGameTime())),
                        value.entry().activatesAt() > 0L,
                        sieges.isReinforcementDisabled(player.level().dimension().location(), value.pos())))
                .toList()
                : List.of();
        net.minecraft.resources.ResourceLocation dimension = player.level().dimension().location();
        long signature = signature(entries);
        long gameTime = player.serverLevel().getGameTime();
        ScanSignature previous = LAST_SCANS.get(player.getUUID());
        if (previous != null && previous.dimension.equals(dimension) && previous.allowed == allowed
                && previous.entryCount == entries.size() && previous.contentHash == signature
                && gameTime - previous.sentAtGameTime < FORCED_FULL_SYNC_TICKS) return;
        LAST_SCANS.put(player.getUUID(), new ScanSignature(
                dimension, allowed, entries.size(), signature, gameTime));
        PacketDistributor.sendToPlayer(player, new S2C_ReinforcementSnapshotPacket(dimension, allowed, entries));
    }

    public static void clearScanCache(UUID playerId) {
        LAST_SCANS.remove(playerId);
        PENDING_SCANS.remove(playerId);
    }

    public static void clearScanCache() {
        LAST_SCANS.clear();
        PENDING_SCANS.clear();
    }

    private static long signature(List<S2C_ReinforcementSnapshotPacket.Entry> entries) {
        long hash = 0xcbf29ce484222325L;
        for (S2C_ReinforcementSnapshotPacket.Entry entry : entries) {
            hash = mix(hash, entry.pos().asLong());
            hash = mix(hash, entry.material().ordinal());
            hash = mix(hash, entry.durability());
            hash = mix(hash, entry.activationTicksRemaining());
            hash = mix(hash, entry.enabled() ? 1L : 0L);
            hash = mix(hash, entry.constructionInProgress() ? 1L : 0L);
            hash = mix(hash, entry.siegeDisabled() ? 1L : 0L);
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }

    private record ScanSignature(net.minecraft.resources.ResourceLocation dimension, boolean allowed,
                                 int entryCount, long contentHash, long sentAtGameTime) { }

    public static boolean canManage(ServerPlayer player) {
        NationSavedData nations = NationSavedData.get(player.server);
        return nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT);
    }

    public static boolean canManage(ServerPlayer player, BlockPos pos) {
        NationSavedData nations = NationSavedData.get(player.server);
        java.util.UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        return nationId != null && nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT)
                && TerritorySavedData.get(player.server).ownsChunk(
                nationId, player.level().dimension().location(), pos);
    }

    public static void syncNearbyManagers(ServerLevel level, BlockPos pos) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(pos) <= (long) SCAN_RADIUS * SCAN_RADIUS
                    && canManage(player)) {
                PENDING_SCANS.add(player.getUUID());
            }
        }
    }

    /** Coalesces all block changes from the same server tick into one scan per nearby player. */
    public static void flushPendingScans(net.minecraft.server.MinecraftServer server) {
        if (PENDING_SCANS.isEmpty()) return;
        List<UUID> pending = List.copyOf(PENDING_SCANS);
        PENDING_SCANS.clear();
        for (UUID playerId : pending) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) sendScan(player, SCAN_RADIUS);
        }
    }

    private static ReinforcementMaterial materialFor(ItemStack stack) {
        if (stack.is(Items.COBBLESTONE)) return ReinforcementMaterial.COBBLESTONE;
        if (stack.is(Items.COPPER_INGOT)) return ReinforcementMaterial.COPPER;
        if (stack.is(Items.IRON_INGOT)) return ReinforcementMaterial.IRON;
        if (stack.is(Items.GOLD_INGOT)) return ReinforcementMaterial.GOLD;
        if (stack.is(Items.DIAMOND)) return ReinforcementMaterial.DIAMOND;
        return null;
    }
}
