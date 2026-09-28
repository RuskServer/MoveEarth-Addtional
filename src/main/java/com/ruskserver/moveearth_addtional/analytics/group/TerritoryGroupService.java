package com.ruskserver.moveearth_addtional.analytics.group;

import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Resolves the analytics "group" of a spot: the nation whose territory controls
 * it, and how the player standing there relates to that nation.
 */
public final class TerritoryGroupService {
    private TerritoryGroupService() { }

    public record Placement(UUID nation, GroupRelation relation) {
        public static final Placement WILDERNESS = new Placement(null, GroupRelation.WILDERNESS);
    }

    public static Placement resolve(ServerPlayer player, BlockPos pos) {
        UUID territory = TerritorySavedData.get(player.server)
                .controllingNation(player.server, player.level().dimension().location(), pos).orElse(null);
        if (territory == null) return Placement.WILDERNESS;
        NationSavedData nations = NationSavedData.get(player.server);
        UUID own = nations.nationIdFor(player.getUUID()).orElse(null);
        boolean allied = own != null && !own.equals(territory) && nations.isAllied(own, territory);
        return new Placement(territory, GroupRelation.of(territory, own, allied));
    }
}
