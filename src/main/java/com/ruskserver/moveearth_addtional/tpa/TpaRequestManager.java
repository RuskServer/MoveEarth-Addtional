package com.ruskserver.moveearth_addtional.tpa;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.beginner.BeginnerKitService;
import com.ruskserver.moveearth_addtional.config.TpaConfig;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TpaRequestManager {
    public static final TpaRequestManager INSTANCE = new TpaRequestManager();
    private static final int REQUEST_TIMEOUT_TICKS = 60 * 20;
    private static final int REQUEST_COOLDOWN_TICKS = 10 * 20;
    private static final double MOVEMENT_TOLERANCE_SQR = 0.25D * 0.25D;
    private static final DateTimeFormatter RESET_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm 'JST'");

    private final Map<UUID, PendingRequest> outgoing = new HashMap<>();
    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private final Map<UUID, Integer> requestCooldownUntil = new HashMap<>();
    private final Map<UUID, Integer> combatUntil = new HashMap<>();

    private TpaRequestManager() {
    }

    public int request(ServerPlayer requester, ServerPlayer target) {
        if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(Component.literal("自分自身にはTPAを送れません。"));
            return 0;
        }
        if (!canUseTpa(requester) || !canUseTpa(target)) {
            requester.sendSystemMessage(Component.literal("PvP参加中、死亡中、スペクテイター、または乗り物搭乗中はTPAを利用できません。"));
            return 0;
        }
        TpaUsageSavedData usage = TpaUsageSavedData.get(requester.server);
        TpaPolicy.Mode mode = modeFor(requester, usage);
        Optional<String> unavailable = unavailableReason(requester, target, mode, usage, System.currentTimeMillis());
        if (unavailable.isPresent()) {
            requester.sendSystemMessage(Component.literal(unavailable.get()));
            return 0;
        }
        if (isInWarmup(requester.getUUID()) || isInWarmup(target.getUUID())) {
            requester.sendSystemMessage(Component.literal("どちらかのプレイヤーがすでにTPAの転送準備中です。"));
            return 0;
        }

        int nowTick = requester.server.getTickCount();
        if (requestCooldownUntil.getOrDefault(requester.getUUID(), 0) > nowTick) {
            requester.sendSystemMessage(Component.literal("TPAリクエストは10秒おきに送信できます。"));
            return 0;
        }

        PendingRequest previous = outgoing.put(requester.getUUID(),
                new PendingRequest(requester.getUUID(), target.getUUID(), nowTick + REQUEST_TIMEOUT_TICKS));
        requestCooldownUntil.put(requester.getUUID(), nowTick + REQUEST_COOLDOWN_TICKS);
        if (previous != null && !previous.targetId.equals(target.getUUID())) {
            ServerPlayer previousTarget = requester.server.getPlayerList().getPlayer(previous.targetId);
            if (previousTarget != null) {
                previousTarget.sendSystemMessage(Component.literal(requester.getScoreboardName()
                        + " のTPAリクエストは取り消されました。"));
            }
        }

        requester.sendSystemMessage(Component.literal(target.getScoreboardName()
                + " にTPAリクエストを送りました。"
                + (mode == TpaPolicy.Mode.BEGINNER ? "初心者合流枠を使用します。" : "")
                + "有効時間は60秒です。"));
        String requesterName = requester.getScoreboardName();
        MutableComponent requestMessage = Component.literal(requesterName + " からTPAリクエストが届きました。"
                        + (mode == TpaPolicy.Mode.BEGINNER ? "（初心者合流枠）" : "") + " ")
                .append(commandButton("[承認]", ChatFormatting.GREEN, "/tpaccept " + requesterName))
                .append(Component.literal(" "))
                .append(commandButton("[拒否]", ChatFormatting.RED, "/tpdeny " + requesterName));
        target.sendSystemMessage(requestMessage);
        return 1;
    }

    public int acceptOnly(ServerPlayer host) {
        List<PendingRequest> requests = requestsFor(host, host.server.getTickCount());
        if (requests.size() != 1) {
            host.sendSystemMessage(Component.literal(requests.isEmpty()
                    ? "有効なTPAリクエストはありません。"
                    : "複数のリクエストがあります。/tpaccept <プレイヤー> で指定してください。"));
            return 0;
        }
        ServerPlayer requester = host.server.getPlayerList().getPlayer(requests.getFirst().requesterId);
        return requester == null ? expireOfflineRequester(host, requests.getFirst()) : accept(host, requester);
    }

    public int accept(ServerPlayer host, ServerPlayer requester) {
        PendingRequest request = outgoing.get(requester.getUUID());
        int nowTick = host.server.getTickCount();
        if (request == null || !request.targetId.equals(host.getUUID()) || request.expiresAtTick < nowTick) {
            if (request != null && request.expiresAtTick < nowTick) {
                outgoing.remove(requester.getUUID());
            }
            host.sendSystemMessage(Component.literal(requester.getScoreboardName()
                    + " からの有効なTPAリクエストはありません。"));
            return 0;
        }
        if (!canUseTpa(host) || !canUseTpa(requester)) {
            host.sendSystemMessage(Component.literal("PvP参加中、死亡中、スペクテイター、または乗り物搭乗中のため承認できません。"));
            return 0;
        }
        if (isInWarmup(host.getUUID()) || isInWarmup(requester.getUUID())) {
            host.sendSystemMessage(Component.literal("どちらかのプレイヤーがすでにTPAの転送準備中です。"));
            return 0;
        }
        TpaUsageSavedData usage = TpaUsageSavedData.get(host.server);
        TpaPolicy.Mode mode = modeFor(requester, usage);
        Optional<String> unavailable = unavailableReason(requester, host, mode, usage, System.currentTimeMillis());
        if (unavailable.isPresent()) {
            notifyCancellation(requester, host, unavailable.get());
            return 0;
        }
        if (DetectorTeleportAccess.findForHost(host).isEmpty()) {
            host.sendSystemMessage(Component.literal("自分が所有者またはホワイトリスト登録者になっている、稼働中のプレイヤー検知ブロック範囲内で承認してください。"));
            return 0;
        }

        outgoing.remove(requester.getUUID());
        int warmupSeconds = TpaConfig.warmupSeconds();
        warmups.put(requester.getUUID(), new Warmup(
                requester.getUUID(),
                host.getUUID(),
                requester.position(),
                host.position(),
                requester.level().dimension(),
                host.level().dimension(),
                warmupSeconds * 20));
        host.sendSystemMessage(Component.literal("TPAを承認しました。" + warmupSeconds + "秒後にテレポートします。その場で待機してください。"));
        requester.sendSystemMessage(Component.literal(host.getScoreboardName()
                + " がTPAを承認しました。" + warmupSeconds + "秒間その場で待機してください。"));
        return 1;
    }

    public int denyOnly(ServerPlayer host) {
        List<PendingRequest> requests = requestsFor(host, host.server.getTickCount());
        if (requests.size() != 1) {
            host.sendSystemMessage(Component.literal(requests.isEmpty()
                    ? "有効なTPAリクエストはありません。"
                    : "複数のリクエストがあります。/tpdeny <プレイヤー> で指定してください。"));
            return 0;
        }
        ServerPlayer requester = host.server.getPlayerList().getPlayer(requests.getFirst().requesterId);
        return requester == null ? expireOfflineRequester(host, requests.getFirst()) : deny(host, requester);
    }

    public int deny(ServerPlayer host, ServerPlayer requester) {
        PendingRequest request = outgoing.get(requester.getUUID());
        if (request == null || !request.targetId.equals(host.getUUID())) {
            host.sendSystemMessage(Component.literal(requester.getScoreboardName()
                    + " からの有効なTPAリクエストはありません。"));
            return 0;
        }
        outgoing.remove(requester.getUUID());
        host.sendSystemMessage(Component.literal("TPAリクエストを拒否しました。"));
        requester.sendSystemMessage(Component.literal(host.getScoreboardName() + " がTPAリクエストを拒否しました。"));
        return 1;
    }

    public int cancel(ServerPlayer requester) {
        boolean removed = false;
        PendingRequest request = outgoing.remove(requester.getUUID());
        if (request != null) {
            removed = true;
            ServerPlayer target = requester.server.getPlayerList().getPlayer(request.targetId);
            if (target != null) {
                target.sendSystemMessage(Component.literal(requester.getScoreboardName()
                        + " がTPAリクエストを取り消しました。"));
            }
        }
        Warmup warmup = warmups.remove(requester.getUUID());
        if (warmup == null) {
            Iterator<Map.Entry<UUID, Warmup>> iterator = warmups.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Warmup> entry = iterator.next();
                if (entry.getValue().hostId.equals(requester.getUUID())) {
                    warmup = entry.getValue();
                    iterator.remove();
                    break;
                }
            }
        }
        if (warmup != null) {
            removed = true;
            ServerPlayer traveler = requester.server.getPlayerList().getPlayer(warmup.requesterId);
            ServerPlayer host = requester.server.getPlayerList().getPlayer(warmup.hostId);
            ServerPlayer counterpart = requester.getUUID().equals(warmup.requesterId) ? host : traveler;
            if (counterpart != null) {
                counterpart.sendSystemMessage(Component.literal(requester.getScoreboardName()
                        + " がTPA待機を取り消しました。"));
            }
        }
        requester.sendSystemMessage(Component.literal(removed
                ? "TPAを取り消しました。"
                : "取り消せるTPAリクエストはありません。"));
        return removed ? 1 : 0;
    }

    public void sendStatus(ServerPlayer player) {
        TpaUsageSavedData usage = TpaUsageSavedData.get(player.server);
        long now = System.currentTimeMillis();
        int beginnerAllowance = TpaConfig.beginnerFreeTeleports();
        int beginnerRemaining = usage.beginnerRemaining(player.getUUID(), beginnerAllowance);
        boolean withinBeginnerTime = TpaPolicy.isWithinBeginnerPlayTime(
                BeginnerKitService.playTimeTicks(player),
                TpaConfig.beginnerPlayTimeHours()
        );
        player.sendSystemMessage(Component.literal("TPA残り回数: " + usage.remaining(player.getUUID())
                + "/" + TpaUsageSavedData.DAILY_LIMIT
                + "（次回リセット: " + RESET_FORMAT.format(OpenDayCycle.nextReset()) + "）\n"
                + "初心者合流: " + beginnerRemaining + "/" + beginnerAllowance + " 回"
                + (withinBeginnerTime && beginnerRemaining > 0 ? "（利用可能）" : "（利用対象外）") + "\n"
                + "移動CD: " + formatRemaining(usage.travelerCooldownRemainingMillis(player.getUUID(), now))
                + " / 受入CD: " + formatRemaining(usage.hostCooldownRemainingMillis(player.getUUID(), now))));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        INSTANCE.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerDamaged(LivingDamageEvent.Pre event) {
        if (event.getNewDamage() <= 0.0F) {
            return;
        }
        int combatLockTicks = TpaConfig.combatLockSeconds() * 20;
        if (combatLockTicks <= 0) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer victim) {
            INSTANCE.markCombat(victim, combatLockTicks);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            INSTANCE.markCombat(attacker, combatLockTicks);
        }
    }

    private void tick(MinecraftServer server) {
        int nowTick = server.getTickCount();
        expireRequests(server, nowTick);

        Iterator<Map.Entry<UUID, Warmup>> iterator = warmups.entrySet().iterator();
        while (iterator.hasNext()) {
            Warmup warmup = iterator.next().getValue();
            ServerPlayer requester = server.getPlayerList().getPlayer(warmup.requesterId);
            ServerPlayer host = server.getPlayerList().getPlayer(warmup.hostId);
            if (requester == null || host == null) {
                iterator.remove();
                notifyCancellation(requester, host, "相手がログアウトしたためTPAを中止しました。");
                continue;
            }
            if (!canUseTpa(requester) || !canUseTpa(host)) {
                iterator.remove();
                notifyCancellation(requester, host, "PvP参加状態、死亡状態、または乗り物搭乗状態が変化したためTPAを中止しました。");
                continue;
            }
            if (!requester.level().dimension().equals(warmup.requesterDimension)
                    || !host.level().dimension().equals(warmup.hostDimension)
                    || requester.position().distanceToSqr(warmup.requesterStartPosition) > MOVEMENT_TOLERANCE_SQR
                    || host.position().distanceToSqr(warmup.hostStartPosition) > MOVEMENT_TOLERANCE_SQR) {
                iterator.remove();
                notifyCancellation(requester, host, "どちらかのプレイヤーが移動したためTPAを中止しました。");
                continue;
            }
            if (isCombatLocked(requester) || isCombatLocked(host)) {
                iterator.remove();
                notifyCancellation(requester, host, "戦闘または被ダメージを検知したためTPAを中止しました。");
                continue;
            }
            warmup.ticksRemaining--;
            if (warmup.ticksRemaining <= 0) {
                iterator.remove();
                executeTeleport(requester, host);
            }
        }

        requestCooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
        combatUntil.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
    }

    private void expireRequests(MinecraftServer server, int nowTick) {
        Iterator<PendingRequest> iterator = outgoing.values().iterator();
        while (iterator.hasNext()) {
            PendingRequest request = iterator.next();
            if (request.expiresAtTick >= nowTick) {
                continue;
            }
            iterator.remove();
            ServerPlayer requester = server.getPlayerList().getPlayer(request.requesterId);
            ServerPlayer host = server.getPlayerList().getPlayer(request.targetId);
            notifyCancellation(requester, host, "TPAリクエストの有効時間が切れました。");
        }
    }

    private void executeTeleport(ServerPlayer requester, ServerPlayer host) {
        if (!canUseTpa(requester) || !canUseTpa(host)) {
            notifyCancellation(requester, host, "TPAを実行できる状態ではありません。");
            return;
        }
        TpaUsageSavedData usage = TpaUsageSavedData.get(requester.server);
        TpaPolicy.Mode mode = modeFor(requester, usage);
        Optional<String> unavailable = unavailableReason(requester, host, mode, usage, System.currentTimeMillis());
        if (unavailable.isPresent()) {
            notifyCancellation(requester, host, unavailable.get());
            return;
        }
        Optional<DetectorTeleportAccess.Access> accessResult = DetectorTeleportAccess.findForHost(host);
        if (accessResult.isEmpty()) {
            notifyCancellation(requester, host, "承認者が有効なプレイヤー検知ブロック範囲外へ出たためTPAを中止しました。");
            return;
        }
        DetectorTeleportAccess.Access access = accessResult.get();
        Optional<Vec3> destination = findSafeDestination(requester, host, access);
        if (destination.isEmpty()) {
            notifyCancellation(requester, host, "安全なテレポート地点が見つからなかったためTPAを中止しました。");
            return;
        }

        Vec3 position = destination.get();
        requester.stopRiding();
        requester.teleportTo(access.level(), position.x, position.y, position.z, host.getYRot(), host.getXRot());
        boolean teleported = requester.serverLevel() == access.level()
                && requester.position().distanceToSqr(position) < 4.0D;
        if (!teleported) {
            notifyCancellation(requester, host, "テレポート処理に失敗しました。回数は消費されていません。");
            return;
        }

        requester.fallDistance = 0.0F;
        long now = System.currentTimeMillis();
        long travelerCooldownMillis = TimeUnit.MINUTES.toMillis(TpaConfig.travelerCooldownMinutes());
        long hostCooldownMillis = TimeUnit.MINUTES.toMillis(mode == TpaPolicy.Mode.BEGINNER
                ? TpaConfig.beginnerHostCooldownMinutes()
                : TpaConfig.hostCooldownMinutes());
        usage.recordSuccessfulTeleport(
                requester.getUUID(),
                host.getUUID(),
                mode,
                now,
                travelerCooldownMillis,
                hostCooldownMillis
        );
        com.ruskserver.moveearth_addtional.analytics.collector.AnalyticsCollectorManager.INSTANCE.recordTpaSuccess(requester);
        int remaining = usage.remaining(requester.getUUID());
        if (mode == TpaPolicy.Mode.BEGINNER) {
            requester.sendSystemMessage(Component.literal("初心者合流TPAが完了しました。初心者枠は残り "
                    + usage.beginnerRemaining(requester.getUUID(), TpaConfig.beginnerFreeTeleports()) + " 回です。"));
        } else {
            requester.sendSystemMessage(Component.literal("TPAが完了しました。残り " + remaining
                    + " 回、移動CDは " + TpaConfig.travelerCooldownMinutes() + " 分です。"));
        }
        host.sendSystemMessage(Component.literal(requester.getScoreboardName() + " を招待しました。受入CDは "
                + (mode == TpaPolicy.Mode.BEGINNER
                ? TpaConfig.beginnerHostCooldownMinutes()
                : TpaConfig.hostCooldownMinutes()) + " 分です。"));
        Moveearth_addtional.LOGGER.info("TPA success requester={} host={} mode={} dimension={} detector={} remaining={}",
                requester.getGameProfile().getName(), host.getGameProfile().getName(),
                mode, access.level().dimension().location(), access.detectorPos(), remaining);
    }

    private static Optional<Vec3> findSafeDestination(ServerPlayer requester, ServerPlayer host,
                                                       DetectorTeleportAccess.Access access) {
        double[][] offsets = {
                {1.25D, 0.0D}, {-1.25D, 0.0D}, {0.0D, 1.25D}, {0.0D, -1.25D},
                {1.25D, 1.25D}, {1.25D, -1.25D}, {-1.25D, 1.25D}, {-1.25D, -1.25D},
                {0.0D, 0.0D}, {2.25D, 0.0D}, {-2.25D, 0.0D}, {0.0D, 2.25D}, {0.0D, -2.25D}
        };
        double[] verticalOffsets = {0.0D, 1.0D, -1.0D};
        ServerLevel level = access.level();
        for (double yOffset : verticalOffsets) {
            for (double[] offset : offsets) {
                Vec3 candidate = new Vec3(host.getX() + offset[0], host.getY() + yOffset,
                        host.getZ() + offset[1]);
                if (!access.contains(candidate)) {
                    continue;
                }
                BlockPos feet = BlockPos.containing(candidate);
                BlockPos floor = BlockPos.containing(candidate.x, candidate.y - 0.01D, candidate.z);
                if (!level.hasChunkAt(feet)
                        || !level.getWorldBorder().isWithinBounds(feet)
                        || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                        || !level.getFluidState(feet).isEmpty()) {
                    continue;
                }
                AABB destinationBox = requester.getBoundingBox().move(
                        candidate.x - requester.getX(), candidate.y - requester.getY(), candidate.z - requester.getZ());
                if (level.noCollision(requester, destinationBox)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean canUseTpa(ServerPlayer player) {
        return player.isAlive()
                && !player.isSpectator()
                && !player.isPassenger()
                && !PvpMatchManager.INSTANCE.isParticipant(player);
    }

    private static TpaPolicy.Mode modeFor(ServerPlayer traveler, TpaUsageSavedData usage) {
        return TpaPolicy.mode(
                BeginnerKitService.playTimeTicks(traveler),
                usage.beginnerUsed(traveler.getUUID()),
                TpaConfig.beginnerPlayTimeHours(),
                TpaConfig.beginnerFreeTeleports()
        );
    }

    private Optional<String> unavailableReason(
            ServerPlayer traveler,
            ServerPlayer host,
            TpaPolicy.Mode mode,
            TpaUsageSavedData usage,
            long nowEpochMs
    ) {
        if (isCombatLocked(traveler) || isCombatLocked(host)) {
            return Optional.of("どちらかのプレイヤーが戦闘・被ダメージ後のTPA禁止時間中です。");
        }
        if (mode == TpaPolicy.Mode.REGULAR) {
            if (usage.remaining(traveler.getUUID()) <= 0) {
                return Optional.of("この開放日のTPA回数を使い切っています。次回リセットは "
                        + RESET_FORMAT.format(OpenDayCycle.nextReset()) + " です。");
            }
            long travelerCooldown = usage.travelerCooldownRemainingMillis(traveler.getUUID(), nowEpochMs);
            if (travelerCooldown > 0L) {
                return Optional.of("申請者のTPA移動CDが残り " + formatRemaining(travelerCooldown) + " あります。");
            }
        }
        long hostCooldown = usage.hostCooldownRemainingMillis(host.getUUID(), nowEpochMs);
        if (hostCooldown > 0L) {
            return Optional.of("受入側のTPA受入CDが残り " + formatRemaining(hostCooldown) + " あります。");
        }
        return Optional.empty();
    }

    private boolean isInWarmup(UUID playerId) {
        if (warmups.containsKey(playerId)) {
            return true;
        }
        return warmups.values().stream().anyMatch(warmup -> warmup.hostId.equals(playerId));
    }

    private boolean isCombatLocked(ServerPlayer player) {
        return combatUntil.getOrDefault(player.getUUID(), 0) > player.server.getTickCount();
    }

    private void markCombat(ServerPlayer player, int durationTicks) {
        int until = player.server.getTickCount() + durationTicks;
        combatUntil.merge(player.getUUID(), until, Math::max);
    }

    private static String formatRemaining(long remainingMillis) {
        if (remainingMillis <= 0L) {
            return "利用可能";
        }
        long totalSeconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return minutes > 0L ? minutes + "分" + seconds + "秒" : seconds + "秒";
    }

    private List<PendingRequest> requestsFor(ServerPlayer host, int nowTick) {
        List<PendingRequest> requests = new ArrayList<>();
        for (PendingRequest request : outgoing.values()) {
            if (request.targetId.equals(host.getUUID()) && request.expiresAtTick >= nowTick) {
                requests.add(request);
            }
        }
        return requests;
    }

    private int expireOfflineRequester(ServerPlayer host, PendingRequest request) {
        outgoing.remove(request.requesterId);
        host.sendSystemMessage(Component.literal("申請者がオフラインになったためTPAリクエストを破棄しました。"));
        return 0;
    }

    private static void notifyCancellation(ServerPlayer requester, ServerPlayer host, String message) {
        if (requester != null) {
            requester.sendSystemMessage(Component.literal(message));
        }
        if (host != null && host != requester) {
            host.sendSystemMessage(Component.literal(message));
        }
    }

    private static Component commandButton(String label, ChatFormatting color, String command) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private record PendingRequest(UUID requesterId, UUID targetId, int expiresAtTick) {
    }

    private static final class Warmup {
        private final UUID requesterId;
        private final UUID hostId;
        private final Vec3 requesterStartPosition;
        private final Vec3 hostStartPosition;
        private final net.minecraft.resources.ResourceKey<Level> requesterDimension;
        private final net.minecraft.resources.ResourceKey<Level> hostDimension;
        private int ticksRemaining;

        private Warmup(
                UUID requesterId,
                UUID hostId,
                Vec3 requesterStartPosition,
                Vec3 hostStartPosition,
                net.minecraft.resources.ResourceKey<Level> requesterDimension,
                net.minecraft.resources.ResourceKey<Level> hostDimension,
                int ticksRemaining
        ) {
            this.requesterId = requesterId;
            this.hostId = hostId;
            this.requesterStartPosition = requesterStartPosition;
            this.hostStartPosition = hostStartPosition;
            this.requesterDimension = requesterDimension;
            this.hostDimension = hostDimension;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
