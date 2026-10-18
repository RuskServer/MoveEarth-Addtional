package com.ruskserver.moveearth_addtional.pvp;

import com.ruskserver.moveearth_addtional.pvp.deploy.PvpDeployPolicy;
import com.ruskserver.moveearth_addtional.pvp.deploy.PvpTacticalMap;
import com.ruskserver.moveearth_addtional.pvp.deploy.PvpTacticalSavedData;
import com.ruskserver.moveearth_addtional.pvp.deploy.PvpTacticalScanner;
import com.ruskserver.moveearth_addtional.pvp.loadout.PvpLoadoutDefinition;
import com.ruskserver.moveearth_addtional.pvp.loadout.PvpLoadoutSavedData;
import com.ruskserver.moveearth_addtional.pvp.map.PvpArenaSavedData;
import com.ruskserver.moveearth_addtional.pvp.map.PvpMapDefinition;
import com.ruskserver.moveearth_addtional.pvp.map.PvpMapGeometry;
import com.ruskserver.moveearth_addtional.pvp.map.PvpMapSavedData;
import com.ruskserver.moveearth_addtional.pvp.map.PvpMapVoteManager;
import com.ruskserver.moveearth_addtional.pvp.reward.PvpRewardData;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ModSounds;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpAllyPositionsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpDeployPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpEntryStatePacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpTacticalMapPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpHudPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpKillcamPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpResultPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpTeamPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpZonePacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_SyncLoadoutsPacket;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.*;
import java.util.function.Consumer;

public final class PvpMatchManager {
    public static final ResourceKey<Level> ARENA = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_arena"));
    public static final PvpMatchManager INSTANCE = new PvpMatchManager();
    private static final int WIN_SCORE = 180;
    private static final int MATCH_TICKS = 10 * 60 * 20;
    private static final int RESPAWN_TICKS = 80;
    private static final int FINISHED_TICKS = 100;
    private static final int RESULT_OVERLAY_TICKS = 60;
    private static final int REWARD_KILL_COOLDOWN = 60 * 20;
    private static final int MIN_REWARD_TEAM_SIZE = 2;
    private static final int MIN_INFINITE_RESERVE_AMMO = 120;
    private static final int COMBAT_FOOD_LEVEL = 18;
    private static final double PVP_MAX_HEALTH = 20.0D;
    private static final int MULTI_KILL_WINDOW_TICKS = 8 * 20;
    private static final int FINAL_STAND_TICKS = 60 * 20;
    /** Minimum spacing between one player's join/leave transitions. */
    private static final int ENTRY_COOLDOWN_TICKS = 5 * 20;
    /** Entry-count-only updates to players outside the match are spaced this far apart. */
    private static final int ENTRY_COUNT_SYNC_TICKS = 2 * 20;
    /** Zone packets go out on change; this re-sends an unchanged zone as a safety net. */
    private static final int ZONE_KEEPALIVE_TICKS = 5 * 20;

    /** Includes queued and active players. Insertion order is used for deterministic balancing. */
    private final Map<UUID, PvpTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, String> loadoutSelections = new HashMap<>();
    private final Map<UUID, PvpPlayerSnapshot> snapshots = new HashMap<>();
    private final Map<UUID, Integer> lastDamageTicks = new HashMap<>();
    private final Map<UUID, RespawnState> respawns = new HashMap<>();
    private final Map<KillPair, Integer> rewardedKills = new HashMap<>();
    private final Map<UUID, MatchStats> matchStats = new HashMap<>();
    private final Map<UUID, KillAnnouncementState> killAnnouncements = new HashMap<>();
    private final Map<UUID, UUID> lastKillerByVictim = new HashMap<>();
    /**
     * What a player set up last life and keeps into the next: each gun's fire mode by gun id, the hotbar slot
     * in hand, and the deploy point chosen. The kit is built anew at every deploy, which used to put every gun
     * back on its default fire mode and the selection back on the first slot.
     */
    private final Map<UUID, Map<ResourceLocation, com.tacz.guns.api.item.gun.FireMode>> fireModes = new HashMap<>();
    private final Map<UUID, Integer> heldSlots = new HashMap<>();
    private final Map<UUID, Long> lastDeployPoints = new HashMap<>();
    /** Ticks each active player has spent outside the battle area in a row. */
    private final Map<UUID, Integer> outOfBounds = new HashMap<>();
    private PvpPhase phase = PvpPhase.IDLE;
    private PvpMapDefinition activeMap;
    private PvpMapGeometry.Box activeBounds;
    private int redScore;
    private int blueScore;
    private int ticksLeft;
    private int syncTicker;
    private boolean matchResultsRecorded;
    private boolean firstBloodAnnounced;
    private boolean finalStandAnnounced;
    private PvpTeam announcedZoneController;
    private PvpZoneState zoneState = PvpZoneState.NEUTRAL;
    private int zoneRedPlayers;
    private int zoneBluePlayers;
    private final PvpEntryCooldown entryCooldown = new PvpEntryCooldown(ENTRY_COOLDOWN_TICKS);
    private final PvpEntryStateSync entryStateSync = new PvpEntryStateSync(ENTRY_COUNT_SYNC_TICKS);
    private boolean entryStateDirty;
    private final PvpChangeGate<HudKey> hudGate = new PvpChangeGate<>(0);
    private final PvpChangeGate<S2C_PvpZonePacket> zoneGate = new PvpChangeGate<>(ZONE_KEEPALIVE_TICKS);

    private PvpMatchManager() {
        // A scan finished during a match reaches the players already on its deploy screen.
        PvpTacticalScanner.INSTANCE.onFinished((server, map) -> {
            if (activeMap == null || !activeMap.id().equals(map.mapId())) return;
            S2C_PvpTacticalMapPacket packet = S2C_PvpTacticalMapPacket.of(map);
            forEachPlayer(server, true, player -> PacketDistributor.sendToPlayer(player, packet));
        });
    }

    public boolean isParticipant(ServerPlayer player) { return teams.containsKey(player.getUUID()); }
    public boolean isQueued(ServerPlayer player) { return isParticipant(player) && !isActive(player); }
    public boolean isActive(ServerPlayer player) { return snapshots.containsKey(player.getUUID()); }
    public boolean isRespawning(UUID playerId) { return respawns.containsKey(playerId); }
    public PvpTeam team(ServerPlayer player) { return teams.get(player.getUUID()); }

    public String selectedLoadoutId(ServerPlayer player) {
        return loadoutSelections.getOrDefault(player.getUUID(), "assault");
    }

    public PvpLoadoutDefinition selectedLoadout(ServerPlayer player) {
        String id = selectedLoadoutId(player);
        return PvpLoadoutSavedData.get(player.server).getOrDefault(id);
    }

    public PvpPhase phase() { return phase; }
    public PvpMapDefinition activeMap() { return activeMap; }
    public int redScore() { return redScore; }
    public int blueScore() { return blueScore; }
    public int ticksLeft() { return ticksLeft; }
    public int participantCount() { return teams.size(); }

    /** Registers for the next match, or immediately activates the player when a match is running. */
    public boolean join(ServerPlayer player, String loadoutId) {
        if (!PvpAdmissionPolicy.canUpdateSelection(isActive(player), eligibleForAdmission(player))) {
            player.sendSystemMessage(MoveEarthMessage.error("戦闘中・拘束中・ダウン中・死亡中・観戦中はPvPへ参加できません。"));
            return false;
        }
        PvpArenaSavedData arena = PvpArenaSavedData.get(player.server);
        if (!arena.hosting()) {
            player.sendSystemMessage(MoveEarthMessage.warning("現在PvPイベントは開催されていません。"));
            return false;
        }
        if (phase == PvpPhase.FINISHED) {
            player.sendSystemMessage(MoveEarthMessage.warning("現在はPvPへ参加できません。"));
            return false;
        }
        PvpLoadoutDefinition loadout = PvpLoadoutSavedData.get(player.server).getById(loadoutId).orElse(null);
        if (loadout == null) {
            player.sendSystemMessage(MoveEarthMessage.error("選択されたPvPロードアウトは使用できません。"));
            return false;
        }
        if (isActive(player)) {
            loadoutSelections.put(player.getUUID(), loadout.id());
            player.sendSystemMessage(MoveEarthMessage.success("ロードアウトを「" + loadout.displayName() + "」に変更しました。（次のリスポーン時から反映されます）"));
            return true;
        }
        boolean running = phase == PvpPhase.RUNNING && activeMap != null;
        if (!running && isQueued(player)) {
            loadoutSelections.put(player.getUUID(), loadout.id());
            player.sendSystemMessage(loadoutMessage(loadout, true));
            return true;
        }
        if (rejectForEntryCooldown(player)) return false;
        if (running) {
            boolean joined = joinRunningMatch(player, loadout);
            if (joined) entryCooldown.record(player.getUUID(), player.server.getTickCount());
            return joined;
        }
        PvpTeam assigned = count(PvpTeam.RED, false) <= count(PvpTeam.BLUE, false) ? PvpTeam.RED : PvpTeam.BLUE;
        teams.put(player.getUUID(), assigned);
        loadoutSelections.put(player.getUUID(), loadout.id());
        phase = PvpPhase.WAITING;
        player.sendSystemMessage(loadoutMessage(loadout, false));
        entryCooldown.record(player.getUUID(), player.server.getTickCount());
        syncEntryState(player.server);
        return true;
    }

    /** Player-requested leave (command / PvP screen); spaced by the join/leave cooldown. */
    public void leave(ServerPlayer player) {
        boolean transition = isParticipant(player) || PvpSessionSavedData.get(player.server).contains(player.getUUID());
        if (transition && rejectForEntryCooldown(player)) return;
        leaveNow(player);
        if (transition) entryCooldown.record(player.getUUID(), player.server.getTickCount());
    }

    /** Leave forced by the server (disconnect, lost eligibility); never delayed by the cooldown. */
    void leaveNow(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean wasActive = isActive(player) || PvpSessionSavedData.get(player.server).contains(id);
        teams.remove(id);
        loadoutSelections.remove(id);
        respawns.remove(id);
        outOfBounds.remove(id);
        if (wasActive) restore(player);
        clearClientState(player);

        if (phase == PvpPhase.RUNNING && (count(PvpTeam.RED, true) == 0 || count(PvpTeam.BLUE, true) == 0)) {
            finish(player.server, "対戦相手が退出したため試合終了", winnerFromRemainingTeams());
        } else if (teams.isEmpty()) {
            resetRuntime();
        }
        syncEntryState(player.server);
    }

    public boolean start(MinecraftServer server) {
        PvpArenaSavedData arenaData = PvpArenaSavedData.get(server);
        ServerLevel arena = server.getLevel(ARENA);
        if (!arenaData.hosting()) return rejectStart(server, "PvPイベントが開催状態ではありません。");
        if (phase == PvpPhase.RUNNING || phase == PvpPhase.VOTING || phase == PvpPhase.FINISHED) {
            return rejectStart(server, "すでに試合または投票が進行中です。");
        }
        if (arena == null) return rejectStart(server, "PvPディメンションを読み込めません。");

        List<PvpMapDefinition> availableMaps = PvpMapSavedData.get(server).getEnabledAndConfigured();
        if (availableMaps.isEmpty()) {
            return rejectStart(server, "有効なPvPマップが登録されていません。/pvp admin map を確認してください。");
        }

        removeOfflineQueueMembers(server);
        String missingPreset = missingPresetContent(server);
        if (missingPreset != null) {
            return rejectStart(server, "PvPロードアウトの銃/アタッチメントデータが見つかりません: " + missingPreset);
        }
        rebalanceTeams();
        if (teams.size() < 2 || count(PvpTeam.RED, false) == 0 || count(PvpTeam.BLUE, false) == 0) {
            return rejectStart(server, "試合開始にはオンライン参加者が2人以上必要です。");
        }

        // 複数マップが存在する場合はマップ投票フェーズへ
        if (availableMaps.size() >= 2) {
            phase = PvpPhase.VOTING;
            PvpMapVoteManager.INSTANCE.startVote(server, teams.keySet(), availableMaps, PvpMapVoteManager.VOTE_DURATION_SECONDS);
            broadcastToParticipants(server, Component.literal("PvP: マップ投票が開始されました！画面から好きなマップを選択してください（15秒）。"));
            return true;
        } else {
            // 単一マップの場合は即開始
            onMapVoteFinished(server, availableMaps.get(0));
            return phase == PvpPhase.RUNNING;
        }
    }

    public void onMapVoteFinished(MinecraftServer server, PvpMapDefinition selectedMap) {
        ServerLevel arena = server.getLevel(ARENA);
        if (arena == null || selectedMap == null || !selectedMap.isConfigured()) {
            rejectStart(server, "選択されたマップの読み込みに失敗しました。");
            stop(server);
            return;
        }
        removeOfflineQueueMembers(server);

        // Registration is not an exemption from ordinary-world combat or custody.
        // Recheck after voting, before taking snapshots or teleporting anyone.
        for (ServerPlayer player : participants(server, false)) {
            if (!eligibleForAdmission(player)) {
                leaveNow(player);
                player.sendSystemMessage(MoveEarthMessage.warning("状態が変わったためPvPの参加登録を解除しました。"));
            }
        }
        rebalanceTeams();
        if (!PvpAdmissionPolicy.canStart(count(PvpTeam.RED, false), count(PvpTeam.BLUE, false))) {
            rejectStart(server, "参加可能なプレイヤーが2人以上いないため試合を中止しました。");
            stop(server);
            return;
        }
        this.activeMap = selectedMap;
        // Only an area an admin set is enforced; one derived from the spawns may cut through the arena.
        this.activeBounds = selectedMap.hasOwnBounds() ? selectedMap.bounds() : null;
        ensureTacticalMap(server, selectedMap);

        PvpSessionSavedData sessions = PvpSessionSavedData.get(server);
        matchStats.clear();
        Map<UUID, PvpPlayerSnapshot> pendingSnapshots = new HashMap<>();
        for (ServerPlayer player : participants(server, false)) {
            PvpPlayerSnapshot snapshot = new PvpPlayerSnapshot(player);
            if (PvpCuriosInventoryCompat.isAvailable() && snapshot.curiosInventory == null) {
                rejectStart(server, "Curios装備を安全に退避できない参加者がいるため試合を開始できません。");
                stop(server);
                return;
            }
            pendingSnapshots.put(player.getUUID(), snapshot);
        }
        Map<UUID, PvpSessionSavedData.Entry> replacedStashes = new HashMap<>();
        for (ServerPlayer player : participants(server, false)) {
            PvpPlayerSnapshot snapshot = pendingSnapshots.get(player.getUUID());
            snapshots.put(player.getUUID(), snapshot);
            replacedStashes.put(player.getUUID(), sessions.put(player.getUUID(), snapshot));
            matchStats.put(player.getUUID(), new MatchStats());
        }
        // On disk before any inventory is replaced; see PvpSessionSavedData.
        if (!PvpSessionSavedData.persistNow(server)) {
            // Nothing has been touched yet; put memory back in line with the file and abort.
            replacedStashes.forEach(sessions::reinstate);
            snapshots.clear();
            matchStats.clear();
            rejectStart(server, Component.translatable("message.moveearth_addtional.pvp.start_stash_save_failed"));
            stop(server);
            return;
        }
        for (ServerPlayer player : participants(server, true)) {
            if (!snapshots.get(player.getUUID()).enterIsolatedState(player)) {
                rejectStart(server, "Curios装備の隔離に失敗したため試合を中止しました。");
                stop(server);
                return;
            }
        }
        for (ServerPlayer player : participants(server, true)) activateParticipant(player, arena, activeMap);
        phase = PvpPhase.RUNNING;
        redScore = blueScore = 0;
        ticksLeft = MATCH_TICKS;
        syncTicker = 0;
        matchResultsRecorded = false;
        resetAnnouncerState();
        zoneState = PvpZoneState.NEUTRAL;
        zoneRedPlayers = zoneBluePlayers = 0;
        hudGate.reset();
        zoneGate.reset();
        cleanupArenaMobs(arena);
        syncTeams(server);
        syncHud(server, "争奪中");
        syncZone(server, activeMap);
        syncEntryState(server);
        playToAllParticipants(server, ModSounds.WARLORD_START);
        broadcastToParticipants(server, Component.literal("PvP試合開始！ マップ: " + activeMap.displayName() + " - 丘を占領して180ptを獲得してください。"));
    }

    private boolean joinRunningMatch(ServerPlayer player, PvpLoadoutDefinition loadout) {
        if (!eligibleForAdmission(player)) return false;
        MinecraftServer server = player.server;
        ServerLevel arena = server.getLevel(ARENA);
        if (arena == null || activeMap == null || !activeMap.isConfigured()) {
            player.sendSystemMessage(MoveEarthMessage.error("PvPアリーナの設定を読み込めないため途中参加できません。"));
            return false;
        }
        String missingPreset = missingPresetContent(server);
        if (missingPreset != null) {
            player.sendSystemMessage(MoveEarthMessage.error("PvPロードアウトの銃/アタッチメントデータが見つかりません: " + missingPreset));
            return false;
        }

        UUID id = player.getUUID();
        PvpTeam assigned = count(PvpTeam.RED, true) <= count(PvpTeam.BLUE, true) ? PvpTeam.RED : PvpTeam.BLUE;
        PvpPlayerSnapshot snapshot = new PvpPlayerSnapshot(player);
        if (!snapshot.enterIsolatedState(player)) {
            player.sendSystemMessage(MoveEarthMessage.error("Curios装備を安全に退避できないため途中参加できません。"));
            return false;
        }
        teams.put(id, assigned);
        loadoutSelections.put(id, loadout.id());
        snapshots.put(id, snapshot);
        matchStats.put(id, new MatchStats());
        PvpSessionSavedData sessions = PvpSessionSavedData.get(server);
        PvpSessionSavedData.Entry replaced = sessions.put(id, snapshot);
        // On disk before the inventory is replaced; see PvpSessionSavedData.
        if (!PvpSessionSavedData.persistNow(server)) {
            sessions.reinstate(id, replaced);
            teams.remove(id);
            loadoutSelections.remove(id);
            snapshots.remove(id);
            matchStats.remove(id);
            PvpCuriosInventoryCompat.restore(player, snapshot.curiosInventory);
            player.sendSystemMessage(MoveEarthMessage.error(
                    Component.translatable("message.moveearth_addtional.pvp.stash_save_failed")));
            syncEntryState(server);
            return false;
        }

        activateParticipant(player, arena, activeMap);
        syncTeams(server);
        syncEntryState(server);
        PacketDistributor.sendToPlayer(player,
                new S2C_PvpHudPacket(true, redScore, blueScore, WIN_SCORE, ticksLeft, "途中参加"));
        sendZone(player, activeMap);
        broadcastToParticipants(server, Component.literal(
                player.getGameProfile().getName() + " が " + assigned.name() + " チームへ途中参加しました"));
        return true;
    }

    private static boolean eligibleForAdmission(ServerPlayer player) {
        return PvpAdmissionPolicy.eligible(player.isAlive(), player.isSpectator(),
                com.ruskserver.moveearth_addtional.CompatEventHandler.isPlayerDown(player),
                com.ruskserver.moveearth_addtional.s2.combat.CombatTagService.isTagged(player),
                com.ruskserver.moveearth_addtional.s2.siege.PrisonerService.isMovementRestricted(player));
    }

    private void activateParticipant(ServerPlayer player, ServerLevel arena, PvpMapDefinition map) {
        player.closeContainer();
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.removeAllEffects();
        player.setGameMode(GameType.SURVIVAL);
        applyPvpMaxHealth(player);
        assignScoreboardTeam(player, team(player));
        giveKit(player, selectedLoadout(player));
        restoreKitSettings(player);
        resetVitals(player);
        BlockPos spawn = PvpSpawnSelector.INSTANCE.selectSpawn(player, map, team(player), player.server);
        teleport(player, arena, spawn);
        PvpTacticalSavedData.get(player.server).get(map.id())
                .ifPresent(tactical -> PacketDistributor.sendToPlayer(player, S2C_PvpTacticalMapPacket.of(tactical)));
        // The opening deploy: the screen shows the whole map before the player picks where to start.
        RespawnState state = new RespawnState(0, player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        state.first = true;
        holdForDeploy(player, state);
        openDeploy(player, state);
    }

    /** Scans the map's battle area when it has no tactical map yet, or one of a different area. */
    private static void ensureTacticalMap(MinecraftServer server, PvpMapDefinition map) {
        PvpMapGeometry.Box box = map.bounds();
        PvpTacticalMap tactical = PvpTacticalSavedData.get(server).get(map.id()).orElse(null);
        boolean current = tactical != null && tactical.originX() == box.minX() && tactical.originZ() == box.minZ()
                && tactical.baseY() == box.minY()
                && tactical.width() == PvpMapGeometry.cells(box.width(), tactical.cellSize())
                && tactical.depth() == PvpMapGeometry.cells(box.depth(), tactical.cellSize());
        if (!current && !PvpTacticalScanner.INSTANCE.scanning(map.id())) PvpTacticalScanner.INSTANCE.request(map, null);
    }

    /** Puts a player into the waiting state of the deploy screen: a still, untouchable spectator. */
    private void holdForDeploy(ServerPlayer player, RespawnState state) {
        respawns.put(player.getUUID(), state);
        outOfBounds.remove(player.getUUID());
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.setInvulnerable(true);
        player.setDeltaMovement(0, 0, 0);
        player.setGameMode(GameType.SPECTATOR);
        player.setCamera(player);
    }

    private void openDeploy(ServerPlayer player, RespawnState state) {
        // The point chosen last life stays chosen, as long as it is still one of this team's.
        long last = lastDeployPoints.getOrDefault(player.getUUID(), S2C_PvpDeployPacket.NO_POINT);
        if (state.chosenPoint == S2C_PvpDeployPacket.NO_POINT && activeMap.deployPoints(team(player)).stream()
                .anyMatch(point -> point.id() == last)) state.chosenPoint = last;
        state.deploying = true;
        state.autoTicks = PvpDeployPolicy.AUTO_DEPLOY_TICKS;
        sendDeploy(player, state);
        sendAllyPositions(player);
    }

    private void sendDeploy(ServerPlayer player, RespawnState state) {
        PvpTeam team = team(player);
        List<S2C_PvpDeployPacket.Point> points = new ArrayList<>();
        for (PvpMapDefinition.DeployPoint point : activeMap.deployPoints(team)) {
            points.add(new S2C_PvpDeployPacket.Point(point.id(), point.name(), point.pos().getX(), point.pos().getY(),
                    point.pos().getZ(), pointDangerous(player.server, team, point.pos())));
        }
        PacketDistributor.sendToPlayer(player, new S2C_PvpDeployPacket(true, activeMap.id(), activeMap.displayName(), state.first, state.autoTicks,
                team == null ? 0 : team.ordinal(), points, state.chosenPoint, selectedLoadoutId(player),
                !state.first, Mth.floor(state.x), Mth.floor(state.z),
                state.hasKiller, Mth.floor(state.killerX), Mth.floor(state.killerZ)));
    }

    /** Teammates on the field, for the deploy screen's map; enemies are never sent. */
    private void sendAllyPositions(ServerPlayer viewer) {
        PvpTeam team = team(viewer);
        List<S2C_PvpAllyPositionsPacket.Ally> allies = new ArrayList<>();
        for (ServerPlayer other : participants(viewer.server, true)) {
            if (other == viewer || team(other) != team || respawns.containsKey(other.getUUID())
                    || !other.level().dimension().equals(ARENA)) continue;
            allies.add(new S2C_PvpAllyPositionsPacket.Ally(other.getGameProfile().getName(),
                    Mth.floor(other.getX()), Mth.floor(other.getZ()), other.getYRot()));
        }
        PacketDistributor.sendToPlayer(viewer, new S2C_PvpAllyPositionsPacket(allies));
    }

    private boolean pointDangerous(MinecraftServer server, PvpTeam team, BlockPos pos) {
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer other : participants(server, true)) {
            if (team(other) == team || respawns.containsKey(other.getUUID())
                    || !other.level().dimension().equals(ARENA)) continue;
            nearest = Math.min(nearest, other.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
        }
        return PvpDeployPolicy.dangerous(nearest);
    }

    /**
     * The deploy screen's choice: remembered for the automatic deploy, and acted on at once when the player
     * pressed deploy.
     */
    public void requestDeploy(ServerPlayer player, long pointId, String loadoutId, boolean now) {
        RespawnState state = respawns.get(player.getUUID());
        if (phase != PvpPhase.RUNNING || activeMap == null || state == null || !isActive(player)) return;
        PvpLoadoutSavedData.get(player.server).getById(loadoutId)
                .ifPresent(loadout -> loadoutSelections.put(player.getUUID(), loadout.id()));
        boolean teamPoint = activeMap.deployPoints(team(player)).stream().anyMatch(point -> point.id() == pointId);
        state.chosenPoint = teamPoint ? pointId : S2C_PvpDeployPacket.NO_POINT;
        lastDeployPoints.put(player.getUUID(), state.chosenPoint);
        if (now && state.deploying) {
            respawns.remove(player.getUUID());
            deploy(player, state);
        }
    }

    private void rememberKitSettings(ServerPlayer player) {
        Map<ResourceLocation, com.tacz.guns.api.item.gun.FireMode> modes =
                fireModes.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null) continue;
            com.tacz.guns.api.item.gun.FireMode mode = gun.getFireMode(stack);
            if (mode != null && mode != com.tacz.guns.api.item.gun.FireMode.UNKNOWN) modes.put(gun.getGunId(stack), mode);
        }
        heldSlots.put(player.getUUID(), player.getInventory().selected);
    }

    private void restoreKitSettings(ServerPlayer player) {
        Map<ResourceLocation, com.tacz.guns.api.item.gun.FireMode> modes = fireModes.getOrDefault(player.getUUID(), Map.of());
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null) continue;
            com.tacz.guns.api.item.gun.FireMode mode = modes.get(gun.getGunId(stack));
            if (mode != null) gun.setFireMode(stack, mode);
        }
        int held = heldSlots.getOrDefault(player.getUUID(), 0);
        player.getInventory().selected = held >= 0 && held < 9 ? held : 0;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(player.getInventory().selected));
    }

    /** Leaves the deploy screen for the field: the chosen point when it is safe, otherwise the usual pick. */
    private void deploy(ServerPlayer player, RespawnState state) {
        MinecraftServer server = player.server;
        player.setCamera(player);
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(false);
        player.removeAllEffects();
        resetVitals(player);
        rememberKitSettings(player);
        player.getInventory().clearContent();
        giveKit(player, selectedLoadout(player));
        restoreKitSettings(player);
        PvpTeam team = team(player);
        BlockPos chosen = activeMap.deployPoints(team).stream()
                .filter(point -> point.id() == state.chosenPoint)
                .map(PvpMapDefinition.DeployPoint::pos)
                .findFirst().orElse(null);
        BlockPos spawn;
        if (PvpDeployPolicy.useChosen(chosen != null, chosen != null && pointDangerous(server, team, chosen))) {
            spawn = chosen;
            PvpSpawnSelector.INSTANCE.record(spawn, server);
        } else {
            if (chosen != null) player.sendSystemMessage(MoveEarthMessage.warning("選んだ出撃地点に敵が迫っていたため、別の地点へ出撃しました。"));
            spawn = PvpSpawnSelector.INSTANCE.selectSpawn(player, activeMap, team, server);
        }
        teleport(player, server.getLevel(ARENA), spawn);
        PacketDistributor.sendToPlayer(player, S2C_PvpDeployPacket.closed());
    }

    public void stop(MinecraftServer server) {
        PvpMapVoteManager.INSTANCE.cancelVote();
        List<ServerPlayer> online = participants(server, true);
        for (ServerPlayer player : online) {
            restore(player);
            clearClientState(player);
        }
        resetRuntime();
        syncEntryState(server);
    }

    public void tick(MinecraftServer server) {
        tickPhase(server);
        flushEntryState(server);
    }

    private void tickPhase(MinecraftServer server) {
        if (phase == PvpPhase.VOTING) {
            PvpMapVoteManager.INSTANCE.tick(server);
            return;
        }

        for (ServerPlayer player : participants(server, true)) {
            if (!player.level().dimension().equals(ARENA) && activeMap != null) {
                BlockPos spawn = team(player) == PvpTeam.RED ? activeMap.redSpawn() : activeMap.blueSpawn();
                teleport(player, server.getLevel(ARENA), spawn);
            }
            maintainCombatHunger(player);
            enforceLoadout(player, selectedLoadout(player));

            if (player.getHealth() < PVP_MAX_HEALTH && !respawns.containsKey(player.getUUID())) {
                int lastDamage = lastDamageTicks.getOrDefault(player.getUUID(), 0);
                if (server.getTickCount() - lastDamage >= 100) {
                    if (server.getTickCount() % 10 == 0) {
                        player.heal(1.0F);
                    }
                }
            }
        }

        if (phase == PvpPhase.FINISHED) {
            if (--ticksLeft <= 0) stop(server);
            return;
        }
        if (phase != PvpPhase.RUNNING || activeMap == null) return;
        
        if (--ticksLeft <= 0) {
            finish(server, winnerText(), scoreWinner());
            return;
        }
        if (!finalStandAnnounced && ticksLeft <= FINAL_STAND_TICKS) {
            finalStandAnnounced = true;
            playToAllParticipants(server, ModSounds.WARLORD_FINAL_STAND);
        }

        tickRespawns(server);
        tickBattleArea(server);
        if (count(PvpTeam.RED, true) == 0 || count(PvpTeam.BLUE, true) == 0) {
            finish(server, "対戦相手がいないため試合終了", winnerFromRemainingTeams());
            return;
        }

        int red = 0;
        int blue = 0;
        for (ServerPlayer player : participants(server, true)) {
            if (respawns.containsKey(player.getUUID()) || !player.level().dimension().equals(ARENA)) continue;
            if (inside(player.blockPosition(), activeMap.hillMin(), activeMap.hillMax())) {
                if (team(player) == PvpTeam.RED) red++;
                else if (team(player) == PvpTeam.BLUE) blue++;
            }
        }
        zoneRedPlayers = red;
        zoneBluePlayers = blue;

        if (red > 0 && blue > 0) {
            zoneState = PvpZoneState.CONTESTED;
            syncHud(server, "争奪中");
        } else if (red > 0) {
            zoneState = PvpZoneState.RED;
            if (announcedZoneController != PvpTeam.RED) {
                announcedZoneController = PvpTeam.RED;
                announceZoneControl(server, PvpTeam.RED);
            }
            if (++syncTicker >= 20) {
                syncTicker = 0;
                redScore++;
                recordZoneForTeam(server, PvpTeam.RED, activeMap);
                syncHud(server, "RED 占領中");
                if (redScore >= WIN_SCORE) {
                    finish(server, "RED 勝利", PvpTeam.RED);
                    return;
                }
            }
        } else if (blue > 0) {
            zoneState = PvpZoneState.BLUE;
            if (announcedZoneController != PvpTeam.BLUE) {
                announcedZoneController = PvpTeam.BLUE;
                announceZoneControl(server, PvpTeam.BLUE);
            }
            if (++syncTicker >= 20) {
                syncTicker = 0;
                blueScore++;
                recordZoneForTeam(server, PvpTeam.BLUE, activeMap);
                syncHud(server, "BLUE 占領中");
                if (blueScore >= WIN_SCORE) {
                    finish(server, "BLUE 勝利", PvpTeam.BLUE);
                    return;
                }
            }
        } else {
            zoneState = PvpZoneState.NEUTRAL;
            announcedZoneController = null;
            syncHud(server, "中立");
        }
        syncZone(server, activeMap);
    }

    public void tickNonParticipant(ServerPlayer player) {
        if (player.level().dimension().equals(ARENA) && !isActive(player)
                && !player.createCommandSourceStack().hasPermission(2)) {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            teleport(player, overworld, spawn);
            player.sendSystemMessage(MoveEarthMessage.error("PvPアリーナには試合参加者のみ入場できます。"));
        }
    }

    public void eliminate(ServerPlayer victim, ServerPlayer killer) {
        if (phase != PvpPhase.RUNNING || !isActive(victim) || respawns.containsKey(victim.getUUID())) return;
        boolean validPlayerKill = killer != null && isActive(killer) && team(killer) != team(victim);
        matchStats.computeIfAbsent(victim.getUUID(), ignored -> new MatchStats()).deaths++;
        if (validPlayerKill) {
            matchStats.computeIfAbsent(killer.getUUID(), ignored -> new MatchStats()).kills++;
            if (canRewardKill(killer, victim)) {
                PvpRewardData.get(victim.server).recordKill(killer, killer.distanceToSqr(victim) <= 64.0D);
            }
            announceElimination(victim, killer);
        } else {
            resetKillState(victim.getUUID());
        }

        double targetX = killer == null ? victim.getX() : killer.getX();
        double targetY = killer == null ? victim.getEyeY() : killer.getEyeY();
        double targetZ = killer == null ? victim.getZ() : killer.getZ();
        // With no killer there is nothing to face: the view stays as it was.
        float yaw = killer == null ? victim.getYRot() : lookYaw(victim.getX(), victim.getZ(), targetX, targetZ);
        float pitch = killer == null ? victim.getXRot()
                : lookPitch(victim.getX(), victim.getEyeY(), victim.getZ(), targetX, targetY, targetZ);
        RespawnState respawn = new RespawnState(RESPAWN_TICKS, victim.getX(), victim.getY(), victim.getZ(), yaw, pitch);
        if (killer != null) {
            respawn.hasKiller = true;
            respawn.killerX = killer.getX();
            respawn.killerZ = killer.getZ();
        }
        respawns.put(victim.getUUID(), respawn);
        outOfBounds.remove(victim.getUUID());

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
        String weaponName = "";
        int streak = 0;
        if (killer != null) {
            ItemStack mainHand = killer.getMainHandItem();
            IGun gun = IGun.getIGunOrNull(mainHand);
            if (gun != null) {
                weaponName = gun.getGunId(mainHand).getPath().replace('_', ' ').toUpperCase(Locale.ROOT);
            } else if (!mainHand.isEmpty()) {
                weaponName = mainHand.getHoverName().getString();
            }
            KillAnnouncementState announced = killAnnouncements.get(killer.getUUID());
            if (announced != null) streak = announced.streak;
        }
        PacketDistributor.sendToPlayer(victim, new S2C_PvpKillcamPacket(killerId, killerName, targetX, targetY, targetZ,
                RESPAWN_TICKS, weaponName, killer == null ? 0.0F : killer.getHealth(),
                killer == null ? 0.0F : killer.getMaxHealth(), killer == null ? 0.0F : (float) victim.distanceTo(killer),
                streak));

        Component notice = Component.literal(victim.getGameProfile().getName() + " は " + killerName + " に倒された");
        broadcastToParticipants(victim.server, notice);
    }

    public void recordDamage(ServerPlayer attacker, ServerPlayer victim, float damage) {
        if (phase != PvpPhase.RUNNING || !isActive(victim) || respawns.containsKey(victim.getUUID())) return;
        lastDamageTicks.put(victim.getUUID(), victim.server.getTickCount());
        
        if (!isActive(attacker) || team(attacker) == team(victim) || !Float.isFinite(damage) || damage <= 0.0F) return;
        float remainingHealth = Math.max(0.0F, victim.getHealth() + victim.getAbsorptionAmount());
        float appliedDamage = Math.min(damage, remainingHealth);
        if (appliedDamage <= 0.0F) return;
        matchStats.computeIfAbsent(attacker.getUUID(), ignored -> new MatchStats()).damage += appliedDamage;
    }

    public void recoverIfNeeded(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new S2C_SyncLoadoutsPacket(PvpLoadoutSavedData.get(player.server).getAll()));

        if (isActive(player)) return;
        // A fresh login has no PvP screen state; forget what an earlier session was sent, and send it anew so
        // the hub's PvP tab shows for someone joining during an event.
        entryStateSync.forget(player.getUUID());
        syncEntryState(player.server);
        PvpSessionSavedData stored = PvpSessionSavedData.get(player.server);
        if (!stored.contains(player.getUUID())) return;
        if (PvpRestoreMarker.restored(player, stored.token(player.getUUID()))) {
            // The player file already holds the restored inventory; only the stash's removal was lost.
            stored.remove(player.getUUID());
            return;
        }
        teams.remove(player.getUUID());
        loadoutSelections.remove(player.getUUID());
        restore(player);
        clearClientState(player);
        player.sendSystemMessage(MoveEarthMessage.success("中断されたPvPセッションから所持品と状態を復旧しました。"));
    }

    public void serverStopped() {
        resetRuntime();
        entryCooldown.clear();
        entryStateSync.clear();
        entryStateDirty = false;
    }

    private void tickRespawns(MinecraftServer server) {
        if (activeMap == null) return;
        int now = server.getTickCount();
        List<Map.Entry<UUID, RespawnState>> due = new ArrayList<>();
        for (Map.Entry<UUID, RespawnState> entry : respawns.entrySet()) {
            RespawnState state = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            if (player.level().dimension().equals(ARENA)) {
                player.teleportTo(player.serverLevel(), state.x, state.y, state.z, state.yaw, state.pitch);
            }
            if (!state.deploying) {
                // The killcam plays first; the deploy screen opens when it ends.
                if (--state.ticks <= 1) openDeploy(player, state);
                continue;
            }
            if (--state.autoTicks <= 0) {
                due.add(entry);
                continue;
            }
            if (now % 10 == 0) sendDeploy(player, state);
            if (now % 5 == 0) sendAllyPositions(player);
        }
        for (Map.Entry<UUID, RespawnState> entry : due) {
            respawns.remove(entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) deploy(player, entry.getValue());
        }
    }

    /** Warns a player who leaves the battle area, then hurts them every second until they return. */
    private void tickBattleArea(MinecraftServer server) {
        if (activeBounds == null) return;
        for (ServerPlayer player : participants(server, true)) {
            UUID id = player.getUUID();
            if (respawns.containsKey(id) || !player.level().dimension().equals(ARENA)
                    || activeBounds.containsXZ(player.getBlockX(), player.getBlockZ())) {
                if (outOfBounds.remove(id) != null) {
                    player.displayClientMessage(Component.literal("戦闘エリアに戻りました").withStyle(ChatFormatting.GREEN), true);
                }
                continue;
            }
            int ticks = outOfBounds.merge(id, 1, Integer::sum);
            int left = PvpDeployPolicy.outOfBoundsSecondsLeft(ticks);
            player.displayClientMessage(Component.literal(left > 0
                    ? "戦闘エリア外です！ " + left + "秒以内に戻ってください"
                    : "戦闘エリア外: ダメージを受けています").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
            if (ticks == 1) player.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.MASTER, 1.0F, 0.6F);
            if (PvpDeployPolicy.outOfBoundsDamage(ticks)) {
                // Counts as being hurt, or the out-of-combat regeneration would heal it straight back.
                lastDamageTicks.put(id, server.getTickCount());
                player.hurt(player.damageSources().outOfBorder(), PvpDeployPolicy.OUT_OF_BOUNDS_DAMAGE);
            }
        }
    }

    private void finish(MinecraftServer server, String message, PvpTeam winner) {
        if (phase == PvpPhase.FINISHED) return;
        recordMatchResults(server, winner);
        announceMatchResult(server, winner);
        phase = PvpPhase.FINISHED;
        ticksLeft = FINISHED_TICKS;
        for (UUID id : respawns.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) PacketDistributor.sendToPlayer(player, S2C_PvpDeployPacket.closed());
        }
        respawns.clear();
        outOfBounds.clear();
        forEachPlayer(server, true, player -> {
            player.setCamera(player);
            player.setInvulnerable(true);
        });
        syncHud(server, message);
        broadcastToParticipants(server, Component.literal(message));
        sendMatchSummary(server);
        syncEntryState(server);
    }

    private String winnerText() {
        PvpTeam winner = scoreWinner();
        return winner == null ? "引き分け" : winner == PvpTeam.RED ? "RED 勝利" : "BLUE 勝利";
    }

    private PvpTeam scoreWinner() {
        return redScore == blueScore ? null : redScore > blueScore ? PvpTeam.RED : PvpTeam.BLUE;
    }

    private PvpTeam winnerFromRemainingTeams() {
        boolean redPresent = count(PvpTeam.RED, true) > 0;
        boolean bluePresent = count(PvpTeam.BLUE, true) > 0;
        return redPresent == bluePresent ? null : redPresent ? PvpTeam.RED : PvpTeam.BLUE;
    }

    private void recordMatchResults(MinecraftServer server, PvpTeam winner) {
        if (matchResultsRecorded || !rewardsEnabled()) return;
        matchResultsRecorded = true;
        PvpRewardData rewards = PvpRewardData.get(server);
        for (ServerPlayer player : participants(server, true)) {
            rewards.recordMatchResult(player, winner != null && team(player) == winner);
        }
    }

    private void announceElimination(ServerPlayer victim, ServerPlayer killer) {
        int now = killer.server.getTickCount();
        UUID killerId = killer.getUUID();
        UUID victimId = victim.getUUID();
        boolean revenge = victimId.equals(lastKillerByVictim.get(killerId));
        lastKillerByVictim.put(victimId, killerId);
        resetKillState(victimId);

        KillAnnouncementState state = killAnnouncements.computeIfAbsent(killerId,
                ignored -> new KillAnnouncementState());
        state.streak++;
        state.multiKills = state.lastKillTick >= 0 && now - state.lastKillTick <= MULTI_KILL_WINDOW_TICKS
                ? state.multiKills + 1 : 1;
        state.lastKillTick = now;

        if (!firstBloodAnnounced) {
            firstBloodAnnounced = true;
            playToAllParticipants(killer.server, ModSounds.WARLORD_FIRST_BLOOD);
            return;
        }

        DeferredHolder<SoundEvent, SoundEvent> sound;
        if (state.streak == 12) sound = ModSounds.WARLORD_UNSTOPPABLE;
        else if (state.streak == 8) sound = ModSounds.WARLORD_RAMPAGE;
        else if (state.streak == 5) sound = ModSounds.WARLORD_DOMINATING;
        else if (state.multiKills == 5) sound = ModSounds.WARLORD_ERADICATION;
        else if (state.multiKills == 4) sound = ModSounds.WARLORD_ANNIHILATION;
        else if (state.multiKills == 3) sound = ModSounds.WARLORD_TRIPLE_KILL;
        else if (state.multiKills == 2) sound = ModSounds.WARLORD_DOUBLE_KILL;
        else if (revenge) sound = ModSounds.WARLORD_REVENGE_KILL;
        else sound = switch (state.streak % 3) {
            case 0 -> ModSounds.WARLORD_KILLSHOT;
            case 1 -> ModSounds.WARLORD_ENEMY_ELIMINATED;
            default -> ModSounds.WARLORD_TANGO_DOWN;
        };
        playToPlayer(killer, sound);
    }

    private void announceZoneControl(MinecraftServer server, PvpTeam controller) {
        for (ServerPlayer player : participants(server, true)) {
            playToPlayer(player, team(player) == controller
                    ? ModSounds.WARLORD_TARGET_SECURED
                    : ModSounds.WARLORD_TARGET_LOCKED);
        }
    }

    private void announceMatchResult(MinecraftServer server, PvpTeam winner) {
        for (ServerPlayer player : participants(server, true)) {
            int outcome = winner == null ? S2C_PvpResultPacket.DRAW
                    : team(player) == winner ? S2C_PvpResultPacket.WIN : S2C_PvpResultPacket.LOSS;
            PacketDistributor.sendToPlayer(player,
                    new S2C_PvpResultPacket(outcome, redScore, blueScore, RESULT_OVERLAY_TICKS));
            if (winner == null) {
                playToPlayer(player, ModSounds.WARLORD_GAME_OVER);
            } else {
                playToPlayer(player, team(player) == winner
                        ? ModSounds.WARLORD_ROUND_WINNER
                        : ModSounds.WARLORD_MISSION_FAILED);
            }
        }
    }

    private void sendMatchSummary(MinecraftServer server) {
        List<MatchResultEntry> ranking = participants(server, true).stream()
                .map(player -> new MatchResultEntry(player.getGameProfile().getName(), team(player),
                        matchStats.getOrDefault(player.getUUID(), new MatchStats())))
                .sorted(Comparator.comparingInt((MatchResultEntry entry) -> entry.stats.kills).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                (MatchResultEntry entry) -> entry.stats.damage).reversed())
                        .thenComparingInt(entry -> entry.stats.deaths)
                        .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (ranking.isEmpty()) return;

        Component separator = Component.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .withStyle(ChatFormatting.DARK_GRAY);
        Component header = MoveEarthMessage.info(Component.literal("KOTH MATCH RESULT")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        Component score = Component.literal("RED  " + redScore).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("   -   ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(blueScore + "  BLUE").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));

        for (ServerPlayer viewer : participants(server, true)) {
            viewer.sendSystemMessage(separator);
            viewer.sendSystemMessage(header);
            viewer.sendSystemMessage(score);
            for (int index = 0; index < ranking.size(); index++) {
                MatchResultEntry entry = ranking.get(index);
                ChatFormatting rankColor = index == 0 ? ChatFormatting.GOLD
                        : index == 1 ? ChatFormatting.WHITE
                        : index == 2 ? ChatFormatting.YELLOW : ChatFormatting.GRAY;
                ChatFormatting teamColor = entry.team == PvpTeam.RED ? ChatFormatting.RED : ChatFormatting.BLUE;
                String damage = String.format(Locale.ROOT, "%.1f", entry.stats.damage);
                Component row = Component.literal("#" + (index + 1) + " ").withStyle(rankColor, ChatFormatting.BOLD)
                        .append(Component.literal("[" + entry.team.name() + "] ").withStyle(teamColor))
                        .append(Component.literal(entry.name).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("  キル " + entry.stats.kills
                                + " / デス " + entry.stats.deaths + " / DMG " + damage)
                                .withStyle(ChatFormatting.GRAY));
                viewer.sendSystemMessage(row);
            }
            viewer.sendSystemMessage(separator);
        }
    }

    private static void playToPlayer(ServerPlayer player, DeferredHolder<SoundEvent, SoundEvent> sound) {
        player.playNotifySound(sound.get(), SoundSource.MASTER, 1.0F, 1.0F);
    }

    private void playToAllParticipants(MinecraftServer server, DeferredHolder<SoundEvent, SoundEvent> sound) {
        forEachPlayer(server, true, player -> playToPlayer(player, sound));
    }

    private void resetKillState(UUID playerId) {
        KillAnnouncementState state = killAnnouncements.get(playerId);
        if (state != null) state.reset();
    }

    private void resetAnnouncerState() {
        killAnnouncements.clear();
        lastKillerByVictim.clear();
        firstBloodAnnounced = false;
        finalStandAnnounced = false;
        announcedZoneController = null;
    }

    private void giveKit(ServerPlayer player, PvpLoadoutDefinition loadout) {
        PvpTeam t = team(player);
        player.setItemSlot(EquipmentSlot.HEAD, createPvpArmor(player, Items.LEATHER_HELMET, t, 3));
        player.setItemSlot(EquipmentSlot.CHEST, createPvpArmor(player, Items.LEATHER_CHESTPLATE, t, 2));
        player.setItemSlot(EquipmentSlot.LEGS, createPvpArmor(player, Items.IRON_LEGGINGS, t, 3));
        player.setItemSlot(EquipmentSlot.FEET, createPvpArmor(player, Items.IRON_BOOTS, t, 3));
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        installLoadout(player, loadout);
        refillGuns(player, loadout);
    }

    private ItemStack createPvpArmor(ServerPlayer player, Item item, PvpTeam team, int enchantLevel) {
        ItemStack stack = new ItemStack(item);
        if ((item == Items.LEATHER_CHESTPLATE || item == Items.LEATHER_HELMET) && team != null) {
            int color = team == PvpTeam.RED ? 0xFF3333 : 0x3333FF;
            stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(color, true));
        }
        var protection = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
        stack.enchant(protection, enchantLevel);
        return stack;
    }

    private static void resetVitals(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.setAbsorptionAmount(0.0F);
        player.getFoodData().setFoodLevel(COMBAT_FOOD_LEVEL);
        player.getFoodData().setSaturation(0.0F);
        player.getFoodData().setExhaustion(0.0F);
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.fallDistance = 0.0F;
    }

    private static void applyPvpMaxHealth(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(PVP_MAX_HEALTH);
    }

    private static void maintainCombatHunger(ServerPlayer player) {
        if (player.getFoodData().getFoodLevel() < COMBAT_FOOD_LEVEL) {
            player.getFoodData().setFoodLevel(COMBAT_FOOD_LEVEL);
        }
        player.getFoodData().setSaturation(0.0F);
    }

    private void enforceLoadout(ServerPlayer player, PvpLoadoutDefinition loadout) {
        for (PvpLoadoutDefinition.WeaponDefinition weapon : loadout.weapons()) {
            ItemStack gunStack = player.getInventory().getItem(weapon.slot());
            if (!isPresetGun(gunStack, weapon)) {
                gunStack = createPresetGun(player, weapon);
                player.getInventory().setItem(weapon.slot(), gunStack);
            }
            maintainInfiniteReserve(gunStack);
        }
        for (int slot = 0; slot < 36; slot++) {
            if (!isLoadoutSlot(loadout, slot) && !player.getInventory().getItem(slot).isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }

        PvpTeam t = team(player);
        ensureArmor(player, EquipmentSlot.HEAD, Items.LEATHER_HELMET, t, 3);
        ensureArmor(player, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, t, 2);
        ensureArmor(player, EquipmentSlot.LEGS, Items.IRON_LEGGINGS, t, 3);
        ensureArmor(player, EquipmentSlot.FEET, Items.IRON_BOOTS, t, 3);
        if (!player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    private void ensureArmor(ServerPlayer player, EquipmentSlot slot, Item expected, PvpTeam team, int enchantLevel) {
        if (!validArmor(player.getItemBySlot(slot), expected)) {
            player.setItemSlot(slot, createPvpArmor(player, expected, team, enchantLevel));
        }
    }

    private static boolean validArmor(ItemStack stack, Item expected) {
        return !stack.isEmpty() && stack.is(expected);
    }

    private void installLoadout(ServerPlayer player, PvpLoadoutDefinition loadout) {
        for (PvpLoadoutDefinition.WeaponDefinition weapon : loadout.weapons()) {
            player.getInventory().setItem(weapon.slot(), createPresetGun(player, weapon));
        }
    }

    private void refillGuns(ServerPlayer player, PvpLoadoutDefinition loadout) {
        for (PvpLoadoutDefinition.WeaponDefinition weapon : loadout.weapons()) {
            ItemStack gunStack = player.getInventory().getItem(weapon.slot());
            IGun gun = IGun.getIGunOrNull(gunStack);
            if (gun == null) continue;
            int magazineSize = magazineSize(gun, gunStack);
            gun.setCurrentAmmoCount(gunStack, magazineSize);
            gun.setBulletInBarrel(gunStack, true);
            int reserveAmmo = Math.max(MIN_INFINITE_RESERVE_AMMO, magazineSize * 4);
            gun.setMaxDummyAmmoAmount(gunStack, reserveAmmo);
            gun.setDummyAmmoAmount(gunStack, reserveAmmo);
        }
    }

    private static void maintainInfiniteReserve(ItemStack gunStack) {
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) return;

        int reserveAmmo = Math.max(MIN_INFINITE_RESERVE_AMMO, magazineSize(gun, gunStack) * 4);
        if (gun.getMaxDummyAmmoAmount(gunStack) != reserveAmmo) {
            gun.setMaxDummyAmmoAmount(gunStack, reserveAmmo);
        }
        if (gun.getDummyAmmoAmount(gunStack) < reserveAmmo) {
            gun.setDummyAmmoAmount(gunStack, reserveAmmo);
        }
    }

    private static ItemStack createPresetGun(ServerPlayer player, PvpLoadoutDefinition.WeaponDefinition weapon) {
        ItemStack gunStack = new ItemStack(com.tacz.guns.init.ModItems.MODERN_KINETIC_GUN.get());
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null || TimelessAPI.getCommonGunIndex(weapon.gunId()).isEmpty()) return ItemStack.EMPTY;
        gun.setGunId(gunStack, weapon.gunId());

        for (ResourceLocation attachmentId : weapon.attachments()) {
            ItemStack attachmentStack = new ItemStack(com.tacz.guns.init.ModItems.ATTACHMENT.get());
            IAttachment attachment = IAttachment.getIAttachmentOrNull(attachmentStack);
            if (attachment == null) continue;
            attachment.setAttachmentId(attachmentStack, attachmentId);
            try {
                if (gun.allowAttachment(gunStack, attachmentStack)) {
                    gun.installAttachment(player.registryAccess(), gunStack, attachmentStack);
                } else {
                    Moveearth_addtional.LOGGER.warn("PvP preset rejected attachment {} for {}",
                            attachmentId, weapon.gunId());
                }
            } catch (RuntimeException exception) {
                Moveearth_addtional.LOGGER.warn("Could not install PvP attachment {} on {}",
                        attachmentId, weapon.gunId(), exception);
            }
        }
        gun.setAttachmentLock(gunStack, true);
        return gunStack;
    }

    private static boolean isPresetGun(ItemStack stack, PvpLoadoutDefinition.WeaponDefinition expected) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null || !expected.gunId().equals(gun.getGunId(stack)) || !gun.hasAttachmentLock(stack)) {
            return false;
        }
        for (ResourceLocation attachmentId : expected.attachments()) {
            var attachment = TimelessAPI.getCommonAttachmentIndex(attachmentId);
            if (attachment.isEmpty()
                    || !attachmentId.equals(gun.getAttachmentId(stack, attachment.get().getType()))) {
                return false;
            }
        }
        return true;
    }

    private static String missingPresetContent(MinecraftServer server) {
        for (PvpLoadoutDefinition loadout : PvpLoadoutSavedData.get(server).getAll()) {
            for (PvpLoadoutDefinition.WeaponDefinition weapon : loadout.weapons()) {
                if (TimelessAPI.getCommonGunIndex(weapon.gunId()).isEmpty()) return weapon.gunId().toString();
                for (ResourceLocation attachment : weapon.attachments()) {
                    if (TimelessAPI.getCommonAttachmentIndex(attachment).isEmpty()) return attachment.toString();
                }
            }
        }
        return null;
    }

    private static boolean isLoadoutSlot(PvpLoadoutDefinition loadout, int slot) {
        return loadout.weapons().stream().anyMatch(weapon -> weapon.slot() == slot);
    }

    private static int magazineSize(IGun gun, ItemStack gunStack) {
        return TimelessAPI.getCommonGunIndex(gun.getGunId(gunStack))
                .map(index -> index.getGunData().getAmmoAmount())
                .orElse(30);
    }

    private void restore(ServerPlayer player) {
        UUID id = player.getUUID();
        PvpSessionSavedData sessions = PvpSessionSavedData.get(player.server);
        UUID token = sessions.token(id);
        if (PvpRestoreMarker.restored(player, token)) {
            // Already applied; applying again would undo whatever happened since.
            snapshots.remove(id);
            return;
        }
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
        if (token == null) {
            sessions.remove(id);
        } else {
            // Kept until this player's file is saved with the restored inventory (PvpRestoreMarker).
            PvpRestoreMarker.mark(player, token);
        }
    }

    /**
     * Sends the HUD to participants only when what it shows changed: the scores, the
     * status text, or the whole second the client displays (it renders ticksLeft / 20).
     */
    private void syncHud(MinecraftServer server, String hillStatus) {
        HudKey key = new HudKey(redScore, blueScore, WIN_SCORE, Math.max(0, ticksLeft) / 20, hillStatus);
        if (!hudGate.shouldSend(key, server.getTickCount())) return;
        S2C_PvpHudPacket packet = new S2C_PvpHudPacket(true, redScore, blueScore, WIN_SCORE, ticksLeft, hillStatus);
        forEachPlayer(server, true, player -> PacketDistributor.sendToPlayer(player, packet));
    }

    /** Sends the zone on change, with a slow keep-alive. */
    private void syncZone(MinecraftServer server, PvpMapDefinition map) {
        if (map == null) return;
        S2C_PvpZonePacket packet = zonePacket(map);
        if (!zoneGate.shouldSend(packet, server.getTickCount())) return;
        forEachPlayer(server, true, player -> PacketDistributor.sendToPlayer(player, packet));
    }

    private void sendZone(ServerPlayer player, PvpMapDefinition map) {
        if (map == null) return;
        PacketDistributor.sendToPlayer(player, zonePacket(map));
    }

    private S2C_PvpZonePacket zonePacket(PvpMapDefinition map) {
        return new S2C_PvpZonePacket(true, ARENA.location(), map.hillMin(), map.hillMax(),
                zoneState, zoneRedPlayers, zoneBluePlayers);
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

    /**
     * Requests an entry-state update. Any number of requests in one tick are sent
     * together at the end of the tick, and only to players whose state changed
     * (see {@link PvpEntryStateSync}).
     */
    public void syncEntryState(MinecraftServer server) {
        entryStateDirty = true;
    }

    private void flushEntryState(MinecraftServer server) {
        if (!entryStateDirty) return;
        boolean hosting = PvpArenaSavedData.get(server).hosting();
        boolean running = phase == PvpPhase.RUNNING;
        int entries = participantCount();
        long now = server.getTickCount();
        boolean pending = false;
        List<ServerPlayer> online = server.getPlayerList().getPlayers();
        List<UUID> onlineIds = new ArrayList<>(online.size());
        for (ServerPlayer player : online) {
            onlineIds.add(player.getUUID());
            PvpEntryStateSync.State state = new PvpEntryStateSync.State(
                    isParticipant(player), isActive(player), hosting, running, entries);
            switch (entryStateSync.decide(player.getUUID(), state, now)) {
                case SEND -> PacketDistributor.sendToPlayer(player, new S2C_PvpEntryStatePacket(
                        state.joined(), state.active(), hosting, running, entries));
                case DEFER -> pending = true;
                case SKIP -> { }
            }
        }
        entryStateSync.retainOnly(onlineIds);
        entryStateDirty = pending;
    }

    private void clearClientState(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, S2C_PvpHudPacket.inactive());
        PacketDistributor.sendToPlayer(player, S2C_PvpZonePacket.inactive());
        PacketDistributor.sendToPlayer(player, new S2C_PvpTeamPacket(List.of()));
        PacketDistributor.sendToPlayer(player, S2C_PvpResultPacket.clear());
        PacketDistributor.sendToPlayer(player, S2C_PvpDeployPacket.closed());
    }

    private void recordZoneForTeam(MinecraftServer server, PvpTeam team, PvpMapDefinition map) {
        for (ServerPlayer player : participants(server, true)) {
            if (team(player) == team && !respawns.containsKey(player.getUUID())
                    && inside(player.blockPosition(), map.hillMin(), map.hillMax())) {
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
        loadoutSelections.keySet().retainAll(teams.keySet());
    }

    private static Component loadoutMessage(PvpLoadoutDefinition loadout, boolean updated) {
        return MoveEarthMessage.success(updated
                ? "PvPロードアウトを [" + loadout.displayName() + "] に変更しました。"
                : "ロードアウト [" + loadout.displayName() + "] でPvPに参加登録しました。");
    }

    private void rebalanceTeams() {
        int index = 0;
        for (UUID id : teams.keySet()) teams.put(id, index++ % 2 == 0 ? PvpTeam.RED : PvpTeam.BLUE);
    }

    private boolean rejectStart(MinecraftServer server, String reason) {
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.error("PvP: " + reason), false);
        return false;
    }

    private boolean rejectStart(MinecraftServer server, Component reason) {
        server.getPlayerList().broadcastSystemMessage(
                MoveEarthMessage.error(Component.literal("PvP: ").append(reason)), false);
        return false;
    }

    /** Tells the player to wait and returns true while their join/leave cooldown runs. */
    private boolean rejectForEntryCooldown(ServerPlayer player) {
        long remaining = entryCooldown.remainingTicks(player.getUUID(), player.server.getTickCount());
        if (remaining <= 0) return false;
        player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.pvp.entry_cooldown", PvpEntryCooldown.secondsCeil(remaining))));
        return true;
    }

    private int count(PvpTeam team, boolean activeOnly) {
        return (int) teams.entrySet().stream()
                .filter(entry -> entry.getValue() == team)
                .filter(entry -> !activeOnly || snapshots.containsKey(entry.getKey()))
                .count();
    }

    public List<ServerPlayer> participants(MinecraftServer server, boolean activeOnly) {
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
        INSTANCE.forEachPlayer(server, true, player -> player.sendSystemMessage(MoveEarthMessage.info(message)));
    }

    private static void cleanupArenaMobs(ServerLevel arena) {
        for (Entity entity : arena.getAllEntities()) if (entity instanceof Mob) entity.discard();
    }

    private static boolean inside(BlockPos position, BlockPos first, BlockPos second) {
        return position.getX() >= Math.min(first.getX(), second.getX()) && position.getX() <= Math.max(first.getX(), second.getX())
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
        lastDeployPoints.clear();
        activeMap = null;
        activeBounds = null;
        outOfBounds.clear();
        redScore = blueScore = ticksLeft = syncTicker = 0;
        zoneState = PvpZoneState.NEUTRAL;
        zoneRedPlayers = zoneBluePlayers = 0;
        teams.clear();
        loadoutSelections.clear();
        snapshots.clear();
        lastDamageTicks.clear();
        respawns.clear();
        rewardedKills.clear();
        matchStats.clear();
        matchResultsRecorded = false;
        resetAnnouncerState();
        hudGate.reset();
        zoneGate.reset();
        PvpMapVoteManager.INSTANCE.cancelVote();
        PvpSpawnSelector.INSTANCE.clear();
    }

    private static final class KillAnnouncementState {
        int streak;
        int multiKills;
        int lastKillTick = -1;

        private void reset() {
            streak = 0;
            multiKills = 0;
            lastKillTick = -1;
        }
    }

    private static final class RespawnState {
        int ticks;
        final double x, y, z;
        final float yaw, pitch;
        /** Whether the deploy screen is open, after the killcam. */
        boolean deploying;
        int autoTicks;
        long chosenPoint = S2C_PvpDeployPacket.NO_POINT;
        /** The opening deploy of a match or a join, with no death behind it. */
        boolean first;
        boolean hasKiller;
        double killerX, killerZ;

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

    /** What the HUD shows; ticksLeft is reduced to the displayed second. */
    private record HudKey(int red, int blue, int target, int seconds, String hill) {}

    private static final class MatchStats {
        int kills;
        int deaths;
        float damage;
    }

    private record MatchResultEntry(String name, PvpTeam team, MatchStats stats) {}
}
