package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Single authority used by storage menus, block breaking and wreckage recovery. */
public final class SiegeLootService {
    private SiegeLootService() { }

    public static Access access(ServerPlayer player, BlockPos pos) {
        if (player.hasPermissions(2)) return new Access(true, null, null, null);
        MinecraftServer server = player.server;
        ResourceLocation dimension = player.level().dimension().location();
        UUID nation = NationSavedData.get(server).nationIdFor(player.getUUID()).orElse(null);
        TerritorySavedData territories = TerritorySavedData.get(server);
        for (SiegeSavedData.FallenRecord fallen : SiegeSavedData.get(server).fallenRecords()) {
            if (!inside(dimension, pos, fallen.dimension(), fallen.corePos(), fallen.radius())) continue;
            boolean attacker = fallen.individualAttacker()
                    ? fallen.attackerNation().equals(player.getUUID())
                    : fallen.attackerNation().equals(nation);
            boolean coreChunk = sameChunk(pos, fallen.corePos());
            boolean vault = isVault(territories, fallen.defenderNation(), dimension, pos);
            return new Access(SiegeLootPolicy.fallenAccess(fallen.stage(), attacker, coreChunk, vault),
                    fallen.defenderNation(), fallen.siegeId(), null);
        }
        long now = OpenTimeService.now(server);
        SiegeLootSavedData loot = SiegeLootSavedData.get(server);
        loot.purgeExpired(now);
        for (SiegeLootSavedData.LootGrant grant : newestFirst(loot)) {
            boolean inside = inside(dimension, pos, grant.dimension(), grant.corePos(), grant.radius());
            if (!inside) continue;
            boolean attacker = grant.individualAttacker() ? grant.attackerId().equals(player.getUUID())
                    : grant.attackerId().equals(nation);
            boolean vault = isVault(territories, grant.defenderNation(), dimension, pos);
            return new Access(SiegeLootPolicy.finalizedAccess(now, grant.expiresOpenTick(), attacker, true, vault),
                    grant.defenderNation(), grant.siegeId(), grant.expiresOpenTick());
        }
        return new Access(false, null, null, null);
    }

    public static UUID formerOwnerAt(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        for (SiegeSavedData.FallenRecord fallen : SiegeSavedData.get(server).fallenRecords())
            if (inside(dimension, pos, fallen.dimension(), fallen.corePos(), fallen.radius())) return fallen.defenderNation();
        for (SiegeLootSavedData.LootGrant grant : newestFirst(SiegeLootSavedData.get(server)))
            if (inside(dimension, pos, grant.dimension(), grant.corePos(), grant.radius())) return grant.defenderNation();
        return TerritorySavedData.get(server).controllingNation(server, dimension, pos).orElse(null);
    }

    public static PositionAccess accessForPosition(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        for (SiegeSavedData.FallenRecord fallen : SiegeSavedData.get(server).fallenRecords())
            if (inside(dimension, pos, fallen.dimension(), fallen.corePos(), fallen.radius()))
                return new PositionAccess(fallen.defenderNation(), fallen.siegeId());
        for (SiegeLootSavedData.LootGrant grant : newestFirst(SiegeLootSavedData.get(server)))
            if (inside(dimension, pos, grant.dimension(), grant.corePos(), grant.radius()))
                return new PositionAccess(grant.defenderNation(), grant.siegeId());
        return new PositionAccess(TerritorySavedData.get(server)
                .controllingNation(server, dimension, pos).orElse(null), null);
    }

    /**
     * Whether a loot window is open over this position right now. Expired grants still mark who
     * owned the area (see {@link #formerOwnerAt}), but they no longer stop automation there;
     * cross-nation extraction after the window is refused by the storage ownership rule instead.
     */
    public static boolean isLootRestrictedPosition(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        for (SiegeSavedData.FallenRecord fallen : SiegeSavedData.get(server).fallenRecords())
            if (inside(dimension, pos, fallen.dimension(), fallen.corePos(), fallen.radius())) return true;
        long now = OpenTimeService.now(server);
        for (SiegeLootSavedData.LootGrant grant : newestFirst(SiegeLootSavedData.get(server)))
            if (inside(dimension, pos, grant.dimension(), grant.corePos(), grant.radius()))
                return now < grant.expiresOpenTick();
        return false;
    }

    /** Whether an attacker's finalized loot window against this nation is still open. */
    public static boolean lootWindowOpenAgainst(MinecraftServer server, UUID nationId) {
        long now = OpenTimeService.now(server);
        for (SiegeLootSavedData.LootGrant grant : SiegeLootSavedData.get(server).grants())
            if (grant.defenderNation().equals(nationId) && now < grant.expiresOpenTick()) return true;
        return false;
    }

    /** A later fall of the same area supersedes the earlier grant, expired or not. */
    private static java.util.List<SiegeLootSavedData.LootGrant> newestFirst(SiegeLootSavedData loot) {
        java.util.List<SiegeLootSavedData.LootGrant> grants = new java.util.ArrayList<>(loot.grants());
        java.util.Collections.reverse(grants);
        return grants;
    }

    private static boolean inside(ResourceLocation actualDimension, BlockPos pos,
                                  ResourceLocation areaDimension, BlockPos center, int radius) {
        return actualDimension.equals(areaDimension)
                && Math.abs((pos.getX() >> 4) - (center.getX() >> 4)) <= radius
                && Math.abs((pos.getZ() >> 4) - (center.getZ() >> 4)) <= radius;
    }

    private static boolean sameChunk(BlockPos first, BlockPos second) {
        return (first.getX() >> 4) == (second.getX() >> 4) && (first.getZ() >> 4) == (second.getZ() >> 4);
    }

    private static boolean isVault(TerritorySavedData territories, UUID owner,
                                   ResourceLocation dimension, BlockPos pos) {
        TerritorySavedData.VaultChunk vault = territories.vaultChunk(owner).orElse(null);
        return vault != null && vault.dimension().equals(dimension)
                && vault.chunkX() == (pos.getX() >> 4) && vault.chunkZ() == (pos.getZ() >> 4);
    }

    public record Access(boolean allowed, UUID formerOwner, UUID siegeId, Long expiresOpenTick) { }
    public record PositionAccess(UUID ownerNation, UUID siegeId) { }
}
