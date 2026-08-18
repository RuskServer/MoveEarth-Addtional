package com.ruskserver.moveearth_addtional.raid;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ModSounds;
import com.ruskserver.moveearth_addtional.entity.AirshipRaiderEntity;
import com.ruskserver.moveearth_addtional.entity.ModEntities;
import com.ruskserver.moveearth_addtional.entity.ai.RaiderRole;
import com.ruskserver.moveearth_addtional.entity.ai.RaiderSquadMemory;
import com.ruskserver.moveearth_addtional.compat.SableAirshipController;
import com.ruskserver.moveearth_addtional.network.S2C_AnnouncementPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.concurrent.ThreadLocalRandom;
import org.joml.Vector3d;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class AirshipRaidManager {
    public static final long AUTOMATIC_CHECK_INTERVAL = 20L * 60L * 30L;
    public static final long PLAYER_COOLDOWN = 20L * 60L * 60L * 12L;
    public static final long RAID_TIME_LIMIT = 20L * 60L * 10L;
    public static final long SALVAGE_TIME_LIMIT = 20L * 60L * 15L;
    private static final double AUTOMATIC_CHANCE = 0.10D;
    private static final Map<Integer, AirshipRaidInstance> ACTIVE_RAIDS = new LinkedHashMap<>();
    private static final Map<Integer, Long> RETREAT_STARTED = new LinkedHashMap<>();

    private AirshipRaidManager() {
    }

    public static AirshipRaidInstance start(MinecraftServer server, ServerPlayer target,
                                            AirshipRaidDifficulty difficulty, boolean automatic) {
        AirshipRaidSavedData data = AirshipRaidSavedData.get(server);
        int id = data.allocateRaidId();
        long gameTime = server.overworld().getGameTime();
        AirshipRaidInstance raid = new AirshipRaidInstance(id, target, difficulty, gameTime);
        SableAirshipController.create(target, id).ifPresent(raid::setShipId);
        ACTIVE_RAIDS.put(id, raid);
        data.recordRaid(target.getUUID(), gameTime);
        String source = automatic ? "自動襲撃" : "手動襲撃";
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "[飛行船襲撃] " + source + " #" + id + " が " + target.getGameProfile().getName()
                        + " を対象に開始されました。難易度: " + difficulty.name().toLowerCase()), false);
        PacketDistributor.sendToPlayer(target, new S2C_AnnouncementPacket("警告: 敵性飛行船が接近しています"));
        target.playNotifySound(ModSounds.SERVER_NOTICE.get(), SoundSource.MASTER, 1.2F, 0.8F);
        return raid;
    }

    public static boolean stop(MinecraftServer server, int id) {
        AirshipRaidInstance raid = ACTIVE_RAIDS.remove(id);
        if (raid == null) return false;
        discardRaiders(server, raid);
        removeAirship(server, raid);
        RETREAT_STARTED.remove(id);
        RaiderSquadMemory.removeRaid(id);
        raid.setPhase(AirshipRaidPhase.FINISHED);
        server.getPlayerList().broadcastSystemMessage(Component.literal("[飛行船襲撃] #" + id + " を停止しました。"), false);
        return true;
    }

    public static int stopAll(MinecraftServer server) {
        int count = ACTIVE_RAIDS.size();
        ACTIVE_RAIDS.values().forEach(raid -> {
            discardRaiders(server, raid);
            removeAirship(server, raid);
            RaiderSquadMemory.removeRaid(raid.id());
            raid.setPhase(AirshipRaidPhase.FINISHED);
        });
        ACTIVE_RAIDS.clear();
        if (count > 0) {
            server.getPlayerList().broadcastSystemMessage(Component.literal("[飛行船襲撃] 全襲撃を停止しました。"), false);
        }
        return count;
    }

    public static Collection<AirshipRaidInstance> activeRaids() {
        return List.copyOf(ACTIVE_RAIDS.values());
    }

    public static Optional<AirshipRaidInstance> getRaid(int id) {
        return Optional.ofNullable(ACTIVE_RAIDS.get(id));
    }

    public static boolean isActiveRaid(int id) {
        return ACTIVE_RAIDS.containsKey(id);
    }

    public static void onRaiderDeath(int raidId, java.util.UUID raiderId) {
        AirshipRaidInstance raid = ACTIVE_RAIDS.get(raidId);
        if (raid != null) raid.raiderIds().remove(raiderId);
    }

    public static void damageAirship(MinecraftServer server, UUID shipId, BlockPos hitPos,
                                     BlockState state, float damage) {
        AirshipRaidInstance raid = ACTIVE_RAIDS.values().stream()
                .filter(candidate -> shipId.equals(candidate.shipId()))
                .findFirst().orElse(null);
        if (raid == null || (raid.phase() != AirshipRaidPhase.APPROACH && raid.phase() != AirshipRaidPhase.WARNING)) return;
        raid.damageHull(Math.min(45.0F, damage * 0.65F));
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (blockId.equals("aeronautics:levitite") && raid.damageComponent(hitPos, damage) >= 100.0F) {
            ServerLevel hitLevel = server.getLevel(raid.levelKey());
            if (hitLevel != null) hitLevel.removeBlock(hitPos, false);
            raid.destroyCore();
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "[飛行船襲撃] 浮力コア破壊 " + raid.destroyedCores() + "/4"), false);
        }
        if (raid.destroyedCores() >= 4 || raid.hullIntegrity() <= 0.0F) beginCrash(server, raid);
    }

    private static void beginCrash(MinecraftServer server, AirshipRaidInstance raid) {
        if (raid.phase() == AirshipRaidPhase.DISABLED || raid.phase() == AirshipRaidPhase.CRASHING) return;
        long now = server.overworld().getGameTime();
        raid.setPhase(AirshipRaidPhase.DISABLED, now);
        discardRaiders(server, raid);
        raid.raiderIds().clear();
        ServerLevel level = server.getLevel(raid.levelKey());
        if (level != null && raid.shipId() != null) SableAirshipController.beginCrash(level, raid.shipId(), raid.destroyedCores());
        raid.setPhase(AirshipRaidPhase.CRASHING, now);
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "[飛行船襲撃] #" + raid.id() + " 撃墜確認。墜落地点を警戒してください。"), false);
        ServerPlayer target = server.getPlayerList().getPlayer(raid.targetId());
        if (target != null) {
            PacketDistributor.sendToPlayer(target, new S2C_AnnouncementPacket("敵飛行船を撃墜！ 残骸から物資を回収できます"));
            target.playNotifySound(ModSounds.SERVER_NOTICE.get(), SoundSource.MASTER, 1.3F, 0.65F);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tickRaids(server);
        if (server.getTickCount() % 20 == 0) {
            tickAutomaticScheduler(server);
        }
    }

    private static void tickRaids(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        List<Integer> completed = new ArrayList<>();
        for (AirshipRaidInstance raid : ACTIVE_RAIDS.values()) {
            long age = now - raid.startedAt();
            tickAirship(server, raid);
            if (age >= 200 && raid.phase() == AirshipRaidPhase.APPROACH) raid.setPhase(AirshipRaidPhase.WARNING);
            if (age >= 400 && raid.phase() == AirshipRaidPhase.WARNING) {
                raid.setPhase(AirshipRaidPhase.DEPLOYING);
                deployRaiders(server, raid);
                raid.setPhase(AirshipRaidPhase.COMBAT);
            }
            if (raid.phase() == AirshipRaidPhase.CRASHING) {
                tickCrash(server, raid, now);
            }
            if (raid.phase() == AirshipRaidPhase.WRECKED) {
                raid.setPhase(AirshipRaidPhase.SALVAGE, now);
                spawnCrashSurvivors(server, raid);
                server.getPlayerList().broadcastSystemMessage(Component.literal(
                        "[飛行船襲撃] #" + raid.id() + " 残骸を15分間サルベージできます。"), false);
            }
            if (raid.phase() == AirshipRaidPhase.SALVAGE) {
                long remaining = SALVAGE_TIME_LIMIT - (now - raid.phaseStartedAt());
                if (remaining <= 2400L && !raid.salvageWarningSent()) {
                    raid.setSalvageWarningSent();
                    server.getPlayerList().broadcastSystemMessage(Component.literal(
                            "[飛行船襲撃] #" + raid.id() + " 残骸消滅まで残り2分。"), false);
                }
                if (remaining <= 0L && !playersNearWreck(server, raid)) {
                    removeAirship(server, raid);
                    raid.setPhase(AirshipRaidPhase.FINISHED, now);
                    RaiderSquadMemory.removeRaid(raid.id());
                    completed.add(raid.id());
                }
            }
            if (raid.phase() == AirshipRaidPhase.COMBAT && raid.raiderIds().isEmpty()) {
                raid.setPhase(AirshipRaidPhase.RETREATING);
                RETREAT_STARTED.put(raid.id(), now);
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[飛行船襲撃] #" + raid.id() + " を撃退しました。"), false);
            }
            if (raid.phase() != AirshipRaidPhase.RETREATING && raid.phase() != AirshipRaidPhase.FINISHED
                    && raid.phase() != AirshipRaidPhase.CRASHING && raid.phase() != AirshipRaidPhase.WRECKED
                    && raid.phase() != AirshipRaidPhase.SALVAGE
                    && age >= RAID_TIME_LIMIT) {
                discardRaiders(server, raid);
                raid.raiderIds().clear();
                raid.setPhase(AirshipRaidPhase.RETREATING);
                RETREAT_STARTED.put(raid.id(), now);
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("[飛行船襲撃] #" + raid.id() + " は制限時間に達したため終了します。"), false);
                ServerPlayer target = server.getPlayerList().getPlayer(raid.targetId());
                if (target != null) {
                    PacketDistributor.sendToPlayer(target,
                            new S2C_AnnouncementPacket("敵性飛行船が撤退を開始しました"));
                }
            }
            if (raid.phase() == AirshipRaidPhase.RETREATING
                    && now - RETREAT_STARTED.getOrDefault(raid.id(), now) >= 200L) {
                removeAirship(server, raid);
                raid.setPhase(AirshipRaidPhase.FINISHED);
                RETREAT_STARTED.remove(raid.id());
                RaiderSquadMemory.removeRaid(raid.id());
                completed.add(raid.id());
            }
        }
        completed.forEach(ACTIVE_RAIDS::remove);
    }

    private static void tickCrash(MinecraftServer server, AirshipRaidInstance raid, long now) {
        ServerLevel level = server.getLevel(raid.levelKey());
        if (level == null || raid.shipId() == null) return;
        if (!SableAirshipController.exists(level, raid.shipId())) {
            raid.setPhase(AirshipRaidPhase.FINISHED, now);
            return;
        }
        SableAirshipController.continueCrash(level, raid.shipId());
        Vector3d position = SableAirshipController.position(level, raid.shipId());
        if (position == null) return;
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(position.x, 0.0D, position.z));
        if (position.y <= surface.getY() + 12.0D || now - raid.phaseStartedAt() >= 400L) {
            raid.setPhase(AirshipRaidPhase.WRECKED, now);
        }
    }

    private static boolean playersNearWreck(MinecraftServer server, AirshipRaidInstance raid) {
        ServerLevel level = server.getLevel(raid.levelKey());
        if (level == null || raid.shipId() == null) return false;
        Vector3d position = SableAirshipController.position(level, raid.shipId());
        if (position == null) return false;
        return level.players().stream().anyMatch(player -> player.distanceToSqr(position.x, position.y, position.z) <= 48.0D * 48.0D);
    }

    private static void spawnCrashSurvivors(MinecraftServer server, AirshipRaidInstance raid) {
        int count = switch (raid.difficulty()) {
            case NORMAL -> 1;
            case ELITE -> 2;
            case LARGE -> 4;
        };
        deployRaiders(server, raid, count);
    }

    private static void deployRaiders(MinecraftServer server, AirshipRaidInstance raid) {
        int count = switch (raid.difficulty()) {
            case NORMAL -> 4;
            case ELITE -> 6;
            case LARGE -> 9;
        };
        deployRaiders(server, raid, count);
    }

    private static void deployRaiders(MinecraftServer server, AirshipRaidInstance raid, int count) {
        ServerPlayer target = server.getPlayerList().getPlayer(raid.targetId());
        if (target == null) {
            raid.setPhase(AirshipRaidPhase.FINISHED);
            return;
        }
        ServerLevel level = target.serverLevel();
        Vector3d drop = raid.shipId() == null ? null : SableAirshipController.dropPosition(level, raid.shipId());
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            double radius = 1.5D + level.random.nextDouble() * 1.5D;
            int x;
            int z;
            int y;
            if (drop != null) {
                x = Mth.floor(drop.x + Math.cos(angle) * radius);
                z = Mth.floor(drop.z + Math.sin(angle) * radius);
                y = Mth.floor(drop.y) - i / 3;
            } else {
                x = Mth.floor(target.getX() + Math.cos(angle) * 14.0D);
                z = Mth.floor(target.getZ() + Math.sin(angle) * 14.0D);
                BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                y = Math.min(level.getMaxBuildHeight() - 5, Math.max(Mth.floor(target.getY()) + 35, surface.getY() + 40));
            }
            AirshipRaiderEntity raider = ModEntities.AIRSHIP_RAIDER.get().create(level);
            if (raider == null) continue;
            raider.moveTo(x + 0.5D, y, z + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
            RaiderRole role = raid.difficulty() == AirshipRaidDifficulty.LARGE && i == 0
                    ? RaiderRole.HEAVY
                    : ((i + raid.id()) % (raid.difficulty() == AirshipRaidDifficulty.NORMAL ? 4 : 3) == 0
                    ? RaiderRole.FLANKER : RaiderRole.RIFLEMAN);
            raider.setRole(role);
            raider.equipRaidLoadout(raid.difficulty());
            raider.setRaidId(raid.id());
            raider.setTarget(target);
            if (level.addFreshEntity(raider)) {
                raid.addRaider(raider.getUUID());
            }
        }
        PacketDistributor.sendToPlayer(target, new S2C_AnnouncementPacket("敵部隊が降下を開始しました"));
        target.playNotifySound(ModSounds.SERVER_NOTICE.get(), SoundSource.MASTER, 1.0F, 1.05F);
    }

    private static void discardRaiders(MinecraftServer server, AirshipRaidInstance raid) {
        for (ServerLevel level : server.getAllLevels()) {
            for (java.util.UUID id : List.copyOf(raid.raiderIds())) {
                net.minecraft.world.entity.Entity entity = level.getEntity(id);
                if (entity != null) entity.discard();
            }
        }
    }

    private static void tickAirship(MinecraftServer server, AirshipRaidInstance raid) {
        if (raid.shipId() == null) return;
        ServerLevel level = server.getLevel(raid.levelKey());
        if (level == null) return;
        ServerPlayer target = server.getPlayerList().getPlayer(raid.targetId());
        Vector3d destination;
        double step;
        if (raid.phase() == AirshipRaidPhase.CRASHING || raid.phase() == AirshipRaidPhase.WRECKED
                || raid.phase() == AirshipRaidPhase.SALVAGE || raid.phase() == AirshipRaidPhase.DISABLED) {
            return;
        } else if (raid.phase() == AirshipRaidPhase.RETREATING) {
            Vector3d current = SableAirshipController.dropPosition(level, raid.shipId());
            if (current == null) return;
            destination = current.add(0.0D, 80.0D, 80.0D);
            step = 0.5D;
        } else if (target != null) {
            destination = new Vector3d(target.getX(), Math.min(level.getMaxBuildHeight() - 12, target.getY() + 65.0D), target.getZ());
            step = raid.phase() == AirshipRaidPhase.APPROACH ? 0.3D : 0.15D;
        } else {
            return;
        }
        SableAirshipController.moveToward(level, raid.shipId(), destination, step);
    }

    private static void removeAirship(MinecraftServer server, AirshipRaidInstance raid) {
        if (raid.shipId() == null) return;
        ServerLevel level = server.getLevel(raid.levelKey());
        if (level != null) SableAirshipController.remove(level, raid.shipId());
    }

    private static void tickAutomaticScheduler(MinecraftServer server) {
        AirshipRaidSavedData data = AirshipRaidSavedData.get(server);
        if (!data.isAutomaticEnabled() || !ACTIVE_RAIDS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        if (now - data.getLastAutomaticCheck() < AUTOMATIC_CHECK_INTERVAL) return;
        data.setLastAutomaticCheck(now);
        if (ThreadLocalRandom.current().nextDouble() >= AUTOMATIC_CHANCE) return;

        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GameType mode = player.gameMode.getGameModeForPlayer();
            boolean playable = mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
            long lastRaid = data.getLastRaidTime(player.getUUID());
            if (playable && (lastRaid == Long.MIN_VALUE || now - lastRaid >= PLAYER_COOLDOWN)) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) return;
        ServerPlayer target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        start(server, target, chooseDifficulty(server), true);
    }

    private static AirshipRaidDifficulty chooseDifficulty(MinecraftServer server) {
        int online = server.getPlayerCount();
        if (online >= 8) return AirshipRaidDifficulty.LARGE;
        if (online >= 4) return AirshipRaidDifficulty.ELITE;
        return AirshipRaidDifficulty.NORMAL;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_RAIDS.clear();
        RETREAT_STARTED.clear();
    }
}
