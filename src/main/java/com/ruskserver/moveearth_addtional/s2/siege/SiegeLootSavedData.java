package com.ruskserver.moveearth_addtional.s2.siege;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Restart-safe finalized loot windows. Live fallen access remains authoritative in SiegeSavedData. */
public final class SiegeLootSavedData extends SavedData {
    private final Map<UUID, LootGrant> grants = new LinkedHashMap<>();
    /**
     * Spatial view of {@link #grants} in map order, rebuilt lazily after a change. Grants are never pruned
     * (expired ones still mark the former owner), so position lookups must not scan the whole season.
     */
    private ChunkSquareIndex<LootGrant> index;
    private long revision;

    public void open(SiegeSavedData.FallenRecord fallen, long nowOpenTick) {
        if (fallen == null) return;
        grants.put(fallen.siegeId(), new LootGrant(fallen.siegeId(), fallen.attackerNation(),
                fallen.individualAttacker(), fallen.defenderNation(), fallen.dimension(), fallen.corePos(),
                fallen.radius(), Math.max(0L, nowOpenTick)
                + com.ruskserver.moveearth_addtional.config.S2TerritoryConfig.siegeLootWindowTicks()));
        changed();
    }

    public Optional<LootGrant> grant(UUID siegeId) { return Optional.ofNullable(grants.get(siegeId)); }

    public java.util.List<LootGrant> grants() { return java.util.List.copyOf(grants.values()); }

    /** Read-only live view in grant order, for hot paths that must not copy the season's grants. */
    public java.util.Collection<LootGrant> grantView() {
        return java.util.Collections.unmodifiableCollection(grants.values());
    }

    public boolean isEmpty() { return grants.isEmpty(); }

    /** Increases whenever a grant is added, replaced or removed. */
    public long revision() { return revision; }

    /**
     * The grant a newest-first scan over {@link #grants()} would pick for this chunk: a later fall of
     * the same area supersedes the earlier grant, expired or not.
     */
    public LootGrant newestCovering(ResourceLocation dimension, int chunkX, int chunkZ) {
        if (grants.isEmpty()) return null;
        ChunkSquareIndex<LootGrant> current = index;
        if (current == null) {
            current = new ChunkSquareIndex<>();
            for (LootGrant grant : grants.values()) {
                current.add(grant.dimension(), grant.corePos().getX() >> 4, grant.corePos().getZ() >> 4,
                        grant.radius(), grant);
            }
            index = current;
        }
        return current.last(dimension, chunkX, chunkZ);
    }

    public void revoke(UUID siegeId) {
        if (siegeId != null && grants.remove(siegeId) != null) changed();
    }

    public void revokeBetween(UUID first, UUID second) {
        boolean changed = grants.values().removeIf(value ->
                value.attackerId().equals(first) && value.defenderNation().equals(second)
                        || !value.individualAttacker() && value.attackerId().equals(second)
                        && value.defenderNation().equals(first));
        if (changed) changed();
    }

    private void changed() {
        index = null;
        revision++;
        setDirty();
    }

    public void purgeExpired(long nowOpenTick) {
        // Expired grants remain as ownership boundaries. Their access is closed, but deleting the
        // old-area record would make captured territory ownership accidentally authorize old chests.
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (LootGrant value : grants.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Siege", value.siegeId());
            entry.putUUID("Attacker", value.attackerId());
            entry.putBoolean("Individual", value.individualAttacker());
            entry.putUUID("Defender", value.defenderNation());
            entry.putString("Dimension", value.dimension().toString());
            entry.putLong("CorePos", value.corePos().asLong());
            entry.putInt("Radius", value.radius());
            entry.putLong("ExpiresOpenTick", value.expiresOpenTick());
            list.add(entry);
        }
        tag.put("Grants", list);
        return tag;
    }

    public static SiegeLootSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SiegeLootSavedData data = new SiegeLootSavedData();
        ListTag list = tag.getList("Grants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (!entry.hasUUID("Siege") || !entry.hasUUID("Attacker") || !entry.hasUUID("Defender")
                    || dimension == null) continue;
            LootGrant value = new LootGrant(entry.getUUID("Siege"), entry.getUUID("Attacker"),
                    entry.getBoolean("Individual"), entry.getUUID("Defender"), dimension,
                    BlockPos.of(entry.getLong("CorePos")), Math.max(0, entry.getInt("Radius")),
                    Math.max(0L, entry.getLong("ExpiresOpenTick")));
            data.grants.put(value.siegeId(), value);
        }
        return data;
    }

    public static SiegeLootSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SiegeLootSavedData::new, SiegeLootSavedData::load, null),
                "moveearth_siege_loot");
    }

    public record LootGrant(UUID siegeId, UUID attackerId, boolean individualAttacker,
                            UUID defenderNation, ResourceLocation dimension, BlockPos corePos,
                            int radius, long expiresOpenTick) { }
}
