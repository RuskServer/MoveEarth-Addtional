package com.ruskserver.moveearth_addtional.analytics.queue;

import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.model.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * サーバースレッドから永続化ワーカースレッドへイベントを非同期に受け渡す上限付きキュー
 */
public class AnalyticsEventQueue {

    public static final AnalyticsEventQueue INSTANCE = new AnalyticsEventQueue();

    /** イベント基底インターフェース */
    public interface AnalyticsEvent {
        boolean isHighPriority();
    }

    /** プレイヤーログイン（セッション開始）イベント */
    public record SessionStartEvent(
            UUID sessionId,
            UUID playerUuid,
            String playerName,
            long joinedAtEpochSec
    ) implements AnalyticsEvent {
        @Override
        public boolean isHighPriority() {
            return true;
        }
    }

    /** プレイヤーログアウト（セッション終了）イベント */
    public record SessionEndEvent(
            SessionRecord sessionRecord
    ) implements AnalyticsEvent {
        @Override
        public boolean isHighPriority() {
            return true;
        }
    }

    /** プレイヤー活動5分バケットフラッシュイベント */
    public record PlayerActivityFlushEvent(
            List<PlayerActivityBucket> records
    ) implements AnalyticsEvent {
        @Override
        public boolean isHighPriority() {
            return false;
        }
    }

    /** 空間活動5分バケットフラッシュイベント */
    public record SpatialActivityFlushEvent(
            List<SpatialActivityBucket> records
    ) implements AnalyticsEvent {
        @Override
        public boolean isHighPriority() {
            return false;
        }
    }

    /** 検知ブロック活動5分バケットフラッシュイベント */
    public record DetectorActivityFlushEvent(
            List<DetectorActivityBucket> records
    ) implements AnalyticsEvent {
        @Override
        public boolean isHighPriority() {
            return false;
        }
    }

    /** コレクターヘルス指標イベント */
    public record HealthMetricEvent(
            long recordedAtEpochSec,
            int queueDepth,
            long droppedEvents,
            long flushMs,
            long dbBytes
    ) implements AnalyticsEvent {
        @Override
        public boolean isHighPriority() {
            return true;
        }
    }

    private final BlockingQueue<AnalyticsEvent> queue;
    private final AtomicLong droppedEvents = new AtomicLong(0);

    public AnalyticsEventQueue() {
        this(AnalyticsConfig.MAX_QUEUE_CAPACITY);
    }

    public AnalyticsEventQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * イベントをキューへ投入（サーバースレッドをブロックしない非ブロッキング処理）
     *
     * @return 正常にキューへ追加された場合は true、キュー満杯で破棄された場合は false
     */
    public boolean enqueue(AnalyticsEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        boolean offered = queue.offer(event);
        if (!offered) {
            // キューが満杯の場合は破棄し、欠損件数を記録
            droppedEvents.incrementAndGet();
        }
        return offered;
    }

    /**
     * キューから1件取得（ノンブロッキング）
     */
    public AnalyticsEvent poll() {
        return queue.poll();
    }

    /**
     * キューから指定件数をまとめて取り出す
     */
    public int drainTo(List<AnalyticsEvent> buffer, int maxElements) {
        return queue.drainTo(buffer, maxElements);
    }

    /**
     * 現在のキュー長
     */
    public int size() {
        return queue.size();
    }

    /**
     * 破棄されたイベント累計件数
     */
    public long getDroppedEventsCount() {
        return droppedEvents.get();
    }

    /**
     * キューとカウンタをリセット（テスト用）
     */
    public void clear() {
        queue.clear();
        droppedEvents.set(0);
    }
}
