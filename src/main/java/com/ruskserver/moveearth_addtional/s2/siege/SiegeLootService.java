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
        for (SiegeSavedData.FallenRecord fallen : SiegeSavedData.get(server).fallenRecordView()) {
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
        SiegeLootSavedData.LootGrant grant = newestCovering(loot, dimension, pos);
        if (grant != null) {
            boolean attacker = grant.individualAttacker() ? grant.attackerId().equals(player.getUUID())
                    : grant.attackerId().equals(nation);
            boolean vault = isVault(territories, grant.defenderNation(), dimension, pos);
            return new Access(SiegeLootPolicy.finalizedAccess(now, grant.expiresOpenTick(), attacker, true, vault),
                    grant.defenderNation(), grant.siegeId(), grant.expiresOpenTick());
        }
        return new Access(false, null, null, null);
    }

    public static UUID formerOwnerAt(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        return automationFacts(server, dimension, pos).formerOwner();
    }

    public static PositionAccess accessForPosition(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        SiegeSavedData.FallenRecord fallen = firstFallenCovering(SiegeSavedData.get(server), dimension, pos);
        if (fallen != null) return new PositionAccess(fallen.defenderNation(), fallen.siegeId());
        SiegeLootSavedData.LootGrant grant = newestCovering(SiegeLootSavedData.get(server), dimension, pos);
        if (grant != null) return new PositionAccess(grant.defenderNation(), grant.siegeId());
        return new PositionAccess(TerritorySavedData.get(server)
                .controllingNation(server, dimension, pos).orElse(null), null);
    }

    /**
     * {@link #isLootRestrictedPosition} and {@link #formerOwnerAt} from one pass over the fall and grant
     * records. Both only depend on the chunk of {@code pos}.
     */
    public static AutomationFacts automationFacts(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        SiegeSavedData.FallenRecord fallen = firstFallenCovering(SiegeSavedData.get(server), dimension, pos);
        if (fallen != null) return new AutomationFacts(true, fallen.defenderNation());
        SiegeLootSavedData.LootGrant grant = newestCovering(SiegeLootSavedData.get(server), dimension, pos);
        if (grant != null) {
            return new AutomationFacts(OpenTimeService.now(server) < grant.expiresOpenTick(), grant.defenderNation());
        }
        return new AutomationFacts(false,
                TerritorySavedData.get(server).controllingNation(server, dimension, pos).orElse(null));
    }

    /**
     * Whether a loot window is open over this position right now. Expired grants still mark who
     * owned the area (see {@link #formerOwnerAt}), but they no longer stop automation there;
     * cross-nation extraction after the window is refused by the storage ownership rule instead.
     */
    public static boolean isLootRestrictedPosition(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        if (firstFallenCovering(SiegeSavedData.get(server), dimension, pos) != null) return true;
        SiegeLootSavedData.LootGrant grant = newestCovering(SiegeLootSavedData.get(server), dimension, pos);
        return grant != null && OpenTimeService.now(server) < grant.expiresOpenTick();
    }

    /** Whether an attacker's finalized loot window against this nation is still open. */
    public static boolean lootWindowOpenAgainst(MinecraftServer server, UUID nationId) {
        long now = OpenTimeService.now(server);
        for (SiegeLootSavedData.LootGrant grant : SiegeLootSavedData.get(server).grantView())
            if (grant.defenderNation().equals(nationId) && now < grant.expiresOpenTick()) return true;
        return false;
    }

    /** The oldest live fall whose area holds the position; falls are few, so a scan of the live view. */
    private static SiegeSavedData.FallenRecord firstFallenCovering(SiegeSavedData sieges,
                                                                    ResourceLocation dimension, BlockPos pos) {
        if (!sieges.hasFallenRecords()) return null;
        for (SiegeSavedData.FallenRecord fallen : sieges.fallenRecordView())
            if (inside(dimension, pos, fallen.dimension(), fallen.corePos(), fallen.radius())) return fallen;
        return null;
    }

    /** A later fall of the same area supersedes the earlier grant, expired or not. */
    private static SiegeLootSavedData.LootGrant newestCovering(SiegeLootSavedData loot,
                                                               ResourceLocation dimension, BlockPos pos) {
        return loot.newestCovering(dimension, pos.getX() >> 4, pos.getZ() >> 4);
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
    /** {@code lootRestricted}: a loot window is open here; {@code formerOwner}: see {@link #formerOwnerAt}. */
    public record AutomationFacts(boolean lootRestricted, UUID formerOwner) { }
    public record PositionAccess(UUID ownerNation, UUID siegeId) { }
}
