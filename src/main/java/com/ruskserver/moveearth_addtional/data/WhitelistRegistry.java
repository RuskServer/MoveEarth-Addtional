package com.ruskserver.moveearth_addtional.data;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * プレイヤー検知ホワイトリストのメモリ上データ構造と操作ロジックを管理するレジストリ
 */
public class WhitelistRegistry {

    /** オーナーUUID -> (メンバーUUID -> 最終確認表示名) */
    private final Map<UUID, Map<UUID, String>> whitelists = new HashMap<>();

    /** オーナーUUID -> 未解決プレイヤー名セット */
    private final Map<UUID, Set<String>> unresolvedNames = new HashMap<>();

    public WhitelistRegistry() {
    }

    /**
     * 指定したオーナーのホワイトリストに登録されている全メンバーUUIDを取得
     */
    public Set<UUID> getMemberUuids(UUID owner) {
        Map<UUID, String> members = whitelists.get(owner);
        return members != null ? Collections.unmodifiableSet(members.keySet()) : Collections.emptySet();
    }

    /**
     * 指定したオーナーのホワイトリストメンバー（UUID -> 表示名）のマップを取得
     */
    public Map<UUID, String> getMembers(UUID owner) {
        Map<UUID, String> members = whitelists.get(owner);
        return members != null ? Collections.unmodifiableMap(members) : Collections.emptyMap();
    }

    /**
     * 指定したオーナーの未解決プレイヤー名一覧を取得
     */
    public Set<String> getUnresolvedNames(UUID owner) {
        Set<String> unresolved = unresolvedNames.get(owner);
        return unresolved != null ? Collections.unmodifiableSet(unresolved) : Collections.emptySet();
    }

    /**
     * 全オーナーUUIDのセットを取得
     */
    public Set<UUID> getAllOwners() {
        Set<UUID> owners = new HashSet<>(whitelists.keySet());
        owners.addAll(unresolvedNames.keySet());
        return owners;
    }

    /**
     * 表示用：登録済みメンバー名（解決済み表示名＋未解決名）の一覧を取得
     */
    public List<String> getMemberNamesForDisplay(UUID owner) {
        List<String> result = new ArrayList<>();
        Map<UUID, String> members = whitelists.get(owner);
        if (members != null) {
            result.addAll(members.values());
        }
        Set<String> unresolved = unresolvedNames.get(owner);
        if (unresolved != null) {
            result.addAll(unresolved);
        }
        return result;
    }

    /**
     * 指定したプレイヤーUUIDがオーナーのホワイトリストに含まれているかを判定
     */
    public boolean isWhitelisted(UUID owner, UUID playerUuid) {
        Map<UUID, String> members = whitelists.get(owner);
        return members != null && members.containsKey(playerUuid);
    }

    /**
     * メンバーをホワイトリストに追加
     */
    public void addToWhitelist(UUID owner, UUID memberUuid, @Nullable String memberName) {
        String displayName = (memberName != null && !memberName.isBlank()) ? memberName : memberUuid.toString();
        Map<UUID, String> members = whitelists.computeIfAbsent(owner, k -> new HashMap<>());
        members.put(memberUuid, displayName);

        // 未解決リストに同名があれば除去
        if (memberName != null) {
            Set<String> unresolved = unresolvedNames.get(owner);
            if (unresolved != null) {
                unresolved.remove(memberName);
                if (unresolved.isEmpty()) {
                    unresolvedNames.remove(owner);
                }
            }
        }
    }

    /**
     * メンバーをUUID指定でホワイトリストから削除
     */
    public boolean removeFromWhitelist(UUID owner, UUID memberUuid) {
        Map<UUID, String> members = whitelists.get(owner);
        if (members != null && members.remove(memberUuid) != null) {
            if (members.isEmpty()) {
                whitelists.remove(owner);
            }
            return true;
        }
        return false;
    }

    /**
     * 名前指定でホワイトリスト（または未解決リスト）から削除
     */
    public boolean removeFromWhitelistByName(UUID owner, String memberName) {
        boolean changed = false;
        Map<UUID, String> members = whitelists.get(owner);
        if (members != null) {
            UUID targetUuid = null;
            for (Map.Entry<UUID, String> entry : members.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(memberName)) {
                    targetUuid = entry.getKey();
                    break;
                }
            }
            if (targetUuid != null) {
                members.remove(targetUuid);
                if (members.isEmpty()) {
                    whitelists.remove(owner);
                }
                changed = true;
            }
        }

        Set<String> unresolved = unresolvedNames.get(owner);
        if (unresolved != null && unresolved.remove(memberName)) {
            if (unresolved.isEmpty()) {
                unresolvedNames.remove(owner);
            }
            changed = true;
        }

        return changed;
    }

    /**
     * 後方互換性用ヘルパー：名前のみの追加（UUIDが即座に解決できない場合は未解決リストへ登録）
     */
    public void addByNameFallback(UUID owner, String name, @Nullable UUID knownUuid) {
        if (knownUuid != null) {
            addToWhitelist(owner, knownUuid, name);
        } else {
            unresolvedNames.computeIfAbsent(owner, k -> new HashSet<>()).add(name);
        }
    }

    /**
     * レジストリをクリア
     */
    public void clear() {
        whitelists.clear();
        unresolvedNames.clear();
    }
}
