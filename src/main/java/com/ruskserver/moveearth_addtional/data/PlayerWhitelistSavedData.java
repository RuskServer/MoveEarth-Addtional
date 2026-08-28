package com.ruskserver.moveearth_addtional.data;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * プレイヤー検知ブロック用のホワイトリストを管理するSavedData。
 * サーバー共通（オーバーワールド）に永続化され、メンバーはUUIDを主軸として保存される。
 */
public class PlayerWhitelistSavedData extends SavedData {

    private final WhitelistRegistry registry = new WhitelistRegistry();

    public PlayerWhitelistSavedData() {
    }

    public WhitelistRegistry getRegistry() {
        return registry;
    }

    /**
     * 指定したオーナーのホワイトリストに登録されている全メンバーUUIDを取得
     */
    public Set<UUID> getMemberUuids(UUID owner) {
        return registry.getMemberUuids(owner);
    }

    /**
     * 指定したオーナーのホワイトリストメンバー（UUID -> 表示名）のマップを取得
     */
    public Map<UUID, String> getMembers(UUID owner) {
        return registry.getMembers(owner);
    }

    /**
     * 指定したオーナーの未解決プレイヤー名一覧を取得
     */
    public Set<String> getUnresolvedNames(UUID owner) {
        return registry.getUnresolvedNames(owner);
    }

    /**
     * GUI表示およびクライアント同期用：登録済みメンバー名（表示名＋未解決名）の一覧を取得
     */
    public List<String> getMemberNamesForDisplay(UUID owner) {
        return registry.getMemberNamesForDisplay(owner);
    }

    /**
     * 指定したプレイヤーUUIDがオーナーのホワイトリストに含まれているかを判定
     */
    public boolean isWhitelisted(UUID owner, UUID playerUuid) {
        return registry.isWhitelisted(owner, playerUuid);
    }

    /**
     * 指定したプレイヤー（ServerPlayer）がオーナーのホワイトリストに含まれているかを判定
     */
    public boolean isWhitelisted(UUID owner, ServerPlayer player) {
        return isWhitelisted(owner, player.getUUID());
    }

    /**
     * メンバーをホワイトリストに追加
     */
    public void addToWhitelist(UUID owner, UUID memberUuid, @Nullable String memberName) {
        registry.addToWhitelist(owner, memberUuid, memberName);
        this.setDirty();
    }

    /**
     * メンバーをUUID指定でホワイトリストから削除
     */
    public boolean removeFromWhitelist(UUID owner, UUID memberUuid) {
        boolean removed = registry.removeFromWhitelist(owner, memberUuid);
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    /**
     * 名前指定でホワイトリスト（または未解決リスト）から削除
     */
    public boolean removeFromWhitelistByName(UUID owner, String memberName) {
        boolean removed = registry.removeFromWhitelistByName(owner, memberName);
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    /**
     * 未解決プレイヤー名の解決を試行
     */
    public void tryResolveUnresolved(@Nullable MinecraftServer server) {
        if (server == null) {
            return;
        }

        GameProfileCache profileCache = server.getProfileCache();
        boolean changed = false;

        for (UUID owner : registry.getAllOwners()) {
            Set<String> names = new HashSet<>(registry.getUnresolvedNames(owner));
            for (String name : names) {
                UUID resolvedUuid = null;
                String finalName = name;

                // 1. オンラインプレイヤーから検索
                ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(name);
                if (onlinePlayer != null) {
                    resolvedUuid = onlinePlayer.getUUID();
                    finalName = onlinePlayer.getScoreboardName();
                } else if (profileCache != null) {
                    // 2. プロファイルキャッシュから検索
                    Optional<GameProfile> profile = profileCache.get(name);
                    if (profile.isPresent()) {
                        resolvedUuid = profile.get().getId();
                        finalName = profile.get().getName();
                    }
                }

                if (resolvedUuid != null) {
                    registry.addToWhitelist(owner, resolvedUuid, finalName);
                    changed = true;
                }
            }
        }

        if (changed) {
            this.setDirty();
        }
    }

    /**
     * 後方互換性用ヘルパー：名前のみの追加（UUIDが即座に解決できない場合は未解決リストへ登録）
     */
    public void addByNameFallback(UUID owner, String name, @Nullable UUID knownUuid) {
        registry.addByNameFallback(owner, name, knownUuid);
        this.setDirty();
    }

    private final Set<String> migratedDimensions = new HashSet<>();

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();

        for (UUID owner : registry.getAllOwners()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("OwnerUUID", owner);

            // 解決済みメンバー (Members)
            Map<UUID, String> members = registry.getMembers(owner);
            if (!members.isEmpty()) {
                ListTag memberList = new ListTag();
                for (Map.Entry<UUID, String> memberEntry : members.entrySet()) {
                    CompoundTag memberTag = new CompoundTag();
                    memberTag.putUUID("UUID", memberEntry.getKey());
                    if (memberEntry.getValue() != null) {
                        memberTag.putString("Name", memberEntry.getValue());
                    }
                    memberList.add(memberTag);
                }
                entryTag.put("Members", memberList);
            }

            // 未解決メンバー (UnresolvedNames)
            Set<String> unresolved = registry.getUnresolvedNames(owner);
            if (!unresolved.isEmpty()) {
                ListTag unresolvedList = new ListTag();
                for (String name : unresolved) {
                    unresolvedList.add(StringTag.valueOf(name));
                }
                entryTag.put("UnresolvedNames", unresolvedList);
            }

            list.add(entryTag);
        }
        tag.put("PlayerWhitelists", list);

        // 移行済みディメンション記録
        ListTag migList = new ListTag();
        for (String dim : migratedDimensions) {
            migList.add(StringTag.valueOf(dim));
        }
        tag.put("MigratedDimensions", migList);

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

                    // 1. 新形式 Members タグの読み込み
                    if (entryTag.contains("Members", Tag.TAG_LIST)) {
                        ListTag membersList = entryTag.getList("Members", Tag.TAG_COMPOUND);
                        for (int j = 0; j < membersList.size(); j++) {
                            CompoundTag mTag = membersList.getCompound(j);
                            if (mTag.hasUUID("UUID")) {
                                UUID mUuid = mTag.getUUID("UUID");
                                String mName = mTag.contains("Name") ? mTag.getString("Name") : mUuid.toString();
                                data.registry.addToWhitelist(owner, mUuid, mName);
                            }
                        }
                    }

                    // 2. 新形式 UnresolvedNames タグの読み込み
                    if (entryTag.contains("UnresolvedNames", Tag.TAG_LIST)) {
                        ListTag unresolvedList = entryTag.getList("UnresolvedNames", Tag.TAG_STRING);
                        for (int j = 0; j < unresolvedList.size(); j++) {
                            data.registry.addByNameFallback(owner, unresolvedList.getString(j), null);
                        }
                    }

                    // 3. 旧形式 Whitelist (文字列リスト) の後方互換読み込み
                    if (entryTag.contains("Whitelist", Tag.TAG_LIST)) {
                        ListTag legacyList = entryTag.getList("Whitelist", Tag.TAG_STRING);
                        for (int j = 0; j < legacyList.size(); j++) {
                            String nameOrUuid = legacyList.getString(j);
                            try {
                                UUID parsedUuid = UUID.fromString(nameOrUuid);
                                data.registry.addToWhitelist(owner, parsedUuid, nameOrUuid);
                            } catch (IllegalArgumentException ignored) {
                                data.registry.addByNameFallback(owner, nameOrUuid, null);
                            }
                        }
                    }
                }
            }
        }

        if (tag.contains("MigratedDimensions", Tag.TAG_LIST)) {
            ListTag migList = tag.getList("MigratedDimensions", Tag.TAG_STRING);
            for (int i = 0; i < migList.size(); i++) {
                data.migratedDimensions.add(migList.getString(i));
            }
        }

        return data;
    }

    /**
     * サーバー共通（オーバーワールド）のPlayerWhitelistSavedDataを取得
     */
    public static PlayerWhitelistSavedData get(ServerLevel level) {
        return get(level.getServer());
    }

    /**
     * MinecraftServerインスタンスから共通のPlayerWhitelistSavedDataを取得
     * 初回取得時に他ディメンション（Nether, End等）の旧SavedDataを走査・自動マージ
     */
    public static PlayerWhitelistSavedData get(MinecraftServer server) {
        PlayerWhitelistSavedData overworldData = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        PlayerWhitelistSavedData::new,
                        PlayerWhitelistSavedData::load,
                        null
                ),
                "player_whitelist"
        );

        overworldData.migrateFromOtherDimensions(server);
        return overworldData;
    }

    private void migrateFromOtherDimensions(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level == server.overworld()) {
                continue;
            }

            String dimKey = level.dimension().location().toString();
            if (migratedDimensions.contains(dimKey)) {
                continue; // 移行済み
            }

            try {
                PlayerWhitelistSavedData otherData = level.getDataStorage().get(
                        new SavedData.Factory<>(
                                PlayerWhitelistSavedData::new,
                                PlayerWhitelistSavedData::load,
                                null
                        ),
                        "player_whitelist"
                );

                if (otherData != null) {
                    boolean merged = false;
                    for (UUID owner : otherData.getRegistry().getAllOwners()) {
                        for (Map.Entry<UUID, String> memberEntry : otherData.getMembers(owner).entrySet()) {
                            this.addToWhitelist(owner, memberEntry.getKey(), memberEntry.getValue());
                            merged = true;
                        }
                        for (String unresolved : otherData.getUnresolvedNames(owner)) {
                            this.addByNameFallback(owner, unresolved, null);
                            merged = true;
                        }
                    }
                    if (merged) {
                        this.setDirty();
                    }
                }
                // 成功したディメンションを記録
                migratedDimensions.add(dimKey);
                this.setDirty();
            } catch (Exception e) {
                System.err.println("[MoveEarth] ディメンション " + dimKey + " からのホワイトリスト移行に失敗しました (次回再試行): " + e.getMessage());
            }
        }
    }
}
