package com.ruskserver.moveearth_addtional.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class PlayerWhitelistSavedData extends SavedData {

    private final Map<UUID, Set<String>> whitelists = new HashMap<>();

    public PlayerWhitelistSavedData() {
    }

    public Set<String> getWhitelist(UUID owner) {
        return whitelists.computeIfAbsent(owner, k -> new HashSet<>());
    }

    public void addToWhitelist(UUID owner, String name) {
        getWhitelist(owner).add(name);
        this.setDirty();
    }

    public void removeFromWhitelist(UUID owner, String name) {
        getWhitelist(owner).remove(name);
        this.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Set<String>> entry : whitelists.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("OwnerUUID", entry.getKey());

            ListTag whitelistTag = new ListTag();
            for (String name : entry.getValue()) {
                whitelistTag.add(StringTag.valueOf(name));
            }
            entryTag.put("Whitelist", whitelistTag);
            list.add(entryTag);
        }
        tag.put("PlayerWhitelists", list);
        return tag;
    }

    public static PlayerWhitelistSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerWhitelistSavedData data = new PlayerWhitelistSavedData();
        if (tag.contains("PlayerWhitelists", Tag.TAG_LIST)) {
            ListTag list = tag.getList("PlayerWhitelists", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entryTag = list.getCompound(i);
                if (entryTag.hasUUID("OwnerUUID")) {
                    UUID owner = entryTag.getUUID("OwnerUUID");
                    Set<String> whitelist = new HashSet<>();
                    ListTag whitelistTag = entryTag.getList("Whitelist", Tag.TAG_STRING);
                    for (int j = 0; j < whitelistTag.size(); j++) {
                        whitelist.add(whitelistTag.getString(j));
                    }
                    data.whitelists.put(owner, whitelist);
                }
            }
        }
        return data;
    }

    public static PlayerWhitelistSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        PlayerWhitelistSavedData::new,
                        PlayerWhitelistSavedData::load,
                        null
                ),
                "player_whitelist"
        );
    }
}
