package com.ruskserver.moveearth_addtional.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_PvpHudPacket;
import com.ruskserver.moveearth_addtional.network.S2C_PvpKillcamPacket;
import com.ruskserver.moveearth_addtional.network.S2C_PvpTeamPacket;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class PvpMatchManager {
    public static final ResourceKey<Level> ARENA = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_arena"));
    public static final PvpMatchManager INSTANCE = new PvpMatchManager();
    private static final int WIN_SCORE = 180;
    private static final int MATCH_TICKS = 10 * 60 * 20;
    private static final int RESPAWN_TICKS = 80;
    private static final int FINISHED_TICKS = 100;
    private static final int REWARD_KILL_COOLDOWN = 60 * 20;
    private static final int MIN_REWARD_TEAM_SIZE = 2;
    private static final int MIN_INFINITE_RESERVE_AMMO = 120;

    /** Includes queued and active players. Insertion order is used for deterministic balancing. */
    private final Map<UUID, PvpTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, Integer> queuedSlots = new HashMap<>();
    private final Map<UUID, PvpPlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, ItemStack> matchGuns = new HashMap<>();
    private final Map<UUID, RespawnState> respawns = new HashMap<>();
    private final Map<KillPair, Integer> rewardedKills = new HashMap<>();
    private PvpPhase phase = PvpPhase.IDLE;
    private int redScore;
    private int blueScore;
    private int ticksLeft;
    private int syncTicker;
    private boolean matchResultsRecorded;

    private PvpMatchManager() {}

    public boolean isParticipant(ServerPlayer player) { return teams.containsKey(player.getUUID()); }
    public boolean isQueued(ServerPlayer player) { return queuedSlots.containsKey(player.getUUID()); }
    public boolean isActive(ServerPlayer player) { return snapshots.containsKey(player.getUUID()); }
    public PvpTeam team(ServerPlayer player) { return teams.get(player.getUUID()); }
    public PvpPhase phase() { return phase; }
    public int redScore() { return redScore; }
    public int blueScore() { return blueScore; }
    public int ticksLeft() { return ticksLeft; }

    /** Queue registration only. The player's inventory and position are untouched until start(). */
    public boolean join(ServerPlayer player, int hotbarSlot) {
        PvpArenaSavedData arena = PvpArenaSavedData.get(player.server);
        if (!arena.hosting()) {
            player.sendSystemMessage(Component.literal("現在PvPイベントは開催されていません。"));
            return false;
        }
        if (phase == PvpPhase.RUNNING || phase == PvpPhase.FINISHED || isActive(player)) {
            player.sendSystemMessage(Component.literal("試合進行中はキューへ参加できません。"));
            return false;
        }
        if (hotbarSlot < 0 || hotbarSlot > 8) return false;
        ItemStack gun = player.getInventory().getItem(hotbarSlot);
        if (gun.isEmpty() || IGun.getIGunOrNull(gun) == null) {
            player.sendSystemMessage(Component.literal("持ち込むTaCZ銃をホットバーから1本選んでください。"));
            return false;
        }

        if (isQueued(player)) {
            queuedSlots.put(player.getUUID(), hotbarSlot);
            player.sendSystemMessage(Component.literal("持ち込み銃の選択を更新しました。"));
            return true;
        }
        PvpTeam assigned = count(PvpTeam.RED, false) <= count(PvpTeam.BLUE, false) ? PvpTeam.RED : PvpTeam.BLUE;
        teams.put(player.getUUID(), assigned);
        queuedSlots.put(player.getUUID(), hotbarSlot);
        phase = PvpPhase.WAITING;
        player.sendSystemMessage(Component.literal("PvPキューに参加しました。試合開始までは通常どおり行動できます。"));
        return true;
    }

    public void leave(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean wasActive = isActive(player) || PvpSessionSavedData.get(player.server).contains(id);
        teams.remove(id);
        queuedSlots.remove(id);
        respawns.remove(id);
        matchGuns.remove(id);
        if (wasActive) restore(player);
        clearClientState(player);

        if (phase == PvpPhase.RUNNING && (count(PvpTeam.RED, true) == 0 || count(PvpTeam.BLUE, true) == 0)) {
            finish(player.server, "対戦相手が退出したため試合終了");
        } else if (teams.isEmpty()) {
            resetRuntime();
        }
    }

    public boolean start(MinecraftServer server) {
        PvpArenaSavedData arenaData = PvpArenaSavedData.get(server);
        ServerLevel arena = server.getLevel(ARENA);
        if (!arenaData.hosting()) return rejectStart(server, "PvPイベントが開催状態ではありません。");
        if (phase == PvpPhase.RUNNING || phase == PvpPhase.FINISHED) return rejectStart(server, "すでに試合進行中です。");
        if (arena == null) return rejectStart(server, "PvPディメンションを読み込めません。");
        if (!arenaData.configured()) return rejectStart(server,
                "場所設定が不足しています。redspawn / bluespawn / hill1 / hill2 を設定してください。");

        removeOfflineQueueMembers(server);
        removeInvalidGunSelections(server);
        rebalanceTeams();
        if (teams.size() < 2 || count(PvpTeam.RED, false) == 0 || count(PvpTeam.BLUE, false) == 0) {
            return rejectStart(server, "試合開始には有効な銃を選択したオンライン参加者が2人以上必要です。");
        }

        PvpSessionSavedData sessions = PvpSessionSavedData.get(server);
        Map<UUID, ItemStack> startingGuns = new HashMap<>();
        for (ServerPlayer player : participants(server, false)) {
            int slot = queuedSlots.getOrDefault(player.getUUID(), -1);
            ItemStack selectedGun = slot >= 0 ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
            if (selectedGun.isEmpty() || IGun.getIGunOrNull(selectedGun) == null) continue;

            PvpPlayerSnapshot snapshot = new PvpPlayerSnapshot(player);
            snapshots.put(player.getUUID(), snapshot);
            sessions.put(player.getUUID(), snapshot);
            startingGuns.put(player.getUUID(), selectedGun.copy());
        }
        // Persist both the original vanilla player data and our recovery copy before replacing any inventory.
        server.saveEverything(false, true, false);

        for (ServerPlayer player : participants(server, true)) {
            ItemStack carriedGun = startingGuns.getOrDefault(player.getUUID(), ItemStack.EMPTY);
            matchGuns.put(player.getUUID(), carriedGun.copy());

            player.closeContainer();
            player.getInventory().clearContent();
            player.getInventory().setItem(0, carriedGun);
            player.getInventory().selected = 0;
            player.removeAllEffects();
            player.setGameMode(GameType.SURVIVAL);
            assignScoreboardTeam(player, team(player));
            giveKit(player);
            resetVitals(player);
            BlockPos spawn = team(player) == PvpTeam.RED ? arenaData.redSpawn() : arenaData.blueSpawn();
            teleport(player, arena, spawn);
        }
        queuedSlots.clear();
        phase = PvpPhase.RUNNING;
        redScore = blueScore = 0;
        ticksLeft = MATCH_TICKS;
        syncTicker = 0;
        matchResultsRecorded = false;
        cleanupArenaMobs(arena);
        syncTeams(server);
        syncHud(server, "争奪中");
        broadcastToParticipants(server, Component.literal("PvP試合開始！ 丘を占領して180ポイントを獲得してください。"));
        return true;
    }

    public void stop(MinecraftServer server) {
        List<ServerPlayer> online = participants(server, true);
        for (ServerPlayer player : online) {
            restore(player);
            clearClientState(player);
        }
        resetRuntime();
    }

    public void tick(MinecraftServer server) {
        for (ServerPlayer player : participants(server, true)) {
            if (!player.level().dimension().equals(ARENA)) {
                PvpArenaSavedData data = PvpArenaSavedData.get(server);
                BlockPos spawn = team(player) == PvpTeam.RED ? data.redSpawn() : data.blueSpawn();
                teleport(player, server.getLevel(ARENA), spawn);
            }
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
            player.getFoodData().setExhaustion(0.0F);
            enforceLoadout(player);
        }

        if (phase == PvpPhase.FINISHED) {
            if (--ticksLeft <= 0) stop(server);
            return;
        }
        if (phase != PvpPhase.RUNNING) return;
        if (--ticksLeft <= 0) {
            finish(server, winnerText());
            return;
        }

        tickRespawns(server);
        if (count(PvpTeam.RED, true) == 0 || count(PvpTeam.BLUE, true) == 0) {
            finish(server, "対戦相手がいないため試合終了");
            return;
        }

        PvpArenaSavedData data = PvpArenaSavedData.get(server);
        int red = 0;
        int blue = 0;
        for (ServerPlayer player : participants(server, true)) {
            if (respawns.containsKey(player.getUUID()) || !player.level().dimension().equals(ARENA)) continue;
            if (inside(player.blockPosition(), data.hillMin(), data.hillMax())) {
                if (team(player) == PvpTeam.RED) red++; else blue++;
            }
        }
        String status = red > 0 && blue > 0 ? "争奪中" : red > 0 ? "RED 制圧中" : blue > 0 ? "BLUE 制圧中" : "無人";
        if (server.getTickCount() % 20 == 0) {
            boolean rewardEligible = rewardsEnabled();
            if (red > 0 && blue == 0) {
                redScore++;
                if (rewardEligible) recordZoneForTeam(server, PvpTeam.RED, data);
            }
            if (blue > 0 && red == 0) {
                blueScore++;
                if (rewardEligible) recordZoneForTeam(server, PvpTeam.BLUE, data);
            }
            if (redScore >= WIN_SCORE || blueScore >= WIN_SCORE) {
                finish(server, winnerText());
                return;
            }
        }
        if (++syncTicker >= 10) {
            syncTicker = 0;
            syncHud(server, status);
        }
        if (server.getTickCount() % 200 == 0) {
            ServerLevel arena = server.getLevel(ARENA);
            if (arena != null) cleanupArenaMobs(arena);
            rewardedKills.entrySet().removeIf(entry -> server.getTickCount() - entry.getValue() > REWARD_KILL_COOLDOWN);
        }
    }

    public void tickNonParticipant(ServerPlayer player) {
        if (player.level().dimension().equals(ARENA) && !isActive(player)
                && !player.createCommandSourceStack().hasPermission(2)) {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            teleport(player, overworld, spawn);
            player.sendSystemMessage(Component.literal("PvPアリーナには試合参加者のみ入場できます。"));
        }
    }

    public void eliminate(ServerPlayer victim, ServerPlayer killer) {
        if (phase != PvpPhase.RUNNING || !isActive(victim) || respawns.containsKey(victim.getUUID())) return;
        if (killer != null && isActive(killer) && team(killer) != team(victim)
                && canRewardKill(killer, victim)) {
            PvpRewardData.get(victim.server).recordKill(killer, killer.distanceToSqr(victim) <= 64.0D);
        }

        double targetX = killer == null ? victim.getX() : killer.getX();
        double targetY = killer == null ? victim.getEyeY() : killer.getEyeY();
        double targetZ = killer == null ? victim.getZ() : killer.getZ();
        float yaw = lookYaw(victim.getX(), victim.getZ(), targetX, targetZ);
        float pitch = lookPitch(victim.getX(), victim.getEyeY(), victim.getZ(), targetX, targetY, targetZ);
        respawns.put(victim.getUUID(), new RespawnState(RESPAWN_TICKS, victim.getX(), victim.getY(), victim.getZ(), yaw, pitch));

        victim.setHealth(victim.getMaxHealth());
        victim.setAbsorptionAmount(0.0F);
        victim.removeAllEffects();
        victim.setInvulnerable(true);
        victim.setDeltaMovement(0, 0, 0);
        victim.setGameMode(GameType.SPECTATOR);
        victim.setCamera(victim);
        victim.teleportTo(victim.serverLevel(), victim.getX(), victim.getY(), victim.getZ(), yaw, pitch);

        String killerName = killer == null ? "不明" : killer.getGameProfile().getName();
        UUID killerId = killer == null ? new UUID(0L, 0L) : killer.getUUID();
        PacketDistributor.sendToPlayer(victim,
                new S2C_PvpKillcamPacket(killerId, killerName, targetX, targetY, targetZ, RESPAWN_TICKS));
        Component notice = Component.literal(victim.getGameProfile().getName() + " は " + killerName + " に倒された");
        broadcastToParticipants(victim.server, notice);
    }

    public void recoverIfNeeded(ServerPlayer player) {
        if (isActive(player)) return;
        if (!PvpSessionSavedData.get(player.server).contains(player.getUUID())) return;
        teams.remove(player.getUUID());
        queuedSlots.remove(player.getUUID());
        restore(player);
        clearClientState(player);
        player.sendSystemMessage(Component.literal("中断されたPvPセッションから所持品と状態を復旧しました。"));
    }

    public void serverStopped() {
        resetRuntime();
    }

    private void tickRespawns(MinecraftServer server) {
        Iterator<Map.Entry<UUID, RespawnState>> iterator = respawns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RespawnState> entry = iterator.next();
            RespawnState state = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            if (state.ticks > 1) {
                state.ticks--;
                if (player.level().dimension().equals(ARENA)) {
                    player.teleportTo(player.serverLevel(), state.x, state.y, state.z, state.yaw, state.pitch);
                }
                continue;
            }
            iterator.remove();
            player.setCamera(player);
            player.setGameMode(GameType.SURVIVAL);
            player.setInvulnerable(false);
            player.removeAllEffects();
            resetVitals(player);
            refillGun(player);
            PvpArenaSavedData data = PvpArenaSavedData.get(server);
            BlockPos spawn = team(player) == PvpTeam.RED ? data.redSpawn() : data.blueSpawn();
            teleport(player, server.getLevel(ARENA), spawn);
        }
    }

    private void finish(MinecraftServer server, String message) {
        if (phase == PvpPhase.FINISHED) return;
        recordMatchResults(server);
        phase = PvpPhase.FINISHED;
        ticksLeft = FINISHED_TICKS;
        respawns.clear();
        forEachPlayer(server, true, player -> {
            player.setCamera(player);
            player.setInvulnerable(true);
        });
        syncHud(server, message);
        broadcastToParticipants(server, Component.literal(message));
    }

    private String winnerText() {
        return redScore == blueScore ? "引き分け" : redScore > blueScore ? "RED 勝利" : "BLUE 勝利";
    }

    private void recordMatchResults(MinecraftServer server) {
        if (matchResultsRecorded || !rewardsEnabled()) return;
        matchResultsRecorded = true;
        PvpTeam winner = redScore == blueScore ? null : redScore > blueScore ? PvpTeam.RED : PvpTeam.BLUE;
        PvpRewardData rewards = PvpRewardData.get(server);
        for (ServerPlayer player : participants(server, true)) {
            rewards.recordMatchResult(player, winner != null && team(player) == winner);
        }
    }

    private void giveKit(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, protectionFour(player, Items.IRON_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, protectionFour(player, Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, protectionFour(player, Items.IRON_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, protectionFour(player, Items.IRON_BOOTS));
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        refillGun(player);
    }

    private ItemStack protectionFour(ServerPlayer player, Item item) {
        ItemStack stack = new ItemStack(item);
        var protection = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        stack.enchant(protection, 4);
        return stack;
    }

    private static void resetVitals(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0.0F);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.getFoodData().setExhaustion(0.0F);
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.fallDistance = 0.0F;
    }

    private void enforceLoadout(ServerPlayer player) {
        ItemStack gunStack = ItemStack.EMPTY;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (!candidate.isEmpty() && IGun.getIGunOrNull(candidate) != null) {
                gunStack = candidate;
                break;
            }
        }
        if (gunStack.isEmpty()) gunStack = matchGuns.getOrDefault(player.getUUID(), ItemStack.EMPTY).copy();

        // clearContent() also empties the armor list. Calling it every tick caused
        // every armor piece to be removed and re-equipped 20 times per second,
        // repeatedly playing the equip sound. Only correct changed main-inventory
        // slots and missing equipment instead.
        if (!gunStack.isEmpty() && player.getInventory().getItem(0) != gunStack) {
            player.getInventory().setItem(0, gunStack);
        } else if (gunStack.isEmpty() && !player.getInventory().getItem(0).isEmpty()) {
            player.getInventory().setItem(0, ItemStack.EMPTY);
        }
        for (int slot = 1; slot < 36; slot++) {
            if (!player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        if (player.getInventory().selected != 0) player.getInventory().selected = 0;

        ensureArmor(player, EquipmentSlot.HEAD, Items.IRON_HELMET);
        ensureArmor(player, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
        ensureArmor(player, EquipmentSlot.LEGS, Items.IRON_LEGGINGS);
        ensureArmor(player, EquipmentSlot.FEET, Items.IRON_BOOTS);
        if (!player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }

        maintainInfiniteReserve(player, gunStack);
    }

    private void ensureArmor(ServerPlayer player, EquipmentSlot slot, Item expected) {
        if (!validArmor(player.getItemBySlot(slot), expected)) {
            player.setItemSlot(slot, protectionFour(player, expected));
        }
    }

    private static boolean validArmor(ItemStack stack, Item expected) {
        return !stack.isEmpty() && stack.is(expected);
    }

    private void refillGun(ServerPlayer player) {
        ItemStack gunStack = player.getInventory().getItem(0);
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun != null) {
            int magazineSize = magazineSize(gun, gunStack);
            gun.setCurrentAmmoCount(gunStack, magazineSize);
            gun.setBulletInBarrel(gunStack, true);
            int reserveAmmo = Math.max(MIN_INFINITE_RESERVE_AMMO, magazineSize * 4);
            gun.setMaxDummyAmmoAmount(gunStack, reserveAmmo);
            gun.setDummyAmmoAmount(gunStack, reserveAmmo);
            matchGuns.put(player.getUUID(), gunStack.copy());
        }
    }

    private void maintainInfiniteReserve(ServerPlayer player, ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;

        int reserveAmmo = Math.max(MIN_INFINITE_RESERVE_AMMO, magazineSize(gun, gunStack) * 4);
        boolean changed = false;
        if (gun.getMaxDummyAmmoAmount(gunStack) != reserveAmmo) {
            gun.setMaxDummyAmmoAmount(gunStack, reserveAmmo);
            changed = true;
        }
        if (gun.getDummyAmmoAmount(gunStack) < reserveAmmo) {
            gun.setDummyAmmoAmount(gunStack, reserveAmmo);
            changed = true;
        }
        if (changed) {
            matchGuns.put(player.getUUID(), gunStack.copy());
        }
    }

    private static int magazineSize(IGun gun, ItemStack gunStack) {
        return TimelessAPI.getCommonGunIndex(gun.getGunId(gunStack))
                .map(index -> index.getGunData().getAmmoAmount())
                .orElse(30);
    }

    private void restore(ServerPlayer player) {
        UUID id = player.getUUID();
        PvpSessionSavedData sessions = PvpSessionSavedData.get(player.server);
        PvpPlayerSnapshot snapshot = snapshots.remove(id);
        if (snapshot == null) snapshot = sessions.get(id);
        if (snapshot == null) return;

        player.closeContainer();
        player.setCamera(player);
        player.setInvulnerable(false);
        snapshot.restoreState(player);
        player.setGameMode(snapshot.gameMode);
        restoreScoreboardTeam(player, snapshot.scoreboardTeam);
        ServerLevel target = player.server.getLevel(snapshot.dimension);
        if (target == null) target = player.server.overworld();
        player.teleportTo(target, snapshot.x, snapshot.y, snapshot.z, snapshot.yaw, snapshot.pitch);
        sessions.remove(id);
    }

    private void syncHud(MinecraftServer server, String hillStatus) {
        S2C_PvpHudPacket packet = new S2C_PvpHudPacket(true, redScore, blueScore, WIN_SCORE, ticksLeft, hillStatus);
        forEachPlayer(server, true, player -> PacketDistributor.sendToPlayer(player, packet));
    }

    private static void assignScoreboardTeam(ServerPlayer player, PvpTeam side) {
        Scoreboard scoreboard = player.server.getScoreboard();
        String name = side == PvpTeam.RED ? "mea_pvp_red" : "mea_pvp_blue";
        PlayerTeam pvpTeam = scoreboard.getPlayerTeam(name);
        if (pvpTeam == null) pvpTeam = scoreboard.addPlayerTeam(name);
        pvpTeam.setColor(side == PvpTeam.RED ? ChatFormatting.RED : ChatFormatting.BLUE);
        pvpTeam.setAllowFriendlyFire(false);
        pvpTeam.setNameTagVisibility(Team.Visibility.NEVER);
        scoreboard.addPlayerToTeam(player.getScoreboardName(), pvpTeam);
    }

    private static void restoreScoreboardTeam(ServerPlayer player, String originalTeamName) {
        Scoreboard scoreboard = player.server.getScoreboard();
        scoreboard.removePlayerFromTeam(player.getScoreboardName());
        if (!originalTeamName.isEmpty()) {
            PlayerTeam original = scoreboard.getPlayerTeam(originalTeamName);
            if (original != null) scoreboard.addPlayerToTeam(player.getScoreboardName(), original);
        }
    }

    private void syncTeams(MinecraftServer server) {
        forEachPlayer(server, true, viewer -> {
            PvpTeam own = team(viewer);
            List<UUID> allies = teams.entrySet().stream()
                    .filter(entry -> entry.getValue() == own && !entry.getKey().equals(viewer.getUUID()))
                    .filter(entry -> snapshots.containsKey(entry.getKey()))
                    .map(Map.Entry::getKey)
                    .toList();
            PacketDistributor.sendToPlayer(viewer, new S2C_PvpTeamPacket(allies));
        });
    }

    private void clearClientState(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, S2C_PvpHudPacket.inactive());
        PacketDistributor.sendToPlayer(player, new S2C_PvpTeamPacket(List.of()));
    }

    private void recordZoneForTeam(MinecraftServer server, PvpTeam team, PvpArenaSavedData data) {
        for (ServerPlayer player : participants(server, true)) {
            if (team(player) == team && !respawns.containsKey(player.getUUID())
                    && inside(player.blockPosition(), data.hillMin(), data.hillMax())) {
                PvpRewardData.get(server).recordZoneSecond(player);
            }
        }
    }

    private boolean canRewardKill(ServerPlayer killer, ServerPlayer victim) {
        if (!rewardsEnabled()) return false;
        KillPair pair = new KillPair(killer.getUUID(), victim.getUUID());
        int now = killer.server.getTickCount();
        Integer previous = rewardedKills.get(pair);
        if (previous != null && now - previous < REWARD_KILL_COOLDOWN) return false;
        rewardedKills.put(pair, now);
        return true;
    }

    private boolean rewardsEnabled() {
        return count(PvpTeam.RED, true) >= MIN_REWARD_TEAM_SIZE
                && count(PvpTeam.BLUE, true) >= MIN_REWARD_TEAM_SIZE;
    }

    private void removeOfflineQueueMembers(MinecraftServer server) {
        teams.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        queuedSlots.keySet().retainAll(teams.keySet());
    }

    private void removeInvalidGunSelections(MinecraftServer server) {
        for (ServerPlayer player : new ArrayList<>(participants(server, false))) {
            int slot = queuedSlots.getOrDefault(player.getUUID(), -1);
            ItemStack gun = slot >= 0 && slot < 9 ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
            if (gun.isEmpty() || IGun.getIGunOrNull(gun) == null) {
                player.sendSystemMessage(Component.literal("選択したTaCZ銃が見つからないためPvPキューから外れました。"));
                teams.remove(player.getUUID());
                queuedSlots.remove(player.getUUID());
            }
        }
    }

    private void rebalanceTeams() {
        int index = 0;
        for (UUID id : teams.keySet()) teams.put(id, index++ % 2 == 0 ? PvpTeam.RED : PvpTeam.BLUE);
    }

    private boolean rejectStart(MinecraftServer server, String reason) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("[PvP] " + reason), false);
        return false;
    }

    private int count(PvpTeam team, boolean activeOnly) {
        return (int) teams.entrySet().stream()
                .filter(entry -> entry.getValue() == team)
                .filter(entry -> !activeOnly || snapshots.containsKey(entry.getKey()))
                .count();
    }

    private List<ServerPlayer> participants(MinecraftServer server, boolean activeOnly) {
        return teams.keySet().stream()
                .filter(id -> !activeOnly || snapshots.containsKey(id))
                .map(server.getPlayerList()::getPlayer)
                .filter(Objects::nonNull)
                .toList();
    }

    private void forEachPlayer(MinecraftServer server, boolean activeOnly, Consumer<ServerPlayer> action) {
        participants(server, activeOnly).forEach(action);
    }

    private static void broadcastToParticipants(MinecraftServer server, Component message) {
        INSTANCE.forEachPlayer(server, true, player -> player.sendSystemMessage(message));
    }

    private static void cleanupArenaMobs(ServerLevel arena) {
        for (Entity entity : arena.getAllEntities()) if (entity instanceof Mob) entity.discard();
    }

    private static boolean inside(BlockPos position, BlockPos first, BlockPos second) {
        return position.getX() >= Math.min(first.getX(), second.getX()) && position.getX() <= Math.max(first.getX(), second.getX())
                && position.getY() >= Math.min(first.getY(), second.getY()) && position.getY() <= Math.max(first.getY(), second.getY())
                && position.getZ() >= Math.min(first.getZ(), second.getZ()) && position.getZ() <= Math.max(first.getZ(), second.getZ());
    }

    private static void teleport(ServerPlayer player, ServerLevel level, BlockPos position) {
        if (level != null) player.teleportTo(level, position.getX() + .5, position.getY(), position.getZ() + .5,
                player.getYRot(), player.getXRot());
    }

    private static float lookYaw(double fromX, double fromZ, double toX, double toZ) {
        return (float) (Mth.atan2(toZ - fromZ, toX - fromX) * 180.0D / Math.PI) - 90.0F;
    }

    private static float lookPitch(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
        double horizontal = Math.sqrt(Mth.square(toX - fromX) + Mth.square(toZ - fromZ));
        return (float) -(Mth.atan2(toY - fromY, horizontal) * 180.0D / Math.PI);
    }

    private void resetRuntime() {
        phase = PvpPhase.IDLE;
        redScore = blueScore = ticksLeft = syncTicker = 0;
        teams.clear();
        queuedSlots.clear();
        snapshots.clear();
        matchGuns.clear();
        respawns.clear();
        rewardedKills.clear();
        matchResultsRecorded = false;
    }

    private static final class RespawnState {
        int ticks;
        final double x, y, z;
        final float yaw, pitch;

        private RespawnState(int ticks, double x, double y, double z, float yaw, float pitch) {
            this.ticks = ticks;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private record KillPair(UUID killer, UUID victim) {}
}
