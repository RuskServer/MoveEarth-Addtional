package com.ruskserver.moveearth_addtional.s2.reinforcement;

import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_ReinforcementSnapshotPacket;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_ReinforcementDeltaPacket;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryClosureRecheckManager;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import net.minecraft.resources.ResourceLocation;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;

public final class ReinforcementService {
    public static final int SCAN_RADIUS = 64;
    private static final int MAX_SCAN_ENTRIES = 8192;
    private static final long FORCED_FULL_SYNC_TICKS = 10L * 20L;
    private static final Map<UUID, ScanSignature> LAST_SCANS = new HashMap<>();
    private static final Set<UUID> PENDING_SCANS = new HashSet<>();
    private static final Map<UUID, PendingDelta> PENDING_DELTAS = new HashMap<>();
    /** Client-requested scans walk every reinforced block in range, so each player gets one per interval. */
    private static final int REQUEST_INTERVAL_TICKS = 10;
    private static final Map<UUID, Integer> LAST_REQUESTED_SCAN = new HashMap<>();
    private static final Map<UUID, Integer> DEFERRED_REQUESTS = new HashMap<>();

    private ReinforcementService() {
    }

    public static InteractionResult apply(ServerPlayer player, BlockPos pos, Direction clickedFace) {
        if (!canManage(player, pos)) {
            player.sendSystemMessage(MoveEarthMessage.error(
                    "ここで補強を管理する権限がありません。自国の予約領土か、補強を許可された同盟国の領土で行ってください。"));
            return InteractionResult.FAIL;
        }
        if (SableVehicleTopology.distanceSquared(player.serverLevel(), player, pos) > 36.0D) {
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
        boolean repairWaiting = false;
        boolean siegeRepair = false;
        List<BlockPos> changedPositions = new java.util.ArrayList<>();
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
            repairWaiting |= data.repairBlockedUntil(target, level.getGameTime()) > level.getGameTime();
            ReinforcementEntry updated;
            if (existing == null) {
                updated = ReinforcementEntry.pending(material, level.getGameTime(),
                        data.repairBlockedUntil(target, level.getGameTime()));
                reinforced++;
            } else if (existing.material() == material && existing.enabled()
                    && existing.activatesAt() == 0L && existing.damaged()
                    && data.repairBlockedUntil(target, level.getGameTime()) <= level.getGameTime()) {
                updated = existing.repair();
                repaired++;
                siegeRepair |= TerritorySavedData.get(player.server).controllingCore(
                                player.server, level.dimension().location(), target)
                        .map(core -> SiegeSavedData.get(player.server).isNationLocked(core.nationId()))
                        .orElse(false);
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
            changedPositions.add(target.immutable());
            if (existing == null) {
                com.ruskserver.moveearth_addtional.advancement.AdvancementEvents.trackReinforcement(
                        player, level.dimension(), target, updated.activatesAt());
            }
            if (!player.getAbilities().instabuild) materialStack.shrink(1);
            player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            if (player.getMainHandItem().isEmpty()) break;
        }

        int changed = reinforced + repaired;
        if (repairWaiting) player.sendSystemMessage(MoveEarthMessage.warning(
                net.minecraft.network.chat.Component.translatable("message.moveearth_addtional.welding.repair_wait")));
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
        if (siegeRepair) {
            com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.SIEGE_REINFORCEMENT_REPAIRED);
        }
        syncChangedNearbyManagers(level, changedPositions);
        return InteractionResult.SUCCESS;
    }

    /**
     * Deliberately removes owned reinforcement without breaking the underlying block.
     * Materials are not refunded and position-level combat repair delays remain intact.
     */
    public static InteractionResult strip(ServerPlayer player, BlockPos pos, Direction clickedFace) {
        if (!canStrip(player, pos)) {
            player.sendSystemMessage(MoveEarthMessage.error(
                    net.minecraft.network.chat.Component.translatable(
                            "message.moveearth_addtional.welding.strip_denied")));
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
        List<BlockPos> removed = new java.util.ArrayList<>();
        int skipped = 0;
        for (ReinforcementBrushPattern.Offset offset : ReinforcementBrushPattern.offsets(axis, brushRadius)) {
            BlockPos target = pos.offset(offset.x(), offset.y(), offset.z());
            if (!canStrip(player, target) || data.get(target).isEmpty()) {
                skipped++;
                continue;
            }
            data.remove(target);
            removed.add(target.immutable());
            player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            if (player.getMainHandItem().isEmpty()) break;
        }

        if (removed.isEmpty()) {
            player.sendSystemMessage(MoveEarthMessage.warning(
                    net.minecraft.network.chat.Component.translatable(
                            "message.moveearth_addtional.welding.strip_none")));
            return InteractionResult.SUCCESS;
        }
        TerritoryClosureRecheckManager.markPotentialOpenings(level, removed);
        syncChangedNearbyManagers(level, removed);
        level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS,
                Math.min(3.0F, 1.3F + removed.size() * 0.04F), 0.85F);
        player.sendSystemMessage(MoveEarthMessage.success(
                net.minecraft.network.chat.Component.translatable(
                        "message.moveearth_addtional.welding.strip_result",
                        removed.size(), ReinforcementBrushPattern.size(brushRadius),
                        ReinforcementBrushPattern.size(brushRadius), skipped)));
        return InteractionResult.SUCCESS;
    }

    public static void sendScan(ServerPlayer player, int requestedRadius) {
        int radius = Math.max(1, Math.min(SCAN_RADIUS, requestedRadius));
        NationSavedData nations = NationSavedData.get(player.server);
        java.util.UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        boolean allowed = nationId != null && nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT);
        ServerLevel level = player.serverLevel();
        ResourceLocation dimension = level.dimension().location();
        long gameTime = level.getGameTime();
        List<ScanEntry> selected = List.of();
        ChunkVisibility visibility = null;
        if (allowed) {
            ReinforcementSavedData reinforcementData = ReinforcementSavedData.get(level);
            java.util.LinkedHashMap<BlockPos, ReinforcementSavedData.LocatedEntry> visible = new java.util.LinkedHashMap<>();
            reinforcementData.around(level, player.blockPosition(), radius)
                    .forEach(value -> visible.put(value.pos(), value));
            SableVehicleTopology.entriesForPlayer(player, reinforcementData)
                    .forEach(value -> visible.put(value.pos(), value));
            visibility = new ChunkVisibility(player, nationId);
            List<ScanEntry> candidates = new java.util.ArrayList<>(visible.size());
            for (ReinforcementSavedData.LocatedEntry value : visible.values()) {
                if (!visibility.reinforceable(value.pos())) continue;
                // Sort key computed once: the Sable-aware distance is far too costly for a comparator.
                candidates.add(new ScanEntry(value, SableVehicleTopology.distanceSquared(level, player, value.pos())));
            }
            candidates.sort(java.util.Comparator.comparingDouble(ScanEntry::distanceSquared));
            selected = candidates.size() > MAX_SCAN_ENTRIES ? candidates.subList(0, MAX_SCAN_ENTRIES) : candidates;
        }
        long signature = ReinforcementScanSignature.EMPTY;
        for (ScanEntry value : selected) {
            BlockPos pos = value.located().pos();
            signature = ReinforcementScanSignature.add(signature, pos.asLong(), value.located().entry(),
                    visibility.siegeDisabled(pos));
        }
        ScanSignature previous = LAST_SCANS.get(player.getUUID());
        if (previous != null && previous.dimension.equals(dimension) && previous.allowed == allowed
                && previous.entryCount == selected.size() && previous.contentHash == signature
                && gameTime - previous.sentAtGameTime < FORCED_FULL_SYNC_TICKS) return;
        LAST_SCANS.put(player.getUUID(), new ScanSignature(
                dimension, allowed, selected.size(), signature, gameTime));
        List<S2C_ReinforcementSnapshotPacket.Entry> entries = new java.util.ArrayList<>(selected.size());
        for (ScanEntry value : selected) {
            BlockPos pos = value.located().pos();
            ReinforcementEntry entry = value.located().entry();
            entries.add(new S2C_ReinforcementSnapshotPacket.Entry(pos, entry.material(), entry.durability(),
                    entry.enabled(), activationTicks(entry, gameTime), entry.constructing(),
                    visibility.siegeDisabled(pos)));
        }
        PacketDistributor.sendToPlayer(player, new S2C_ReinforcementSnapshotPacket(dimension, allowed, entries));
    }

    /** Remaining ticks relative to {@code gameTime}, the server tick at which the packet is built. */
    private static int activationTicks(ReinforcementEntry entry, long gameTime) {
        return (int) Math.min(Integer.MAX_VALUE, entry.activationTicksRemaining(gameTime));
    }

    private record ScanEntry(ReinforcementSavedData.LocatedEntry located, double distanceSquared) { }

    /**
     * Per-request cache of the visibility rules. Territory, ally grants, Sable containment and siege
     * fall-out are all resolved per chunk column, so one lookup serves every reinforced block in it.
     */
    private static final class ChunkVisibility {
        private final ServerPlayer player;
        private final UUID nationId;
        private final TerritorySavedData territories;
        private final SiegeSavedData sieges;
        private final ResourceLocation dimension;
        private final Map<Long, Boolean> reinforceable = new HashMap<>();
        private final Map<Long, Boolean> siegeDisabled = new HashMap<>();

        private ChunkVisibility(ServerPlayer player, UUID nationId) {
            this.player = player;
            this.nationId = nationId;
            this.territories = TerritorySavedData.get(player.server);
            this.sieges = SiegeSavedData.get(player.server);
            this.dimension = player.level().dimension().location();
        }

        boolean reinforceable(BlockPos pos) {
            return reinforceable.computeIfAbsent(chunkKey(pos), ignored ->
                    reinforceableFor(player, territories, nationId, pos)
                            || com.ruskserver.moveearth_addtional.s2.nation.AllyAccessService
                            .canReinforce(player, pos));
        }

        boolean siegeDisabled(BlockPos pos) {
            return siegeDisabled.computeIfAbsent(chunkKey(pos), ignored ->
                    sieges.isReinforcementDisabled(dimension, pos));
        }
    }

    private static long chunkKey(BlockPos pos) {
        return net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** Whether {@code player} may see and manage reinforcement on {@code pos}; matches the scan filter. */
    public static boolean reinforceableBy(ServerPlayer player, BlockPos pos) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        return nationId != null && nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT)
                && (reinforceableFor(player, TerritorySavedData.get(player.server), nationId, pos)
                || com.ruskserver.moveearth_addtional.s2.nation.AllyAccessService.canReinforce(player, pos));
    }

    private static boolean reinforceableFor(ServerPlayer player, TerritorySavedData territories,
                                            UUID nationId, BlockPos pos) {
        return territories.allowsReinforcement(player.server, nationId, player.level().dimension().location(), pos)
                || SableVehicleTopology.at(player.serverLevel(), pos)
                .map(context -> nationId.equals(context.vehicle().nationId())).orElse(false);
    }

    /**
     * Entry point for scans the client asks for. A request inside the interval is not dropped: the
     * latest one is deferred and served by {@link #flushPendingScans} once the interval has passed.
     */
    public static void requestScan(ServerPlayer player, int requestedRadius) {
        int now = player.server.getTickCount();
        Integer last = LAST_REQUESTED_SCAN.get(player.getUUID());
        if (last != null && now - last < REQUEST_INTERVAL_TICKS) {
            DEFERRED_REQUESTS.put(player.getUUID(), requestedRadius);
            return;
        }
        LAST_REQUESTED_SCAN.put(player.getUUID(), now);
        DEFERRED_REQUESTS.remove(player.getUUID());
        sendScan(player, requestedRadius);
    }

    public static void clearScanCache(UUID playerId) {
        LAST_SCANS.remove(playerId);
        PENDING_SCANS.remove(playerId);
        PENDING_DELTAS.remove(playerId);
        LAST_REQUESTED_SCAN.remove(playerId);
        DEFERRED_REQUESTS.remove(playerId);
    }

    public static void clearScanCache() {
        LAST_SCANS.clear();
        PENDING_SCANS.clear();
        PENDING_DELTAS.clear();
        LAST_REQUESTED_SCAN.clear();
        DEFERRED_REQUESTS.clear();
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
        if (nationId == null || !nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT)) return false;
        var vehicle = SableVehicleTopology.at(player.serverLevel(), pos).orElse(null);
        if (vehicle != null) return vehicle.vehicle().health() > 0
                && nationId.equals(vehicle.vehicle().nationId())
                && com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService
                .penalty(player.server, nationId).reinforcementProtectionEnabled();
        return TerritorySavedData.get(player.server).allowsReinforcement(
                player.server, nationId, player.level().dimension().location(), pos)
                || com.ruskserver.moveearth_addtional.s2.nation.AllyAccessService.canReinforce(player, pos);
    }

    /** Ownership check for dismantling; unlike construction it also permits inactive owned territory. */
    public static boolean canStrip(ServerPlayer player, BlockPos pos) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        boolean permission = nationId != null
                && nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT);
        boolean withinReach = SableVehicleTopology.distanceSquared(
                player.serverLevel(), player, pos) <= 36.0D;
        boolean owned = false;
        if (nationId != null) {
            var vehicle = SableVehicleTopology.at(player.serverLevel(), pos).orElse(null);
            if (vehicle != null) {
                owned = nationId.equals(vehicle.vehicle().nationId());
            } else {
                ResourceLocation dimension = player.level().dimension().location();
                owned = TerritorySavedData.get(player.server).reservedCores(dimension, pos).stream()
                        .anyMatch(core -> nationId.equals(core.nationId()));
            }
        }
        return ReinforcementRemovalPolicy.canStrip(
                nationId != null, permission, owned, withinReach);
    }

    public static void syncNearbyManagers(ServerLevel level, BlockPos pos) {
        for (ServerPlayer player : level.players()) {
            if (SableVehicleTopology.distanceSquared(level, player, pos) <= (long) SCAN_RADIUS * SCAN_RADIUS
                    && canManage(player)) {
                PENDING_SCANS.add(player.getUUID());
                PENDING_DELTAS.remove(player.getUUID());
            }
        }
    }

    /**
     * Queues exact changed positions, coalesced into one delta packet per player and server tick.
     * Positions are grouped per chunk column once; for ordinary (non-Sable) chunks one exact distance to a
     * representative block plus the group's extent accepts or rejects the whole column per player, so only
     * columns straddling the scan sphere pay per-position distance checks.
     */
    public static void syncChangedNearbyManagers(ServerLevel level, Collection<BlockPos> changedPositions) {
        if (changedPositions == null || changedPositions.isEmpty()) return;
        ResourceLocation dimension = level.dimension().location();
        List<ServerPlayer> recipients = new java.util.ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (PENDING_SCANS.contains(player.getUUID()) || !canManage(player)) continue;
            PendingDelta pending = PENDING_DELTAS.get(player.getUUID());
            if (pending != null && !pending.dimension.equals(dimension)) {
                PENDING_DELTAS.remove(player.getUUID());
                PENDING_SCANS.add(player.getUUID());
                continue;
            }
            recipients.add(player);
        }
        if (recipients.isEmpty()) return;
        List<ChangeGroup> groups = ChangeGroup.of(level, changedPositions);
        double radius = SCAN_RADIUS;
        double radiusSquared = radius * radius;
        for (ServerPlayer player : recipients) {
            PendingDelta pending = PENDING_DELTAS.get(player.getUUID());
            for (ChangeGroup group : groups) {
                boolean acceptAll = false;
                if (group.plainWorld()) {
                    double distance = Math.sqrt(SableVehicleTopology.distanceSquared(level, player, group.anchor()));
                    if (distance - group.extent() > radius) continue;
                    acceptAll = distance + group.extent() <= radius;
                }
                for (BlockPos pos : group.positions()) {
                    if (!acceptAll && SableVehicleTopology.distanceSquared(level, player, pos) > radiusSquared) continue;
                    if (pending == null) {
                        pending = new PendingDelta(dimension, new LinkedHashSet<>());
                        PENDING_DELTAS.put(player.getUUID(), pending);
                    }
                    pending.positions.add(pos);
                }
            }
        }
    }

    /**
     * Changed positions of one chunk column. {@code extent} bounds the Euclidean distance of every member
     * from {@code anchor}; it is only used for columns outside Sable plots, where block distance is plain
     * world distance and the triangle inequality holds exactly.
     */
    private record ChangeGroup(BlockPos anchor, List<BlockPos> positions, double extent, boolean plainWorld) {
        static List<ChangeGroup> of(ServerLevel level, Collection<BlockPos> changedPositions) {
            Map<Long, List<BlockPos>> byChunk = new java.util.LinkedHashMap<>();
            for (BlockPos pos : changedPositions) {
                byChunk.computeIfAbsent(chunkKey(pos), ignored -> new java.util.ArrayList<>()).add(pos.immutable());
            }
            List<ChangeGroup> groups = new java.util.ArrayList<>(byChunk.size());
            for (List<BlockPos> positions : byChunk.values()) {
                BlockPos anchor = positions.getFirst();
                if (positions.size() == 1) {
                    groups.add(new ChangeGroup(anchor, positions, 0.0D, false));
                    continue;
                }
                long extentSquared = 0L;
                for (BlockPos pos : positions) {
                    long dx = pos.getX() - anchor.getX();
                    long dy = pos.getY() - anchor.getY();
                    long dz = pos.getZ() - anchor.getZ();
                    extentSquared = Math.max(extentSquared, dx * dx + dy * dy + dz * dz);
                }
                boolean plainWorld = SableVehicleTopology.placement(level, anchor).subLevel() == null;
                groups.add(new ChangeGroup(anchor, positions, Math.sqrt(extentSquared), plainWorld));
            }
            return groups;
        }
    }

    /** Coalesces all block changes from the same server tick into one scan per nearby player. */
    public static void flushPendingScans(net.minecraft.server.MinecraftServer server) {
        if (!DEFERRED_REQUESTS.isEmpty()) {
            int now = server.getTickCount();
            for (Map.Entry<UUID, Integer> request : Map.copyOf(DEFERRED_REQUESTS).entrySet()) {
                Integer last = LAST_REQUESTED_SCAN.get(request.getKey());
                if (last != null && now - last < REQUEST_INTERVAL_TICKS) continue;
                DEFERRED_REQUESTS.remove(request.getKey());
                ServerPlayer player = server.getPlayerList().getPlayer(request.getKey());
                if (player != null) requestScan(player, request.getValue());
            }
        }
        if (!PENDING_SCANS.isEmpty()) {
            List<UUID> pending = List.copyOf(PENDING_SCANS);
            PENDING_SCANS.clear();
            for (UUID playerId : pending) {
                PENDING_DELTAS.remove(playerId);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) sendScan(player, SCAN_RADIUS);
            }
        }
        if (PENDING_DELTAS.isEmpty()) return;
        Map<UUID, PendingDelta> deltas = Map.copyOf(PENDING_DELTAS);
        PENDING_DELTAS.clear();
        for (Map.Entry<UUID, PendingDelta> value : deltas.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(value.getKey());
            if (player != null) sendDelta(player, value.getValue());
        }
    }

    private static void sendDelta(ServerPlayer player, PendingDelta pending) {
        if (!player.level().dimension().location().equals(pending.dimension)
                || pending.positions.size() > 8_192) {
            sendScan(player, SCAN_RADIUS);
            return;
        }
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (nationId == null || !canManage(player)) {
            sendScan(player, SCAN_RADIUS);
            return;
        }
        ServerLevel level = player.serverLevel();
        ReinforcementSavedData data = ReinforcementSavedData.get(level);
        ChunkVisibility visibility = new ChunkVisibility(player, nationId);
        List<S2C_ReinforcementDeltaPacket.Entry> upserts = new java.util.ArrayList<>();
        List<BlockPos> removals = new java.util.ArrayList<>();
        long now = level.getGameTime();
        long radiusSquared = (long) SCAN_RADIUS * SCAN_RADIUS;
        for (BlockPos pos : pending.positions) {
            ReinforcementEntry entry = data.get(pos).orElse(null);
            // Same visibility rule as sendScan (own territory, owned vehicle or ally grant), so a delta can
            // never remove an entry the next snapshot would show again.
            if (entry == null || !visibility.reinforceable(pos)
                    || SableVehicleTopology.distanceSquared(level, player, pos) > radiusSquared
                    || level.getBlockState(pos).isAir()) {
                removals.add(pos);
                continue;
            }
            upserts.add(new S2C_ReinforcementDeltaPacket.Entry(pos, entry.material(), entry.durability(),
                    entry.enabled(), activationTicks(entry, now), entry.constructing(),
                    visibility.siegeDisabled(pos)));
        }
        if (!upserts.isEmpty() || !removals.isEmpty()) {
            PacketDistributor.sendToPlayer(player,
                    new S2C_ReinforcementDeltaPacket(pending.dimension, upserts, removals));
        }
    }

    private record PendingDelta(ResourceLocation dimension, Set<BlockPos> positions) { }

    private static ReinforcementMaterial materialFor(ItemStack stack) {
        if (stack.is(Items.COBBLESTONE)) return ReinforcementMaterial.COBBLESTONE;
        if (stack.is(Items.COPPER_INGOT)) return ReinforcementMaterial.COPPER;
        if (stack.is(Items.IRON_INGOT)) return ReinforcementMaterial.IRON;
        if (stack.is(Items.GOLD_INGOT)) return ReinforcementMaterial.GOLD;
        if (stack.is(Items.DIAMOND)) return ReinforcementMaterial.DIAMOND;
        return null;
    }
}
