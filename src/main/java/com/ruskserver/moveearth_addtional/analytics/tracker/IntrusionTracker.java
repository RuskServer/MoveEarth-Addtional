package com.ruskserver.moveearth_addtional.analytics.tracker;

import com.ruskserver.moveearth_addtional.analytics.model.DetectorActivityBucket;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤー検知ブロックにおける侵入セッションおよび滞在時間を追跡・集計するトラッカー
 */
public class IntrusionTracker {

    public static final IntrusionTracker INSTANCE = new IntrusionTracker();

    /** 検知ブロックごとの集計状態 */
    private static class DetectorState {
        final String dimension;
        final String detectorPosHash;
        @Nullable final UUID groupOwnerUuid;

        // 進行中の侵入者UUIDセット (退域検知用)
        final Set<UUID> activeIntruders = new HashSet<>();

        // 5分バケット内の累計値
        double memberMinutes = 0.0;
        double visitorMinutes = 0.0;
        int intrusionSessionCount = 0;
        final Set<UUID> distinctMembers = new HashSet<>();
        final Set<UUID> distinctVisitors = new HashSet<>();

        long lastScanTimeMs = 0L;

        DetectorState(String dimension, String detectorPosHash, @Nullable UUID groupOwnerUuid) {
            this.dimension = dimension;
            this.detectorPosHash = detectorPosHash;
            this.groupOwnerUuid = groupOwnerUuid;
        }
    }

    private final Map<String, DetectorState> detectorStates = new ConcurrentHashMap<>();

    public IntrusionTracker() {
    }

    /**
     * 検知ブロックの5秒周期スキャン結果を記録
     *
     * @param dimension ディメンション識別子
     * @param detectorPosHash 検知ブロックの座標ハッシュ
     * @param groupOwnerUuid グループ所有者UUID
     * @param currentMembers 現在検知範囲内にいるグループメンバーUUID一覧
     * @param currentVisitors 現在検知範囲内にいる友好的訪問者UUID一覧
     * @param currentIntruders 現在検知範囲内にいる侵入者UUID一覧
     * @param currentTimeMs 現在時刻 (ミリ秒)
     */
    public synchronized void recordScan(
            String dimension,
            String detectorPosHash,
            @Nullable UUID groupOwnerUuid,
            Set<UUID> currentMembers,
            Set<UUID> currentVisitors,
            Set<UUID> currentIntruders,
            long currentTimeMs
    ) {
        DetectorState state = detectorStates.computeIfAbsent(detectorPosHash,
                k -> new DetectorState(dimension, detectorPosHash, groupOwnerUuid));

        double elapsedMinutes = 0.0;
        if (state.lastScanTimeMs > 0L) {
            long deltaMs = currentTimeMs - state.lastScanTimeMs;
            if (deltaMs > 0 && deltaMs <= 15000L) { // 異常に長いギャップでなければスキャン間隔を加算
                elapsedMinutes = deltaMs / 60000.0D;
            } else {
                elapsedMinutes = 5000.0D / 60000.0D; // デフォルト5秒
            }
        } else {
            elapsedMinutes = 5000.0D / 60000.0D;
        }
        state.lastScanTimeMs = currentTimeMs;

        // メンバー滞在時間とユニーク数加算
        if (!currentMembers.isEmpty()) {
            state.memberMinutes += elapsedMinutes * currentMembers.size();
            state.distinctMembers.addAll(currentMembers);
        }

        // 友好的訪問者滞在時間とユニーク数加算
        if (!currentVisitors.isEmpty()) {
            state.visitorMinutes += elapsedMinutes * currentVisitors.size();
            state.distinctVisitors.addAll(currentVisitors);
        }

        // 侵入者のセッション判定（入域〜退域）
        for (UUID intruder : currentIntruders) {
            if (!state.activeIntruders.contains(intruder)) {
                // 新たに入域した侵入者 -> 1セッション開始
                state.activeIntruders.add(intruder);
                state.intrusionSessionCount++;
            }
            state.distinctVisitors.add(intruder);
            state.visitorMinutes += elapsedMinutes;
        }

        // 範囲外へ出た侵入者をアクティブセットから削除
        state.activeIntruders.retainAll(currentIntruders);
    }

    /**
     * 5分バケットの集計レコードをフラッシュし、バケット累計をリセット
     */
    public synchronized List<DetectorActivityBucket> flushBucket(long bucketAtEpochSec) {
        List<DetectorActivityBucket> buckets = new ArrayList<>();

        for (DetectorState state : detectorStates.values()) {
            if (state.memberMinutes > 0 || state.visitorMinutes > 0 || state.intrusionSessionCount > 0
                    || !state.distinctMembers.isEmpty() || !state.distinctVisitors.isEmpty()) {
                buckets.add(new DetectorActivityBucket(
                        bucketAtEpochSec,
                        state.dimension,
                        state.detectorPosHash,
                        state.groupOwnerUuid,
                        state.memberMinutes,
                        state.visitorMinutes,
                        state.intrusionSessionCount,
                        state.distinctMembers.size(),
                        state.distinctVisitors.size()
                ));
            }
            // バケット累計値をリセット（アクティブ侵入者セットとlastScanTimeは保持）
            state.memberMinutes = 0.0;
            state.visitorMinutes = 0.0;
            state.intrusionSessionCount = 0;
            state.distinctMembers.clear();
            state.distinctVisitors.clear();
        }

        return buckets;
    }

    public synchronized void clear() {
        detectorStates.clear();
    }
}
