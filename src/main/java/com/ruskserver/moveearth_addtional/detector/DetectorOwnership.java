package com.ruskserver.moveearth_addtional.detector;

import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** {@link DetectorAccessPolicy} applied to a live detector and the server's nations. */
public final class DetectorOwnership {
    private DetectorOwnership() {
    }

    /**
     * Brings the detector's owner up to date: a player's detector passes to the nation they have joined, and a
     * nation's detector is left ownerless and stopped once the nation is gone, for the next player to claim.
     *
     * @return whether the detector still has an owner
     */
    public static boolean settle(MinecraftServer server, PlayerDetectorBlockEntity detector) {
        NationSavedData nations = NationSavedData.get(server);
        UUID nation = detector.getOwnerNation();
        if (nation != null) {
            if (nations.nation(nation).isPresent()) return true;
            detector.release();
            return false;
        }
        UUID owner = detector.getOwnerUUID();
        if (owner == null) return false;
        // Not approved: the player who brought it in may lack the rights to spend the treasury on it.
        nations.nationIdFor(owner).ifPresent(joined -> detector.setOwnerNation(joined, false));
        return true;
    }

    public static EconomyLedgerSavedData.Account account(PlayerDetectorBlockEntity detector) {
        return detector.getOwnerNation() != null
                ? EconomyLedgerSavedData.Account.nation(detector.getOwnerNation())
                : EconomyLedgerSavedData.Account.player(detector.getOwnerUUID());
    }

    public static DetectorAccessPolicy.Relation relation(NationSavedData nations, PlayerDetectorBlockEntity detector,
                                                         ServerPlayer player) {
        UUID playerNation = nations.nationIdFor(player.getUUID()).orElse(null);
        UUID ownerNation = detector.getOwnerNation();
        return DetectorAccessPolicy.relation(ownerNation, detector.getOwnerUUID(), player.getUUID(), playerNation,
                ownerNation != null && nations.isAllied(ownerNation, playerNation));
    }

    public static boolean mayManage(ServerPlayer player, PlayerDetectorBlockEntity detector) {
        NationSavedData nations = NationSavedData.get(player.server);
        return DetectorAccessPolicy.mayManage(detector.getOwnerNation(), detector.getOwnerUUID(), player.getUUID(),
                nations.nationIdFor(player.getUUID()).orElse(null), nationRights(nations, player),
                player.hasPermissions(2));
    }

    public static boolean mayView(ServerPlayer player, PlayerDetectorBlockEntity detector) {
        NationSavedData nations = NationSavedData.get(player.server);
        return mayManage(player, detector) || DetectorAccessPolicy.mayView(detector.getOwnerNation(),
                detector.getOwnerUUID(), player.getUUID(), nations.nationIdFor(player.getUUID()).orElse(null));
    }

    public static boolean mayPlace(ServerPlayer player) {
        NationSavedData nations = NationSavedData.get(player.server);
        return DetectorAccessPolicy.mayPlace(nations.nationIdFor(player.getUUID()).orElse(null),
                nationRights(nations, player), player.hasPermissions(2));
    }

    /** The players online who hear the detector's alerts: every citizen of its nation, or its owner. */
    public static List<ServerPlayer> watchers(MinecraftServer server, PlayerDetectorBlockEntity detector) {
        NationSavedData nations = NationSavedData.get(server);
        List<ServerPlayer> watchers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (relation(nations, detector, player) == DetectorAccessPolicy.Relation.MEMBER) watchers.add(player);
        }
        return watchers;
    }

    /** "Name [TAG]" of the owning nation, or the owner's name. */
    public static String ownerLabel(MinecraftServer server, PlayerDetectorBlockEntity detector) {
        UUID nation = detector.getOwnerNation();
        if (nation == null) return detector.getOwnerName() == null ? "" : detector.getOwnerName();
        return NationSavedData.get(server).nation(nation)
                .map(found -> found.name() + " [" + found.tag() + "]")
                .orElse("");
    }

    private static boolean nationRights(NationSavedData nations, ServerPlayer player) {
        return nations.can(player.getUUID(), S2Permission.BASTION_ACCESS)
                || nations.can(player.getUUID(), S2Permission.MANAGE_TREASURY);
    }
}
