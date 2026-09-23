package com.ruskserver.moveearth_addtional.warehouse;

import com.ruskserver.moveearth_addtional.ModSounds;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.entity.ModEntities;
import com.ruskserver.moveearth_addtional.entity.WarehouseRaiderEntity;
import com.ruskserver.moveearth_addtional.entity.ai.RaiderRole;
import com.ruskserver.moveearth_addtional.entity.ai.RaiderSquadMemory;
import com.ruskserver.moveearth_addtional.raid.AirshipRaidDifficulty;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashSet;

/** Operator-gated combat prototype. No public activation or loot until the reward phase is ready. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WarehouseEncounterService {
    private static final String REGION_TAG = "MoveEarthWarehouseRegion";
    private static final String CYCLE_TAG = "MoveEarthWarehouseCycle";
    private static final String BOSS_TAG = "MoveEarthWarehouseBoss";
    private static final int BOSS_HEALTH = 320;
    private static final int GUARD_HEALTH = 64;
    private static final int INITIAL_GUARDS = 4;
    private static final int REINFORCEMENT_GUARDS = 2;
    private static final Map<Integer, ServerBossEvent> BARS = new HashMap<>();
    private static final Map<Integer, Integer> MISSING_TICKS = new HashMap<>();
    private static final Map<Integer, Set<UUID>> PARTICIPANTS = new HashMap<>();

    private WarehouseEncounterService() { }

    /** An operator must deliberately opt in while loot remains unimplemented. */
    public static boolean startForTesting(MinecraftServer server, int region) {
        WarehouseSites.Site site = WarehouseSites.get(server).all().stream()
                .filter(candidate -> candidate.regionId() == region).findFirst().orElse(null);
        if (site == null || !OpenTimeService.isOpen(server)) return false;
        ServerLevel level = server.overworld();
        if (!site.dimension().equals(level.dimension().location())
                || WarehouseEncounterState.get(server).get(region).phase()
                != WarehouseEncounterState.Phase.DORMANT) return false;
        BlockPos spawn = findSpawn(level, site);
        if (spawn == null) return false;
        WarehouseRaiderEntity boss = ModEntities.WAREHOUSE_RAIDER.get().create(level);
        if (boss == null) return false;
        boss.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BOSS_HEALTH);
        boss.getAttribute(Attributes.ARMOR).setBaseValue(12.0D);
        boss.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(40.0D);
        boss.setHealth(BOSS_HEALTH);
        boss.setCustomName(Component.literal("倉庫警備隊長"));
        boss.setCustomNameVisible(true);
        boss.setRole(RaiderRole.HEAVY);
        boss.equipRaidLoadout(AirshipRaidDifficulty.NORMAL);
        boss.setHealth(BOSS_HEALTH);
        boss.setPersistenceRequired();
        int cycle = WarehouseEncounterState.get(server).get(region).cycle() + 1;
        mark(boss, region, cycle, true, spawn);
        if (!WarehouseEncounterState.get(server).begin(region, boss.getUUID())) return false;
        if (!level.addFreshEntity(boss)) {
            WarehouseEncounterState.get(server).fail(region, boss.getUUID(), OpenTimeService.now(server));
            return false;
        }
        spawnGuards(level, site, region, cycle, spawn, INITIAL_GUARDS);
        Moveearth_addtional.LOGGER.info("Warehouse encounter started: region={} cycle={}", region, cycle);
        return true;
    }

    public static boolean resetForTesting(MinecraftServer server, int region) {
        WarehouseSites.Site site = WarehouseSites.get(server).all().stream()
                .filter(candidate -> candidate.regionId() == region).findFirst().orElse(null);
        if (site == null) return false;
        WarehouseEncounterState.Encounter previous = WarehouseEncounterState.get(server).get(region);
        WarehouseEncounterState.get(server).resetForTesting(region);
        RaiderSquadMemory.removeRaid(-region);
        clearBar(region);
        MISSING_TICKS.remove(region);
        PARTICIPANTS.remove(region);
        ServerLevel level = server.overworld();
        if (previous.boss() != null) {
            Entity boss = level.getEntity(previous.boss());
            if (boss != null) boss.discard();
        }
        BlockPos center = site.min().offset(WarehouseSitePolicy.WIDTH / 2, 1,
                WarehouseSitePolicy.LENGTH / 2);
        if (level.hasChunkAt(center)) {
            for (WarehouseRaiderEntity pillager : level.getEntitiesOfClass(WarehouseRaiderEntity.class,
                    new AABB(center).inflate(64), mob -> taggedRegion(mob) == region)) pillager.discard();
        }
        return true;
    }

    /** Shared, persistent 9-slot inventory behind the schematic's shipping containers. */
    public static boolean openLoot(ServerPlayer player, BlockPos pos) {
        WarehouseSites.Site site = WarehouseSites.get(player.server).all().stream()
                .filter(candidate -> candidate.dimension().equals(player.serverLevel().dimension().location())
                        && WarehouseSitePolicy.insideStructure(candidate.min().getX(), candidate.min().getY(),
                        candidate.min().getZ(), pos.getX(), pos.getY(), pos.getZ()))
                .findFirst().orElse(null);
        if (site == null) return false;
        var blockId = BuiltInRegistries.BLOCK.getKey(player.serverLevel().getBlockState(pos).getBlock());
        if (blockId == null || !blockId.getNamespace().equals("createdeco")
                || !blockId.getPath().contains("shipping_container")) return false;
        var loot = WarehouseEncounterState.get(player.server).loot(site.regionId());
        if (loot == null) return false;
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new ChestMenu(MenuType.GENERIC_9x1, id, inventory, loot, 1),
                Component.literal("地方" + site.regionId() + " 倉庫戦利品")));
        com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                com.ruskserver.moveearth_addtional.advancement.ModCriteria.WAREHOUSE_LOOT_OPENED);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof WarehouseRaiderEntity boss)
                || taggedRegion(boss) <= 0 || !(boss.level() instanceof ServerLevel level)) return;
        if (event.getSource().getEntity() instanceof ServerPlayer participant) {
            if (isBoss(boss)) {
                PARTICIPANTS.computeIfAbsent(taggedRegion(boss), ignored -> new HashSet<>())
                        .add(participant.getUUID());
            }
            com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(participant,
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.WAREHOUSE_RAID_PARTICIPATED);
        }
        if (!isBoss(boss)) return;
        int region = taggedRegion(boss);
        if (WarehouseEncounterPolicy.firstHalfHealthHit(
                WarehouseEncounterState.get(level.getServer()).get(region).phase()
                        == WarehouseEncounterState.Phase.ACTIVE, boss.getHealth(), boss.getMaxHealth())
                && WarehouseEncounterState.get(level.getServer()).alert(region, boss.getUUID())) {
            announceHalfHealth(level.getServer(), region, boss.blockPosition());
            spawnGuards(level, site(level.getServer(), region), region,
                    boss.getPersistentData().getInt(CYCLE_TAG), boss.blockPosition(), REINFORCEMENT_GUARDS);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof WarehouseRaiderEntity boss) || !isBoss(boss)
                || !(boss.level() instanceof ServerLevel level)) return;
        int region = taggedRegion(boss);
        if (WarehouseEncounterState.get(level.getServer()).alert(region, boss.getUUID())) {
            announceHalfHealth(level.getServer(), region, boss.blockPosition());
        }
        long now = OpenTimeService.now(level.getServer());
        if (!WarehouseEncounterState.get(level.getServer()).defeat(region, boss.getUUID(), now,
                WarehouseRewardTable.generate(region,
                        WarehouseEncounterState.get(level.getServer()).get(region).cycle()))) return;
        RaiderSquadMemory.removeRaid(-region);
        clearBar(region);
        discardGuards(level, region);
        level.getServer().getPlayerList().broadcastSystemMessage(
                MoveEarthMessage.info("地方" + region
                        + "の倉庫警備隊長が倒されました。建物内の輸送コンテナから戦利品を回収できます"), false);
        Moveearth_addtional.LOGGER.info("Warehouse encounter defeated: region={} cycle={}", region,
                WarehouseEncounterState.get(level.getServer()).get(region).cycle());
        for (UUID participantId : PARTICIPANTS.getOrDefault(region, Set.of())) {
            ServerPlayer participant = level.getServer().getPlayerList().getPlayer(participantId);
            if (participant != null) {
                com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(participant,
                        com.ruskserver.moveearth_addtional.advancement.ModCriteria.WAREHOUSE_BOSS_DEFEATED);
            }
        }
        PARTICIPANTS.remove(region);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof WarehouseRaiderEntity mob && taggedRegion(mob) > 0) event.getDrops().clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof WarehouseRaiderEntity mob)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        int region = taggedRegion(mob);
        if (region <= 0) return;
        WarehouseEncounterState.Encounter current = WarehouseEncounterState.get(level.getServer()).get(region);
        if ((current.phase() != WarehouseEncounterState.Phase.ACTIVE
                && current.phase() != WarehouseEncounterState.Phase.ALERTED)
                || current.cycle() != mob.getPersistentData().getInt(CYCLE_TAG)
                || isBoss(mob) && !mob.getUUID().equals(current.boss())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        WarehouseEncounterState state = WarehouseEncounterState.get(server);
        ServerLevel level = server.overworld();
        for (WarehouseSites.Site site : WarehouseSites.get(server).all()) {
            int region = site.regionId();
            state.advance(region, OpenTimeService.now(server));
            WarehouseEncounterState.Encounter encounter = state.get(region);
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && WarehouseSitePolicy.insideStructure(site.min().getX(), site.min().getY(),
                        site.min().getZ(), player.getBlockX(), player.getBlockY(), player.getBlockZ())) {
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                            com.ruskserver.moveearth_addtional.advancement.ModCriteria.WAREHOUSE_ENTERED);
                }
            }
            if (encounter.phase() == WarehouseEncounterState.Phase.DORMANT
                    && OpenTimeService.isOpen(server)
                    && server.getPlayerList().getPlayers().stream().anyMatch(player ->
                    player.serverLevel() == level && !player.isSpectator() && !player.isCreative()
                            && WarehouseSitePolicy.insideStructure(site.min().getX(), site.min().getY(),
                            site.min().getZ(), player.blockPosition().getX(),
                            player.blockPosition().getY(), player.blockPosition().getZ()))) {
                startForTesting(server, region);
                continue;
            }
            if (encounter.phase() != WarehouseEncounterState.Phase.ACTIVE
                    && encounter.phase() != WarehouseEncounterState.Phase.ALERTED) {
                clearBar(region);
                continue;
            }
            BlockPos center = site.min().offset(WarehouseSitePolicy.WIDTH / 2, 1,
                    WarehouseSitePolicy.LENGTH / 2);
            boolean nearby = server.getPlayerList().getPlayers().stream().anyMatch(player ->
                    player.serverLevel() == level && player.distanceToSqr(center.getCenter()) <= 96 * 96);
            if (!nearby || !level.hasChunkAt(center)) {
                clearBar(region);
                MISSING_TICKS.remove(region);
                continue;
            }
            Entity entity = level.getEntity(encounter.boss());
            if (!(entity instanceof WarehouseRaiderEntity boss) || !boss.isAlive()) {
                int missing = MISSING_TICKS.merge(region, 20, Integer::sum);
                if (missing >= 20 * 30 && state.fail(region, encounter.boss(), OpenTimeService.now(server))) {
                    RaiderSquadMemory.removeRaid(-region);
                    clearBar(region);
                    discardGuards(level, region);
                    PARTICIPANTS.remove(region);
                    Moveearth_addtional.LOGGER.warn("Warehouse encounter failed: missing boss in region {}", region);
                }
                continue;
            }
            MISSING_TICKS.remove(region);
            ServerBossEvent bar = BARS.computeIfAbsent(region, ignored -> new ServerBossEvent(
                    Component.literal("地方" + region + " 倉庫警備隊長"),
                    BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS));
            bar.setProgress(Math.max(0.0F, Math.min(1.0F, boss.getHealth() / boss.getMaxHealth())));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.serverLevel() == level && player.distanceToSqr(boss) <= 128 * 128) bar.addPlayer(player);
                else bar.removePlayer(player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
        MISSING_TICKS.clear();
        PARTICIPANTS.clear();
        for (WarehouseSites.Site site : WarehouseSites.get(event.getServer()).all()) {
            RaiderSquadMemory.removeRaid(-site.regionId());
        }
    }

    private static void mark(WarehouseRaiderEntity mob, int region, int cycle, boolean boss,
                             BlockPos anchor) {
        mob.setRaidId(-region);
        mob.setWarehouseAnchor(anchor);
        mob.getPersistentData().putInt(REGION_TAG, region);
        mob.getPersistentData().putInt(CYCLE_TAG, cycle);
        mob.getPersistentData().putBoolean(BOSS_TAG, boss);
    }

    private static int taggedRegion(WarehouseRaiderEntity mob) { return mob.getPersistentData().getInt(REGION_TAG); }

    private static boolean isBoss(WarehouseRaiderEntity mob) { return mob.getPersistentData().getBoolean(BOSS_TAG); }

    private static WarehouseSites.Site site(MinecraftServer server, int region) {
        return WarehouseSites.get(server).all().stream()
                .filter(candidate -> candidate.regionId() == region).findFirst().orElse(null);
    }

    private static BlockPos findSpawn(ServerLevel level, WarehouseSites.Site site) {
        if (site == null) return null;
        int centerX = site.min().getX() + WarehouseSitePolicy.WIDTH / 2;
        int centerZ = site.min().getZ() + WarehouseSitePolicy.LENGTH / 2;
        for (int radius = 0; radius <= 7; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (!WarehouseSitePolicy.within(site.min().getX(), site.min().getZ(), x, z, 0)) continue;
                    for (int dy = 1; dy < WarehouseSitePolicy.HEIGHT - 2; dy++) {
                        BlockPos pos = new BlockPos(x, site.min().getY() + dy, z);
                        if (!level.hasChunkAt(pos)) return null;
                        if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(),
                                net.minecraft.core.Direction.UP)) return pos;
                    }
                }
            }
        }
        return null;
    }

    private static void spawnGuards(ServerLevel level, WarehouseSites.Site site, int region, int cycle,
                                    BlockPos near, int count) {
        if (site == null) return;
        List<BlockPos> occupied = new ArrayList<>();
        BlockPos center = site.min().offset(WarehouseSitePolicy.WIDTH / 2, 1,
                WarehouseSitePolicy.LENGTH / 2);
        for (WarehouseRaiderEntity raider : level.getEntitiesOfClass(WarehouseRaiderEntity.class,
                new AABB(center).inflate(32), Entity::isAlive)) occupied.add(raider.blockPosition());
        int spawned = 0;
        while (spawned < count) {
            BlockPos candidate = findGuardSpawn(level, site, near, occupied);
            if (candidate == null) break;
            occupied.add(candidate);
            WarehouseRaiderEntity guard = ModEntities.WAREHOUSE_RAIDER.get().create(level);
            if (guard == null) break;
            guard.moveTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D,
                    0.0F, 0.0F);
            guard.setCustomName(Component.literal("倉庫警備員"));
            guard.setRole(spawned == count - 1 ? RaiderRole.FLANKER : RaiderRole.RIFLEMAN);
            guard.equipRaidLoadout(AirshipRaidDifficulty.NORMAL);
            guard.getAttribute(Attributes.MAX_HEALTH).setBaseValue(GUARD_HEALTH);
            guard.getAttribute(Attributes.ARMOR).setBaseValue(6.0D);
            guard.setHealth(GUARD_HEALTH);
            guard.setPersistenceRequired();
            mark(guard, region, cycle, false, candidate);
            if (level.addFreshEntity(guard)) spawned++;
        }
        if (spawned < count) Moveearth_addtional.LOGGER.warn(
                "Warehouse guards short of spawn positions: region={} requested={} spawned={}",
                region, count, spawned);
    }

    private static BlockPos findGuardSpawn(ServerLevel level, WarehouseSites.Site site,
                                           BlockPos near, List<BlockPos> occupied) {
        BlockPos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int minX = site.min().getX();
        int minZ = site.min().getZ();
        for (int x = minX; x < minX + WarehouseSitePolicy.WIDTH; x++) {
            for (int z = minZ; z < minZ + WarehouseSitePolicy.LENGTH; z++) {
                int dx = x - near.getX();
                int dz = z - near.getZ();
                int distance = dx * dx + dz * dz;
                if (distance < 9 || distance > 196) continue;
                for (int y = Math.max(site.min().getY() + 1, near.getY() - 1);
                     y <= Math.min(site.min().getY() + WarehouseSitePolicy.HEIGHT - 2,
                             near.getY() + 1); y++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(candidate) || !level.getBlockState(candidate).isAir()
                            || !level.getBlockState(candidate.above()).isAir()
                            || !level.getBlockState(candidate.below()).isFaceSturdy(level,
                            candidate.below(), net.minecraft.core.Direction.UP)) continue;
                    double nearest = occupied.stream().mapToDouble(pos -> pos.distSqr(candidate)).min()
                            .orElse(36.0D);
                    if (nearest < 9.0D) continue;
                    double score = Math.min(nearest, 36.0D) - Math.abs(distance - 36) * 0.5D
                            - Math.abs(y - near.getY()) * 4.0D;
                    if (score > bestScore) {
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }

    private static void announceHalfHealth(MinecraftServer server, int region, BlockPos pos) {
        int approxX = Math.floorDiv(pos.getX(), 256) * 256;
        int approxZ = Math.floorDiv(pos.getZ(), 256) * 256;
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.warning(
                "地方" + region
                        + "の警備隊長が半分まで削られました。およそ X=" + approxX + " Z=" + approxZ), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(ModSounds.SERVER_NOTICE.get(), SoundSource.MASTER, 0.85F, 1.0F);
        }
        Moveearth_addtional.LOGGER.info("Warehouse half-health alert: region={} approxX={} approxZ={}",
                region, approxX, approxZ);
    }


    private static void discardGuards(ServerLevel level, int region) {
        WarehouseSites.Site site = site(level.getServer(), region);
        if (site == null) return;
        BlockPos center = site.min().offset(WarehouseSitePolicy.WIDTH / 2, 1,
                WarehouseSitePolicy.LENGTH / 2);
        if (!level.hasChunkAt(center)) return;
        for (WarehouseRaiderEntity guard : level.getEntitiesOfClass(WarehouseRaiderEntity.class,
                new AABB(center).inflate(64), mob -> taggedRegion(mob) == region && !isBoss(mob))) {
            guard.discard();
        }
    }

    private static void clearBar(int region) {
        ServerBossEvent bar = BARS.remove(region);
        if (bar != null) bar.removeAllPlayers();
    }
}
