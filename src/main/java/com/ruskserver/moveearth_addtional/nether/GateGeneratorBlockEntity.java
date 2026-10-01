package com.ruskserver.moveearth_addtional.nether;

import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Charges from shaft work, then opens a Nether gate: waves of strengthened Nether
 * enemies that the owning nation must clear within the time limit to earn shards.
 * Charging and fighting stop while the nation is locked by a Siege or the spot
 * has left its territory; a fight in progress then closes without a reward.
 */
public final class GateGeneratorBlockEntity extends KineticBlockEntity {
    // New values go last: clients read the ordinal.
    public enum Status { IDLE, TOO_SLOW, CHARGING, WAITING_FOR_MEMBER, FIGHTING, DISABLED, BLOCKED }

    private static final int PARTICIPANT_RADIUS = 32;
    private static final long BLOCKED_NOTICE_INTERVAL_TICKS = 30L * 20L;

    private UUID ownerNation;
    private long charge;
    private UUID encounterId;
    private long encounterEnd;
    private int wave;
    private final Set<UUID> mobs = new HashSet<>();
    private ServerBossEvent bossBar;
    /** True only for a fight started in this server session; one loaded from disk is abandoned. */
    private boolean encounterLive;
    private boolean operable = true;
    private long lastPresenceTick;
    private long lastBlockedNoticeTick = Long.MIN_VALUE;

    // Synced to clients for goggles.
    private Status status = Status.IDLE;
    private float chargeFraction;
    private int remainingSeconds;
    private int enemiesLeft;

    public GateGeneratorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    void setOwnerNation(UUID nation) {
        ownerNation = nation;
        setChanged();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !(level instanceof ServerLevel server)) return;
        long gameTime = server.getGameTime();
        if (encounterId != null && !encounterLive) {
            endEncounter(server, Outcome.ABANDONED);
        }
        if (gameTime % 20L == 0L) operable = canOperate(server);
        if (encounterId != null) {
            if (gameTime % 20L == 0L) tickEncounter(server, gameTime);
            return;
        }

        Status next;
        long required = NetherGateConfig.gateChargeWork();
        if (!operable) {
            next = Status.DISABLED;
        } else if (charge < required) {
            long step = isOverStressed() ? 0L : KineticWork.step(getSpeed(), NetherGateConfig.gateMinRpm());
            charge = Math.min(required, charge + step);
            if (step > 0L) setChanged();
            next = step > 0L ? Status.CHARGING : Math.abs(getSpeed()) > 0.0F ? Status.TOO_SLOW : Status.IDLE;
            if (step > 0L && gameTime % 10L == 0L) {
                server.sendParticles(ParticleTypes.PORTAL, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.1D,
                        worldPosition.getZ() + 0.5D, 3, 0.3D, 0.2D, 0.3D, 0.2D);
            }
        } else {
            next = status == Status.BLOCKED ? Status.BLOCKED : Status.WAITING_FOR_MEMBER;
            if (gameTime % 20L == 0L) {
                List<ServerPlayer> members = nearbyMembers(server);
                if (members.isEmpty()) {
                    next = Status.WAITING_FOR_MEMBER;
                } else {
                    NetherGateBattles.Verdict verdict = openVerdict(server);
                    if (verdict == NetherGateBattles.Verdict.ALLOWED) {
                        startEncounter(server, gameTime);
                        next = encounterId != null ? Status.FIGHTING : Status.IDLE;
                    } else {
                        next = Status.BLOCKED;
                        noticeBlocked(members, verdict, gameTime);
                    }
                }
            }
        }
        if (gameTime % 20L == 0L) publish(next, required);
    }

    private boolean canOperate(ServerLevel server) {
        if (ownerNation == null) return false;
        UUID controlling = TerritorySavedData.get(server.getServer())
                .controllingNation(server.getServer(), server.dimension().location(), worldPosition).orElse(null);
        return ownerNation.equals(controlling)
                && !SiegeSavedData.get(server.getServer()).isNationLocked(ownerNation);
    }

    private List<ServerPlayer> nearbyMembers(ServerLevel server) {
        NationSavedData nations = NationSavedData.get(server.getServer());
        double radius = NetherGateConfig.gateOpenRadius();
        List<ServerPlayer> members = new java.util.ArrayList<>();
        for (ServerPlayer player : server.players()) {
            if (player.isSpectator() || player.distanceToSqr(worldPosition.getCenter()) > radius * radius) continue;
            if (nations.nationIdFor(player.getUUID()).filter(ownerNation::equals).isPresent()) members.add(player);
        }
        return members;
    }

    private NetherGateBattles.Verdict openVerdict(ServerLevel server) {
        return NetherGateBattles.canOpen(NetherGateBattles.active(), ownerNation,
                server.dimension().location().toString(),
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                NetherGateConfig.maxBattlesPerNation(), NetherGateConfig.maxBattlesServerWide(),
                NetherGateConfig.minimumBattleDistance());
    }

    /** Tells the members waiting at the gate why it stays shut, at most every 30 seconds. */
    private void noticeBlocked(List<ServerPlayer> members, NetherGateBattles.Verdict verdict, long gameTime) {
        if (lastBlockedNoticeTick != Long.MIN_VALUE
                && gameTime - lastBlockedNoticeTick < BLOCKED_NOTICE_INTERVAL_TICKS) return;
        lastBlockedNoticeTick = gameTime;
        Component reason = switch (verdict) {
            case NATION_LIMIT -> Component.translatable("message.moveearth_addtional.nether_gate.blocked_nation",
                    NetherGateConfig.maxBattlesPerNation());
            case SERVER_LIMIT -> Component.translatable("message.moveearth_addtional.nether_gate.blocked_server",
                    NetherGateConfig.maxBattlesServerWide());
            case TOO_CLOSE -> Component.translatable("message.moveearth_addtional.nether_gate.blocked_nearby",
                    NetherGateConfig.minimumBattleDistance());
            case ALLOWED -> null;
        };
        if (reason == null) return;
        Component message = MoveEarthMessage.warning(reason);
        members.forEach(player -> player.sendSystemMessage(message));
    }

    private void startEncounter(ServerLevel server, long gameTime) {
        encounterId = UUID.randomUUID();
        encounterLive = true;
        encounterEnd = gameTime + NetherGateConfig.fightSeconds() * 20L;
        charge = 0L;
        wave = 0;
        mobs.clear();
        lastPresenceTick = gameTime;
        lastBlockedNoticeTick = Long.MIN_VALUE;
        NetherGateMobs.ACTIVE.put(encounterId, worldPosition);
        NetherGateBattles.register(new NetherGateBattles.Battle(encounterId, ownerNation,
                server.dimension().location().toString(),
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()));
        bossBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        server.playSound(null, worldPosition, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 0.7F);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.5D,
                worldPosition.getZ() + 0.5D, 80, 1.5D, 1.0D, 1.5D, 0.05D);
        notifyNearby(server, MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.nether_gate.opened", NetherGateConfig.fightSeconds() / 60)));
        participants(server).forEach(player -> com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player, com.ruskserver.moveearth_addtional.advancement.ModCriteria.NETHER_GATE_OPENED));
        setChanged();
        if (!spawnWave(server)) endEncounter(server, Outcome.NO_ENEMIES);
    }

    /**
     * Spawns the current wave. False when not one enemy could be added, such as when
     * another mod refuses the spawn; the fight is then called off rather than
     * counting an empty wave as cleared.
     */
    private boolean spawnWave(ServerLevel server) {
        boolean any = false;
        for (NetherGateWaves.Kind kind : NetherGateWaves.spawnOrder(wave, NetherGateConfig.fightEnemyCount())) {
            Mob mob = NetherGateMobs.spawn(server, kind, worldPosition, encounterId, server.random);
            if (mob != null) {
                mobs.add(mob.getUUID());
                any = true;
            }
        }
        return any;
    }

    private void tickEncounter(ServerLevel server, long gameTime) {
        if (!operable) {
            endEncounter(server, Outcome.INTERRUPTED);
            return;
        }
        List<ServerPlayer> present = participants(server);
        if (present.stream().anyMatch(player -> !player.isSpectator())) {
            lastPresenceTick = gameTime;
        } else if (NetherGateBattles.deserted(lastPresenceTick, gameTime, NetherGateConfig.desertedSeconds() * 20)) {
            endEncounter(server, Outcome.DESERTED);
            return;
        }
        double leash = NetherGateConfig.fightLeash();
        mobs.removeIf(id -> {
            Entity entity = server.getEntity(id);
            if (entity == null || !entity.isAlive()) return true;
            if (entity.distanceToSqr(worldPosition.getCenter()) > leash * leash) {
                BlockPos back = NetherGateMobs.spawnPosition(server, worldPosition, server.random,
                        entity instanceof net.minecraft.world.entity.monster.Blaze);
                entity.teleportTo(back.getX() + 0.5D, back.getY(), back.getZ() + 0.5D);
            }
            return false;
        });
        if (mobs.isEmpty()) {
            if (wave + 1 < NetherGateWaves.count()) {
                wave++;
                notifyNearby(server, MoveEarthMessage.info(Component.translatable(
                        "message.moveearth_addtional.nether_gate.next_wave", wave + 1, NetherGateWaves.count())));
                if (!spawnWave(server)) {
                    endEncounter(server, Outcome.NO_ENEMIES);
                    return;
                }
            } else {
                endEncounter(server, Outcome.CLEARED);
                return;
            }
        }
        if (gameTime >= encounterEnd) {
            endEncounter(server, Outcome.TIMED_OUT);
            return;
        }
        remainingSeconds = (int) Math.max(0L, (encounterEnd - gameTime) / 20L);
        enemiesLeft = mobs.size();
        if (bossBar != null) {
            bossBar.setName(Component.translatable("bossbar.moveearth_addtional.nether_gate",
                    wave + 1, NetherGateWaves.count(), enemiesLeft, remainingSeconds / 60,
                    String.format("%02d", remainingSeconds % 60)));
            bossBar.setProgress(Math.max(0.0F, Math.min(1.0F,
                    remainingSeconds / (float) NetherGateConfig.fightSeconds())));
            Set<ServerPlayer> near = new HashSet<>(present);
            for (ServerPlayer player : List.copyOf(bossBar.getPlayers())) {
                if (!near.contains(player)) bossBar.removePlayer(player);
            }
            near.forEach(bossBar::addPlayer);
        }
        publish(Status.FIGHTING, NetherGateConfig.gateChargeWork());
    }

    private enum Outcome { CLEARED, TIMED_OUT, INTERRUPTED, NO_ENEMIES, ABANDONED, DESERTED }

    private void endEncounter(ServerLevel server, Outcome outcome) {
        for (UUID id : mobs) {
            Entity entity = server.getEntity(id);
            if (entity != null) entity.discard();
        }
        mobs.clear();
        if (encounterId != null) NetherGateMobs.ACTIVE.remove(encounterId);
        NetherGateBattles.unregister(encounterId);
        if (bossBar != null) bossBar.removeAllPlayers();
        bossBar = null;
        encounterId = null;
        encounterLive = false;
        switch (outcome) {
            case CLEARED -> {
                int shards = NetherGateConfig.fightShards();
                if (shards > 0) {
                    ItemEntity drop = new ItemEntity(server, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.2D,
                            worldPosition.getZ() + 0.5D, new ItemStack(NetherGateRegistry.NETHER_SHARD.get(), shards));
                    drop.setDeltaMovement(0.0D, 0.2D, 0.0D);
                    server.addFreshEntity(drop);
                }
                server.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 1.0F);
                notifyNearby(server, MoveEarthMessage.success(Component.translatable(
                        "message.moveearth_addtional.nether_gate.cleared", shards)));
                participants(server).forEach(player -> com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player, com.ruskserver.moveearth_addtional.advancement.ModCriteria.NETHER_GATE_CLEARED));
            }
            case TIMED_OUT -> notifyNearby(server, MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.nether_gate.timed_out")));
            case INTERRUPTED -> notifyNearby(server, MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.nether_gate.interrupted")));
            case NO_ENEMIES -> notifyNearby(server, MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.nether_gate.no_enemies")));
            case ABANDONED -> { }
            // Nobody is near to read it there, so the nation's online members are told.
            case DESERTED -> notifyNation(server, MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.nether_gate.deserted", NetherGateConfig.desertedSeconds())));
        }
        server.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
        publish(Status.IDLE, NetherGateConfig.gateChargeWork());
        setChanged();
    }

    private List<ServerPlayer> participants(ServerLevel server) {
        return server.players().stream()
                .filter(player -> player.distanceToSqr(worldPosition.getCenter()) <= PARTICIPANT_RADIUS * PARTICIPANT_RADIUS)
                .toList();
    }

    private void notifyNearby(ServerLevel server, Component message) {
        participants(server).forEach(player -> player.sendSystemMessage(message));
    }

    private void notifyNation(ServerLevel server, Component message) {
        if (ownerNation == null) return;
        NationSavedData.get(server.getServer()).nation(ownerNation).ifPresent(nation ->
                nation.members().keySet().forEach(id -> {
                    ServerPlayer member = server.getServer().getPlayerList().getPlayer(id);
                    if (member != null) member.sendSystemMessage(message);
                }));
    }

    private void publish(Status next, long required) {
        float fraction = required <= 0L ? 0.0F : charge / (float) required;
        boolean changed = next != status || Math.abs(fraction - chargeFraction) >= 0.01F
                || next == Status.FIGHTING;
        status = next;
        chargeFraction = fraction;
        if (changed) sendData();
    }

    /** Server shutdown, chunk unload or the block breaking: the fight cannot continue. */
    @Override
    public void invalidate() {
        if (level instanceof ServerLevel server && encounterId != null) endEncounter(server, Outcome.ABANDONED);
        super.invalidate();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putInt("GateStatus", status.ordinal());
            tag.putFloat("GateCharge", chargeFraction);
            tag.putInt("GateSeconds", remainingSeconds);
            tag.putInt("GateEnemies", enemiesLeft);
            tag.putInt("GateWave", wave);
            return;
        }
        if (ownerNation != null) tag.putUUID("OwnerNation", ownerNation);
        tag.putLong("Charge", charge);
        if (encounterId != null) tag.putUUID("Encounter", encounterId);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            Status[] values = Status.values();
            status = values[Math.floorMod(tag.getInt("GateStatus"), values.length)];
            chargeFraction = tag.getFloat("GateCharge");
            remainingSeconds = tag.getInt("GateSeconds");
            enemiesLeft = tag.getInt("GateEnemies");
            wave = tag.getInt("GateWave");
            return;
        }
        ownerNation = tag.hasUUID("OwnerNation") ? tag.getUUID("OwnerNation") : null;
        charge = Math.max(0L, tag.getLong("Charge"));
        encounterId = tag.hasUUID("Encounter") ? tag.getUUID("Encounter") : null;
        encounterLive = false;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        tooltip.add(Component.literal("    ").append(Component.translatable(
                "goggles.moveearth_addtional.nether_gate.title").withStyle(ChatFormatting.GRAY)));
        Component line = switch (status) {
            case CHARGING, IDLE -> Component.translatable("goggles.moveearth_addtional.nether_gate.charge",
                    Math.round(chargeFraction * 100.0F));
            case TOO_SLOW -> Component.translatable("goggles.moveearth_addtional.nether_gate.too_slow",
                    NetherGateConfig.SPEC.isLoaded() ? NetherGateConfig.gateMinRpm() : 128);
            case WAITING_FOR_MEMBER -> Component.translatable("goggles.moveearth_addtional.nether_gate.ready");
            case BLOCKED -> Component.translatable("goggles.moveearth_addtional.nether_gate.blocked");
            case FIGHTING -> Component.translatable("goggles.moveearth_addtional.nether_gate.fighting",
                    wave + 1, NetherGateWaves.count(), enemiesLeft, remainingSeconds);
            case DISABLED -> Component.translatable("goggles.moveearth_addtional.nether_gate.disabled");
        };
        tooltip.add(Component.literal("    ").append(line.copy().withStyle(
                status == Status.DISABLED || status == Status.TOO_SLOW || status == Status.BLOCKED
                        ? ChatFormatting.RED : ChatFormatting.GOLD)));
        return true;
    }
}
