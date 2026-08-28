package com.ruskserver.moveearth_addtional.analytics.group;

import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.data.DetectorBlockPositionSavedData;
import com.ruskserver.moveearth_addtional.data.PlayerWhitelistSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * プレイヤー検知ブロックに基づく検知グループの解決サービス。
 * グループ所属判定、拠点範囲判定、自領域/他領域/荒野の立場判定を提供する。
 */
public class DetectorGroupService {

    public static final DetectorGroupService INSTANCE = new DetectorGroupService();

    public DetectorGroupService() {
    }

    /**
     * プレイヤーが指定した検知グループのメンバーであるかを判定（所有者本人またはホワイトリスト登録者）
     */
    public boolean isMember(MinecraftServer server, UUID groupOwnerUuid, UUID playerUuid) {
        if (groupOwnerUuid.equals(playerUuid)) {
            return true;
        }
        PlayerWhitelistSavedData whitelistData = PlayerWhitelistSavedData.get(server);
        return whitelistData.isWhitelisted(groupOwnerUuid, playerUuid);
    }

    /**
     * プレイヤーが所属しているすべての検知グループ（所有者UUID一覧）を取得
     */
    public Set<UUID> getPlayerGroups(MinecraftServer server, UUID playerUuid) {
        Set<UUID> groups = new HashSet<>();
        // 1. 自身が所有者のグループ
        groups.add(playerUuid);

        // 2. 他人のホワイトリストに登録されているグループ
        PlayerWhitelistSavedData whitelistData = PlayerWhitelistSavedData.get(server);
        // 保存されている全オーナーのホワイトリストを調査
        // （小〜中規模サーバー向け走査）
        for (ServerLevel level : server.getAllLevels()) {
            for (BlockPos pos : DetectorBlockPositionSavedData.get(level).getPositions()) {
                if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof PlayerDetectorBlockEntity detector) {
                    UUID owner = detector.getOwnerUUID();
                    if (owner != null && !groups.contains(owner)) {
                        if (whitelistData.isWhitelisted(owner, playerUuid)) {
                            groups.add(owner);
                        }
                    }
                }
            }
        }
        return groups;
    }

    /**
     * 指定したワールド座標において、稼働中の検知ブロックを保持するグループ所有者UUIDを取得
     * （範囲内に複数のグループがある場合は最初に見つかったものを返す）
     */
    @Nullable
    public UUID findCoveringGroupOwner(ServerLevel level, BlockPos pos) {
        Vec3 target = Vec3.atCenterOf(pos);
        long now = System.currentTimeMillis();
        double radiusSqr = AnalyticsConfig.DETECTOR_GROUP_RADIUS_BLOCKS * AnalyticsConfig.DETECTOR_GROUP_RADIUS_BLOCKS;

        for (BlockPos detectorPos : DetectorBlockPositionSavedData.get(level).getPositions()) {
            if (Vec3.atCenterOf(detectorPos).distanceToSqr(target) <= radiusSqr) {
                // チャンクがロードされている場合のみBlockEntityを確認して稼働状態を判定
                if (level.hasChunkAt(detectorPos)
                        && level.getBlockEntity(detectorPos) instanceof PlayerDetectorBlockEntity detector) {
                    if (detector.isOperational(now) && detector.getOwnerUUID() != null) {
                        return detector.getOwnerUUID();
                    }
                }
            }
        }
        return null;
    }

    /**
     * 指定した座標におけるプレイヤーの立場（自領域 MEMBER / 他領域 OUTSIDER / 荒野 WILDERNESS）を判定
     */
    public GroupRelation getGroupRelation(ServerLevel level, BlockPos pos, UUID playerUuid) {
        UUID coveringOwner = findCoveringGroupOwner(level, pos);
        if (coveringOwner == null) {
            return GroupRelation.WILDERNESS;
        }

        if (isMember(level.getServer(), coveringOwner, playerUuid)) {
            return GroupRelation.MEMBER;
        } else {
            return GroupRelation.OUTSIDER;
        }
    }
}
