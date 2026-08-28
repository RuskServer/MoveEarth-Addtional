package com.ruskserver.moveearth_addtional.tpa;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TpaRequestManager {
    public static final TpaRequestManager INSTANCE = new TpaRequestManager();
    private static final int REQUEST_TIMEOUT_TICKS = 60 * 20;
    private static final int REQUEST_COOLDOWN_TICKS = 10 * 20;
    private static final int WARMUP_TICKS = 5 * 20;
    private static final double MOVEMENT_TOLERANCE_SQR = 0.25D * 0.25D;
    private static final DateTimeFormatter RESET_FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm 'JST'");

    private final Map<UUID, PendingRequest> outgoing = new HashMap<>();
    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private final Map<UUID, Integer> cooldownUntil = new HashMap<>();

    private TpaRequestManager() {
    }

    public int request(ServerPlayer requester, ServerPlayer target) {
        if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(Component.literal("自分自身にはTPAを送れません。"));
            return 0;
        }
        if (!canUseTpa(requester) || !canUseTpa(target)) {
            requester.sendSystemMessage(Component.literal("PvP参加中、死亡中、またはスペクテイターにはTPAを利用できません。"));
            return 0;
        }
        TpaUsageSavedData usage = TpaUsageSavedData.get(requester.server);
        if (usage.remaining(requester.getUUID()) <= 0) {
            requester.sendSystemMessage(Component.literal("この開放日のTPA回数を使い切っています。次回リセットは "
                    + RESET_FORMAT.format(OpenDayCycle.nextReset()) + " です。"));
            return 0;
        }
        if (warmups.containsKey(requester.getUUID())) {
            requester.sendSystemMessage(Component.literal("すでにTPAのテレポート待機中です。"));
            return 0;
        }

        int nowTick = requester.server.getTickCount();
        if (cooldownUntil.getOrDefault(requester.getUUID(), 0) > nowTick) {
            requester.sendSystemMessage(Component.literal("TPAリクエストは10秒おきに送信できます。"));
            return 0;
        }

        PendingRequest previous = outgoing.put(requester.getUUID(),
                new PendingRequest(requester.getUUID(), target.getUUID(), nowTick + REQUEST_TIMEOUT_TICKS));
        cooldownUntil.put(requester.getUUID(), nowTick + REQUEST_COOLDOWN_TICKS);
        if (previous != null && !previous.targetId.equals(target.getUUID())) {
            ServerPlayer previousTarget = requester.server.getPlayerList().getPlayer(previous.targetId);
            if (previousTarget != null) {
                previousTarget.sendSystemMessage(Component.literal(requester.getScoreboardName()
                        + " のTPAリクエストは取り消されました。"));
            }
        }

        requester.sendSystemMessage(Component.literal(target.getScoreboardName()
                + " にTPAリクエストを送りました。有効時間は60秒です。"));
        String requesterName = requester.getScoreboardName();
        MutableComponent requestMessage = Component.literal(requesterName + " からTPAリクエストが届きました。 ")
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
            host.sendSystemMessage(Component.literal("PvP参加中、死亡中、またはスペクテイターのため承認できません。"));
            return 0;
        }
        if (host.hurtTime > 0 || requester.hurtTime > 0) {
            host.sendSystemMessage(Component.literal("被ダメージ直後はTPAを承認できません。"));
            return 0;
        }
        if (TpaUsageSavedData.get(host.server).remaining(requester.getUUID()) <= 0) {
            host.sendSystemMessage(Component.literal("申請者はこの開放日のTPA回数を使い切っています。"));
            requester.sendSystemMessage(Component.literal("この開放日のTPA回数を使い切っています。"));
            return 0;
        }
        if (DetectorTeleportAccess.findForHost(host).isEmpty()) {
            host.sendSystemMessage(Component.literal("自分が所有者またはホワイトリスト登録者になっている、稼働中のプレイヤー検知ブロック範囲内で承認してください。"));
            return 0;
        }

        outgoing.remove(requester.getUUID());
        warmups.put(requester.getUUID(), new Warmup(
                requester.getUUID(), host.getUUID(), requester.position(), WARMUP_TICKS));
        host.sendSystemMessage(Component.literal("TPAを承認しました。5秒後にテレポートします。"));
        requester.sendSystemMessage(Component.literal(host.getScoreboardName()
                + " がTPAを承認しました。5秒間その場で待機してください。"));
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
        if (warmup != null) {
            removed = true;
            ServerPlayer host = requester.server.getPlayerList().getPlayer(warmup.hostId);
            if (host != null) {
                host.sendSystemMessage(Component.literal(requester.getScoreboardName()
                        + " のTPA待機が取り消されました。"));
            }
        }
        requester.sendSystemMessage(Component.literal(removed
                ? "TPAを取り消しました。"
                : "取り消せるTPAリクエストはありません。"));
        return removed ? 1 : 0;
    }

    public void sendStatus(ServerPlayer player) {
        TpaUsageSavedData usage = TpaUsageSavedData.get(player.server);
        player.sendSystemMessage(Component.literal("TPA残り回数: " + usage.remaining(player.getUUID())
                + "/" + TpaUsageSavedData.DAILY_LIMIT
                + "（次回リセット: " + RESET_FORMAT.format(OpenDayCycle.nextReset()) + "）"));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        INSTANCE.tick(event.getServer());
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
                notifyCancellation(requester, host, "PvP参加状態などが変化したためTPAを中止しました。");
                continue;
            }
            if (requester.position().distanceToSqr(warmup.startPosition) > MOVEMENT_TOLERANCE_SQR) {
                iterator.remove();
                notifyCancellation(requester, host, "申請者が移動したためTPAを中止しました。");
                continue;
            }
            if (requester.hurtTime > 0 || host.hurtTime > 0) {
                iterator.remove();
                notifyCancellation(requester, host, "被ダメージを検知したためTPAを中止しました。");
                continue;
            }
            warmup.ticksRemaining--;
            if (warmup.ticksRemaining <= 0) {
                iterator.remove();
                executeTeleport(requester, host);
            }
        }

        cooldownUntil.entrySet().removeIf(entry -> entry.getValue() <= nowTick);
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
        Optional<DetectorTeleportAccess.Access> accessResult = DetectorTeleportAccess.findForHost(host);
        if (accessResult.isEmpty()) {
            notifyCancellation(requester, host, "承認者が有効なプレイヤー検知ブロック範囲外へ出たためTPAを中止しました。");
            return;
        }
        TpaUsageSavedData usage = TpaUsageSavedData.get(requester.server);
        if (usage.remaining(requester.getUUID()) <= 0) {
            notifyCancellation(requester, host, "申請者がこの開放日のTPA回数を使い切っているため中止しました。");
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
        usage.tryConsume(requester.getUUID());
        com.ruskserver.moveearth_addtional.analytics.collector.AnalyticsCollectorManager.INSTANCE.recordTpaSuccess(requester);
        int remaining = usage.remaining(requester.getUUID());
        requester.sendSystemMessage(Component.literal("TPAが完了しました。残り " + remaining + " 回です。"));
        host.sendSystemMessage(Component.literal(requester.getScoreboardName() + " を招待しました。"));
        Moveearth_addtional.LOGGER.info("TPA success requester={} host={} dimension={} detector={} remaining={}",
                requester.getGameProfile().getName(), host.getGameProfile().getName(),
                access.level().dimension().location(), access.detectorPos(), remaining);
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
                && !PvpMatchManager.INSTANCE.isParticipant(player);
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
        private final Vec3 startPosition;
        private int ticksRemaining;

        private Warmup(UUID requesterId, UUID hostId, Vec3 startPosition, int ticksRemaining) {
            this.requesterId = requesterId;
            this.hostId = hostId;
            this.startPosition = startPosition;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
