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

    public void open(SiegeSavedData.FallenRecord fallen, long nowOpenTick) {
        if (fallen == null) return;
        grants.put(fallen.siegeId(), new LootGrant(fallen.siegeId(), fallen.attackerNation(),
                fallen.individualAttacker(), fallen.defenderNation(), fallen.dimension(), fallen.corePos(),
                fallen.radius(), Math.max(0L, nowOpenTick)
                + com.ruskserver.moveearth_addtional.config.S2TerritoryConfig.siegeLootWindowTicks()));
        setDirty();
    }

    public Optional<LootGrant> grant(UUID siegeId) { return Optional.ofNullable(grants.get(siegeId)); }

    public java.util.List<LootGrant> grants() { return java.util.List.copyOf(grants.values()); }

    public void revoke(UUID siegeId) {
        if (siegeId != null && grants.remove(siegeId) != null) setDirty();
    }

    public void revokeBetween(UUID first, UUID second) {
        boolean changed = grants.values().removeIf(value ->
                value.attackerId().equals(first) && value.defenderNation().equals(second)
                        || !value.individualAttacker() && value.attackerId().equals(second)
                        && value.defenderNation().equals(first));
        if (changed) setDirty();
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
