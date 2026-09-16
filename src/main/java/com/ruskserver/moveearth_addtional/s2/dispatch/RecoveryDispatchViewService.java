package com.ruskserver.moveearth_addtional.s2.dispatch;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.RecoveryDispatchConfig;
import com.ruskserver.moveearth_addtional.network.C2S_RecoveryDispatchActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_RecoveryDispatchActionResultPacket;
import com.ruskserver.moveearth_addtional.network.S2C_RecoveryDispatchSnapshotPacket;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.recovery.NationRecoverySavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.RecoveryFundSavedData;
import com.ruskserver.moveearth_addtional.s2.recovery.RecoveryFundService;
import com.ruskserver.moveearth_addtional.s2.recovery.WarHistorySavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RecoveryDispatchViewService {
    private static final Map<UUID, LinkedHashMap<Integer, CachedResult>> REPLAY = new java.util.HashMap<>();
    private RecoveryDispatchViewService() { }

    public static void send(ServerPlayer player, boolean openScreen) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        boolean admin = player.hasPermissions(2);
        long now = OpenTimeService.now(player.server);
        NationRecoverySavedData recoveryData = NationRecoverySavedData.get(player.server);
        NationRecoverySavedData.Episode episode = nationId == null ? null
                : recoveryData.activeForNation(nationId).orElseGet(() -> recoveryData.forNation(nationId).stream().findFirst().orElse(null));
        S2C_RecoveryDispatchSnapshotPacket.RecoveryView recovery = episode == null ? null
                : new S2C_RecoveryDispatchSnapshotPacket.RecoveryView(episode.id(), episode.revision(),
                episode.state().name(), Math.max(0L, episode.expiresAt() - now), episode.supportPercent(),
                episode.resealed(), episode.healthyWalls(), episode.wallTarget(), episode.upkeepPaid(),
                episode.aidUsed(), episode.aidReserved(), episode.individualAttacker() ? null : episode.attackerId(),
                episode.individualAttacker() ? "" : nationName(nations, episode.attackerId()),
                nationName(nations, episode.rivalNation()),
                recoveryData.protectionWaived(episode.id()));
        List<S2C_RecoveryDispatchSnapshotPacket.ContractView> contracts = admin
                ? DispatchContractSavedData.get(player.server).all().stream()
                .map(value -> contractView(nations, player, value)).toList()
                : nationId == null
                ? DispatchContractSavedData.get(player.server).all().stream()
                .filter(value -> value.participants().contains(player.getUUID())).map(value -> contractView(nations, player, value)).toList()
                : DispatchContractSavedData.get(player.server).forNation(nationId).stream()
                .map(value -> contractView(nations, player, value)).toList();
        List<S2C_RecoveryDispatchSnapshotPacket.NationOption> nationOptions = nations.nations().values().stream()
                .map(value -> new S2C_RecoveryDispatchSnapshotPacket.NationOption(value.id(), value.name(), value.tag(),
                        value.members().values().stream().map(member ->
                                new S2C_RecoveryDispatchSnapshotPacket.MemberOption(member.id(), member.lastKnownName())).toList()))
                .toList();
        List<S2C_RecoveryDispatchSnapshotPacket.CoreOption> cores = TerritorySavedData.get(player.server).cores().stream()
                .map(core -> new S2C_RecoveryDispatchSnapshotPacket.CoreOption(core.id(), core.nationId(),
                        nationName(nations, core.nationId()), core.type().name(), core.dimension().toString(),
                        core.pos().getX(), core.pos().getY(), core.pos().getZ())).toList();
        List<S2C_RecoveryDispatchSnapshotPacket.HistoryView> history = WarHistorySavedData.get(player.server)
                .visibleTo(nationId, admin).stream().limit(128).map(value ->
                        new S2C_RecoveryDispatchSnapshotPacket.HistoryView(value.openTick(), value.type().name(),
                                nationName(nations, value.primaryNation()), nationName(nations, value.secondaryNation()),
                                value.arguments())).toList();
        RecoveryFundSavedData fund = RecoveryFundSavedData.get(player.server);
        List<S2C_RecoveryDispatchSnapshotPacket.FundReview> reviews = admin
                ? fund.transactions().stream()
                .filter(value -> value.state() == RecoveryFundSavedData.State.REVIEW_REQUIRED).limit(64)
                .map(value -> new S2C_RecoveryDispatchSnapshotPacket.FundReview(value.id(), value.type().name(),
                        nationName(nations, value.nationId()), value.amount(), value.detail())).toList()
                : List.of();
        PacketDistributor.sendToPlayer(player, new S2C_RecoveryDispatchSnapshotPacket(openScreen, now, nationId,
                nationId != null, admin, admin || nations.can(player.getUUID(), S2Permission.MANAGE_SIEGE),
                admin || nations.can(player.getUUID(), S2Permission.MANAGE_TREASURY),
                admin || nations.can(player.getUUID(), S2Permission.MANAGE_DISPATCH),
                admin || nations.can(player.getUUID(), S2Permission.MANAGE_DIPLOMACY),
                fund.balance(), fund.reserved(), recovery, contracts, nationOptions, cores, history, reviews));
    }

    private static S2C_RecoveryDispatchSnapshotPacket.ContractView contractView(NationSavedData nations,
            ServerPlayer viewer, DispatchContractSavedData.Contract value) {
        return new S2C_RecoveryDispatchSnapshotPacket.ContractView(value.id(), value.revision(), value.state().name(),
                nationName(nations, value.employerNation()), nationName(nations, value.providerNation()),
                value.side().name(), coreName(value.targetCoreId()),
                value.participants().stream().map(id -> playerName(nations, id)).toList(), value.consents().size(),
                value.pricePerOpenMinute(), value.maximumOpenTicks(),
                value.billedTicks().values().stream().mapToLong(Long::longValue).sum(), value.requestedSubsidy(), value.ownEscrow(),
                value.subsidyEscrow(), value.employerApproved(), value.providerApproved(), value.subsidyApproved(),
                value.participants().contains(viewer.getUUID()), value.consents().contains(viewer.getUUID()),
                value.endReason());
    }

    private static String coreName(UUID id) { return id == null ? "" : id.toString().substring(0, 8); }
    private static String nationName(NationSavedData data, UUID id) {
        return id == null ? "" : data.nation(id).map(NationSavedData.Nation::name).orElse("Unknown");
    }
    private static String playerName(NationSavedData data, UUID id) {
        for (NationSavedData.Nation nation : data.nations().values()) {
            NationSavedData.Member member = nation.members().get(id);
            if (member != null) return member.lastKnownName();
        }
        return id.toString().substring(0, 8);
    }

    public static void handle(ServerPlayer player, C2S_RecoveryDispatchActionPacket packet) {
        CachedResult cached = REPLAY.getOrDefault(player.getUUID(), new LinkedHashMap<>()).get(packet.requestId());
        if (cached != null) {
            PacketDistributor.sendToPlayer(player, new S2C_RecoveryDispatchActionResultPacket(
                    packet.requestId(), cached.success(), cached.messageKey()));
            send(player, false);
            return;
        }
        boolean success = false;
        String detail = "unsupported";
        switch (packet.action()) {
            case REFRESH -> { success = true; detail = "refreshed"; }
            case CREATE -> {
                DispatchContractSavedData.Side side = packet.option() == 1
                        ? DispatchContractSavedData.Side.DEFENSE : DispatchContractSavedData.Side.OFFENSE;
                var result = DispatchContractService.create(player, packet.secondaryId(), packet.targetId(),
                        packet.tertiaryId(), side, Set.copyOf(packet.participants()), packet.amount(),
                        Math.max(1L, packet.auxiliaryAmount()) * 1_200L, packet.expectedRevision());
                success = result.success(); detail = result.detail();
            }
            case APPROVE, APPROVE_SUBSIDY -> {
                var result = DispatchContractService.approve(player, packet.targetId(), packet.expectedRevision(),
                        packet.action() == C2S_RecoveryDispatchActionPacket.Action.APPROVE_SUBSIDY);
                success = result.success(); detail = result.detail();
            }
            case CONSENT, DECLINE -> {
                var result = DispatchContractService.consent(player, packet.targetId(), packet.expectedRevision(),
                        packet.action() == C2S_RecoveryDispatchActionPacket.Action.CONSENT);
                success = result.success(); detail = result.detail();
            }
            case FUND -> {
                var result = DispatchContractService.fund(player, packet.targetId(), packet.expectedRevision());
                success = result.success(); detail = result.detail();
            }
            case CANCEL -> {
                var result = DispatchContractService.cancel(player, packet.targetId(), "user");
                success = result.success(); detail = result.detail();
            }
            case WAIVE_PROTECTION -> {
                success = waive(player, packet.targetId(), packet.expectedRevision());
                detail = success ? "protection_waived" : "stale_or_no_permission";
            }
            case SET_RIVAL, CLEAR_RIVAL -> {
                success = setRival(player, packet.targetId(), packet.action()
                        == C2S_RecoveryDispatchActionPacket.Action.SET_RIVAL ? packet.secondaryId() : null,
                        packet.expectedRevision());
                detail = success ? "rival_updated" : "stale_or_no_permission";
            }
            case ADMIN_MINT -> {
                if (player.hasPermissions(2) && RecoveryDispatchConfig.fundEnabled()) {
                    var result = RecoveryFundService.creditMint(player.server, packet.amount());
                    success = result.success(); detail = result.detail();
                } else detail = "no_permission";
            }
            case NATION_DONATE -> {
                NationSavedData nations = NationSavedData.get(player.server);
                UUID nation = nations.nationIdFor(player.getUUID()).orElse(null);
                if (nation != null && nations.can(player.getUUID(), S2Permission.MANAGE_TREASURY)
                        && RecoveryDispatchConfig.fundEnabled()) {
                    var result = RecoveryFundService.creditFromNation(player.server, nation, packet.amount());
                    success = result.success(); detail = result.detail();
                } else detail = "no_permission";
            }
        }
        String messageKey = "screen.moveearth_addtional.recovery.result." + detail;
        LinkedHashMap<Integer, CachedResult> results = REPLAY.computeIfAbsent(
                player.getUUID(), ignored -> new LinkedHashMap<>());
        results.put(packet.requestId(), new CachedResult(success, messageKey));
        while (results.size() > 128) results.remove(results.keySet().iterator().next());
        PacketDistributor.sendToPlayer(player, new S2C_RecoveryDispatchActionResultPacket(packet.requestId(), success,
                messageKey));
        send(player, false);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        REPLAY.remove(event.getEntity().getUUID());
    }

    private record CachedResult(boolean success, String messageKey) { }

    private static boolean waive(ServerPlayer player, UUID episodeId, long expectedRevision) {
        NationSavedData nations = NationSavedData.get(player.server);
        NationRecoverySavedData.Episode episode = NationRecoverySavedData.get(player.server).byId(episodeId).orElse(null);
        UUID own = nations.nationIdFor(player.getUUID()).orElse(null);
        return episode != null && episode.revision() == expectedRevision && episode.nationId().equals(own)
                && (player.hasPermissions(2) || nations.can(player.getUUID(), S2Permission.MANAGE_SIEGE))
                && NationRecoverySavedData.get(player.server).waiveProtection(episodeId);
    }

    private static boolean setRival(ServerPlayer player, UUID episodeId, UUID rival, long expectedRevision) {
        if (!RecoveryDispatchConfig.rivalEnabled()) return false;
        NationSavedData nations = NationSavedData.get(player.server);
        NationRecoverySavedData.Episode episode = NationRecoverySavedData.get(player.server).byId(episodeId).orElse(null);
        UUID own = nations.nationIdFor(player.getUUID()).orElse(null);
        if (episode == null || episode.revision() != expectedRevision || !episode.nationId().equals(own)
                || !player.hasPermissions(2) && !nations.can(player.getUUID(), S2Permission.MANAGE_DIPLOMACY)
                || rival != null && (!rival.equals(episode.attackerId()) || nations.nation(rival).isEmpty())) return false;
        if (!NationRecoverySavedData.get(player.server).setRival(episodeId, rival)) return false;
        WarHistorySavedData.get(player.server).append(OpenTimeService.now(player.server),
                rival == null ? WarHistorySavedData.Type.RIVAL_CLEARED : WarHistorySavedData.Type.RIVAL_SET,
                WarHistorySavedData.Visibility.PUBLIC, own, rival, episodeId, List.of());
        NationNotificationService.publish(player.server, List.of(own),
                NationNotificationSavedData.EventType.RIVAL_UPDATED, null, null, null,
                List.of(rival == null ? "cleared" : nationName(nations, rival)));
        return true;
    }
}
