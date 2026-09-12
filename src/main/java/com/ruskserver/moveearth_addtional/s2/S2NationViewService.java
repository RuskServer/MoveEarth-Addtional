package com.ruskserver.moveearth_addtional.s2;

import com.ruskserver.moveearth_addtional.network.S2C_S2HubSnapshotPacket;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryUpkeepPolicy;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeTimerPolicy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.UUID;

/** Server-only boundary between the S2 UI and the nation persistence layer. */
public final class S2NationViewService {
    public static final S2NationViewService INSTANCE = new S2NationViewService();

    private S2NationViewService() {
    }

    public S2NationSnapshot snapshotFor(ServerPlayer player) {
        boolean serverAdmin = player.createCommandSourceStack().hasPermission(2);
        NationSavedData data = NationSavedData.get(player.server);
        data.updateKnownName(player.getUUID(), player.getGameProfile().getName());
        NationSavedData.Nation nation = data.nationFor(player.getUUID()).orElse(null);
        if (nation == null) {
            var invitations = data.invitationFor(player.getUUID()).stream()
                    .map(invite -> data.nation(invite.nationId()).orElse(null))
                    .filter(java.util.Objects::nonNull)
                    .map(invitedNation -> new S2NationSnapshot.InvitationView(
                            invitedNation.id(), invitedNation.name(), invitedNation.tag()))
                    .toList();
            return new S2NationSnapshot(data.revision(), player.getGameProfile().getName(),
                    serverAdmin, false, "", "", "", 0L, 0, 0, 0, 0, 0L,
                    "NO ACTIVE SIEGE", java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), invitations,
                    java.util.List.of());
        }

        NationSavedData.Member ownMember = nation.members().get(player.getUUID());
        NationSavedData.Role ownRole = ownMember == null ? null : nation.roles().get(ownMember.roleId());
        long permissions = ownRole == null ? 0L : ownRole.permissionMask();
        var members = nation.members().values().stream()
                .map(member -> new S2NationSnapshot.MemberView(member.id(), member.lastKnownName(), member.roleId(),
                        java.util.Optional.ofNullable(nation.roles().get(member.roleId()))
                                .map(NationSavedData.Role::displayName).orElse("Member"),
                        player.server.getPlayerList().getPlayer(member.id()) != null,
                        member.lastSeenAt()))
                .sorted(Comparator.comparing(S2NationSnapshot.MemberView::online).reversed()
                        .thenComparing(S2NationSnapshot.MemberView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        var roles = nation.roles().values().stream()
                .map(role -> new S2NationSnapshot.RoleView(role.id(), role.displayName(), role.permissionMask(),
                        (int) nation.members().values().stream()
                                .filter(member -> role.id().equals(member.roleId())).count()))
                .toList();
        int online = (int) members.stream().filter(S2NationSnapshot.MemberView::online).count();
        boolean canManageMembers = (permissions & S2Permission.MANAGE_MEMBERS.mask()) != 0L;
        var candidates = canManageMembers ? player.server.getPlayerList().getPlayers().stream()
                .filter(candidate -> !candidate.getUUID().equals(player.getUUID()))
                .filter(candidate -> data.nationFor(candidate.getUUID()).isEmpty())
                .filter(candidate -> data.invitationFor(candidate.getUUID()).isEmpty())
                .map(candidate -> new S2NationSnapshot.CandidateView(
                        candidate.getUUID(), candidate.getGameProfile().getName()))
                .sorted(Comparator.comparing(S2NationSnapshot.CandidateView::name,
                        String.CASE_INSENSITIVE_ORDER)).toList() : java.util.List.<S2NationSnapshot.CandidateView>of();
        TerritorySavedData territories = TerritorySavedData.get(player.server);
        var diplomacy = data.nations().values().stream()
                .filter(other -> !other.id().equals(nation.id()))
                .map(other -> new S2NationSnapshot.DiplomacyView(other.id(), other.name(), other.tag(),
                        S2NationSnapshot.DiplomacyState.valueOf(
                                data.relation(nation.id(), other.id()).name()),
                        data.isHostileFrom(nation.id(), other.id())))
                .sorted(Comparator.comparing(S2NationSnapshot.DiplomacyView::nationName,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        int controlledChunks = territories.controlledChunkCount(nation.id());
        int controlledCores = territories.controlledCoreCount(nation.id());
        long upkeep = TerritoryUpkeepPolicy.calculateConfigured(controlledChunks,
                territories.activeOutpostCount(nation.id()));
        SiegeSavedData siegeData = SiegeSavedData.get(player.server);
        java.util.List<S2NationSnapshot.SiegeView> sieges = new java.util.ArrayList<>();
        sieges.addAll(siegeData.activeFor(nation.id()).stream().map(siege -> {
            boolean attacker = siege.attackerNation().equals(nation.id());
            var opponent = data.nation(attacker ? siege.defenderNation() : siege.attackerNation()).orElse(null);
            var core = territories.cores().stream().filter(candidate -> candidate.id().equals(siege.coreId()))
                    .findFirst().orElse(null);
            net.minecraft.server.level.ServerLevel coreLevel = player.server.getLevel(
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION, siege.dimension()));
            boolean offlineDefense = core != null && coreLevel != null
                    && com.ruskserver.moveearth_addtional.s2.siege.OfflineDefenseService
                    .divisor(coreLevel, core) > 1;
            UUID opponentId = attacker ? siege.defenderNation() : siege.attackerNation();
            return new S2NationSnapshot.SiegeView(siege.id(), opponentId,
                    opponent == null ? "Unknown" : opponent.name(),
                    opponent == null ? "" : opponent.tag(), attacker,
                    siege.phase() == SiegeTimerPolicy.Phase.ROLLING
                            ? S2NationSnapshot.SiegePhase.ROLLING : S2NationSnapshot.SiegePhase.INITIAL_LOCK,
                    siege.remainingTicks(), siege.dimension().toString(),
                    siege.corePos().getX(), siege.corePos().getY(), siege.corePos().getZ(),
                    core == null ? 0 : core.health(), core == null ? 1 : core.maximumHealth(),
                    0L, 0L, 0, offlineDefense);
        }).toList());
        sieges.addAll(siegeData.fallenFor(nation.id()).stream().map(fallen -> {
            boolean attacker = fallen.attackerNation().equals(nation.id());
            var opponent = data.nation(attacker ? fallen.defenderNation() : fallen.attackerNation()).orElse(null);
            var core = territories.cores().stream().filter(candidate -> candidate.id().equals(fallen.coreId()))
                    .findFirst().orElse(null);
            UUID opponentId = attacker ? fallen.defenderNation() : fallen.attackerNation();
            return new S2NationSnapshot.SiegeView(fallen.siegeId(), opponentId,
                    opponent == null ? "Unknown" : opponent.name(), opponent == null ? "" : opponent.tag(),
                    attacker, S2NationSnapshot.SiegePhase.FALLEN, fallen.remainingTicks(),
                    fallen.dimension().toString(), fallen.corePos().getX(), fallen.corePos().getY(),
                    fallen.corePos().getZ(), core == null ? 0 : core.health(),
                    core == null ? 1 : core.maximumHealth(), fallen.captureTicks(),
                    com.ruskserver.moveearth_addtional.config.S2TerritoryConfig.siegeCounterCaptureTicks(),
                    fallen.stage(), false);
        }).toList());
        sieges.sort(Comparator.comparing(S2NationSnapshot.SiegeView::remainingTicks));
        var peaceProposals = com.ruskserver.moveearth_addtional.s2.siege.PeaceSavedData
                .get(player.server).forNation(nation.id()).stream().map(proposal -> {
                    boolean incoming = proposal.receiverNation().equals(nation.id());
                    UUID opponentId = incoming ? proposal.proposerNation() : proposal.receiverNation();
                    NationSavedData.Nation opponent = data.nation(opponentId).orElse(null);
                    return new S2NationSnapshot.PeaceView(proposal.id(), opponentId,
                            opponent == null ? "Unknown" : opponent.name(),
                            opponent == null ? "" : opponent.tag(), incoming,
                            proposal.goldCompensation(), proposal.remainingTicks());
                }).toList();
        var truces = data.nations().values().stream()
                .filter(other -> !other.id().equals(nation.id()))
                .map(other -> new S2NationSnapshot.TruceView(other.id(), other.name(), other.tag(),
                        siegeData.peaceTruceRemaining(nation.id(), other.id())))
                .filter(truce -> truce.remainingTicks() > 0L)
                .sorted(Comparator.comparingLong(S2NationSnapshot.TruceView::remainingTicks))
                .toList();
        var prisoners = com.ruskserver.moveearth_addtional.s2.siege.PrisonerSavedData
                .get(player.server).betweenNation(nation.id()).stream().map(prisoner -> {
                    boolean heldByViewer = prisoner.holdingNation().equals(nation.id());
                    UUID opponentId = heldByViewer ? prisoner.homeNation() : prisoner.holdingNation();
                    NationSavedData.Nation opponent = data.nation(opponentId).orElse(null);
                    NationSavedData.Nation home = data.nation(prisoner.homeNation()).orElse(null);
                    NationSavedData.Member captive = home == null ? null : home.members().get(prisoner.playerId());
                    return new S2NationSnapshot.PrisonerView(prisoner.playerId(),
                            captive == null ? "Unknown" : captive.lastKnownName(), opponentId,
                            opponent == null ? "Unknown" : opponent.name(),
                            opponent == null ? "" : opponent.tag(), heldByViewer);
                }).toList();
        TerritorySavedData.VaultChunk vault = territories.vaultChunk(nation.id()).orElse(null);
        long settlementTruce = Math.max(siegeData.nationSettlementTruceRemaining(nation.id()),
                territories.cores().stream().filter(core -> core.nationId().equals(nation.id()))
                        .mapToLong(core -> siegeData.coreSettlementTruceRemaining(core.id()))
                        .max().orElse(0L));
        long peaceTruce = data.nations().keySet().stream()
                .filter(other -> !other.equals(nation.id()))
                .mapToLong(other -> siegeData.peaceTruceRemaining(nation.id(), other))
                .max().orElse(0L);
        String siegeStatus = sieges.isEmpty() && settlementTruce > 0L
                ? "REBUILDING TRUCE • " + formatRemaining(settlementTruce)
                : sieges.isEmpty() && peaceTruce > 0L
                ? "PEACE TRUCE • " + formatRemaining(peaceTruce)
                : sieges.isEmpty() ? "NO ACTIVE SIEGE"
                : (sieges.getFirst().phase() == S2NationSnapshot.SiegePhase.ROLLING ? "ROLLING"
                : sieges.getFirst().phase() == S2NationSnapshot.SiegePhase.FALLEN ? "CORE FALLEN" : "INITIAL LOCK")
                + " • " + formatRemaining(sieges.getFirst().remainingTicks());
        return new S2NationSnapshot(data.revision(), player.getGameProfile().getName(), serverAdmin,
                true, nation.name(), nation.tag(), ownRole == null ? "Member" : ownRole.displayName(),
                java.util.Optional.ofNullable(nation.members().get(nation.ownerId()))
                        .map(NationSavedData.Member::lastKnownName).orElse("Unknown"), permissions,
                online, members.size(), controlledChunks,
                controlledCores, upkeep, siegeStatus, vault != null,
                vault == null ? "" : vault.dimension().toString(),
                vault == null ? 0 : vault.chunkX(), vault == null ? 0 : vault.chunkZ(),
                territories.vaultChangeCooldown(nation.id()), sieges, peaceProposals, truces, prisoners,
                members, roles, diplomacy,
                java.util.List.of(), candidates);
    }

    public void sendHub(ServerPlayer player, S2HubTab tab) {
        PacketDistributor.sendToPlayer(player, new S2C_S2HubSnapshotPacket(tab, snapshotFor(player)));
    }

    private static String formatRemaining(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
    }
}
