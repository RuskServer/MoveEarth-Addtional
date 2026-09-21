package com.ruskserver.moveearth_addtional.region;

import com.ruskserver.moveearth_addtional.config.RegionResourceConfig;
import com.ruskserver.moveearth_addtional.network.S2C_RegionSnapshotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Describes the region a player is standing in, and sends it to them. */
public final class RegionViewService {

    private RegionViewService() { }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new S2C_RegionSnapshotPacket(build(player)));
    }

    public static RegionSnapshot build(ServerPlayer player) {
        if (!RegionProfiles.ready() || !Level.OVERWORLD.equals(player.level().dimension())) {
            return RegionSnapshot.none();
        }
        int region = RegionResolver.regionAt(player.getBlockX(), player.getBlockZ());
        if (region == RegionGrid.NONE) {
            return RegionSnapshot.none();
        }
        List<String> held = RegionProfiles.exclusivesOf(region);
        // What the region lacks, without saying who has it. The lack is what
        // makes a player go and find out; naming the owner here would answer
        // that for them from a menu.
        List<String> elsewhere = new ArrayList<>();
        for (String material : RegionProfiles.exclusiveMaterials()) {
            if (!held.contains(material)) {
                elsewhere.add(material);
            }
        }
        var assignment = RegionProfiles.assignment(region).orElse(null);
        return new RegionSnapshot(region, held, elsewhere,
                assignment == null ? "" : assignment.specialty(),
                assignment == null ? "" : assignment.shortage(),
                RegionResourceConfig.exclusiveOutsideShare());
    }
}
