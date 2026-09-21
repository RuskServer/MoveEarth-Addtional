package com.ruskserver.moveearth_addtional.region;

import com.ruskserver.moveearth_addtional.config.RegionResourceConfig;
import com.ruskserver.moveearth_addtional.network.S2C_RegionSnapshotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds and sends one player's view of the regions around them. */
public final class RegionViewService {

    private RegionViewService() { }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new S2C_RegionSnapshotPacket(build(player)));
    }

    /**
     * The player's own region and the ones bordering it.
     *
     * <p>Neighbours only, rather than every region there is. A list of all eight
     * would answer the question the map is supposed to make you travel to
     * answer, and the ones that matter for trade are the ones you can reach.
     */
    public static RegionSnapshot build(ServerPlayer player) {
        if (!RegionProfiles.ready() || !Level.OVERWORLD.equals(player.level().dimension())) {
            return new RegionSnapshot(RegionGrid.NONE, List.of());
        }
        int current = RegionResolver.regionAt(player.getBlockX(), player.getBlockZ());
        RegionVisitSavedData visits = RegionVisitSavedData.get(player.server);
        boolean revealAll = RegionResourceConfig.revealAllRegions() || player.hasPermissions(2);

        List<RegionSnapshot.Entry> entries = new ArrayList<>();
        if (current != RegionGrid.NONE) {
            // Standing in it counts as knowing it even before the tick that
            // records the visit has run, so the tab never opens on a blank
            // entry for the ground underfoot.
            entries.add(describe(current, true, true));
        }
        Set<Integer> neighbours = current == RegionGrid.NONE
                ? Set.of()
                : RegionProfiles.neighboursOf(current);
        for (int neighbour : neighbours.stream().sorted().toList()) {
            boolean known = revealAll || visits.knows(player.getUUID(), neighbour);
            entries.add(known
                    ? describe(neighbour, false, true)
                    : RegionSnapshot.Entry.unknown(neighbour, false));
        }
        return new RegionSnapshot(current, entries);
    }

    private static RegionSnapshot.Entry describe(int region, boolean current, boolean known) {
        var assignment = RegionProfiles.assignment(region).orElse(null);
        return new RegionSnapshot.Entry(region, current, known,
                RegionProfiles.exclusivesOf(region),
                assignment == null ? "" : assignment.specialty(),
                assignment == null ? "" : assignment.shortage(),
                assignment == null ? 1.0 : assignment.baseDensity());
    }
}
