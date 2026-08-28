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

    /** イベント優先度定義 */
    public enum EventPriority {
        HIGH,
        NORMAL,
        LOW
    }

    /** イベント基底インターフェース */
    public interface AnalyticsEvent {
        default EventPriority getPriority() {
            return isHighPriority() ? EventPriority.HIGH : EventPriority.NORMAL;
        }

        default boolean isHighPriority() {
            return getPriority() == EventPriority.HIGH;
        }
    }

    /** プレイヤーログイン（セッション開始）イベント */
    public record SessionStartEvent(
            UUID sessionId,
            UUID playerUuid,
            String playerName,
            long joinedAtEpochSec
    ) implements AnalyticsEvent {
        @Override
        public EventPriority getPriority() {
            return EventPriority.HIGH;
        }
    }

    /** プレイヤーログアウト（セッション終了）イベント */
    public record SessionEndEvent(
            SessionRecord sessionRecord
    ) implements AnalyticsEvent {
        @Override
        public EventPriority getPriority() {
            return EventPriority.HIGH;
        }
    }

    /** プレイヤー活動5分バケットフラッシュイベント */
    public record PlayerActivityFlushEvent(
            List<PlayerActivityBucket> records
    ) implements AnalyticsEvent {
        @Override
        public EventPriority getPriority() {
            return EventPriority.NORMAL;
        }
    }

    /** 空間活動5分バケットフラッシュイベント */
    public record SpatialActivityFlushEvent(
            List<SpatialActivityBucket> records
    ) implements AnalyticsEvent {
        @Override
        public EventPriority getPriority() {
            return EventPriority.LOW;
        }
    }

    /** 検知ブロック活動5分バケットフラッシュイベント */
    public record DetectorActivityFlushEvent(
            List<DetectorActivityBucket> records
    ) implements AnalyticsEvent {
        @Override
        public EventPriority getPriority() {
            return EventPriority.NORMAL;
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
        public EventPriority getPriority() {
            return EventPriority.HIGH;
        }
    }

    private final BlockingQueue<AnalyticsEvent> queue;
    private final int capacity;
    private final AtomicLong droppedEvents = new AtomicLong(0);

    public AnalyticsEventQueue() {
        this(AnalyticsConfig.MAX_QUEUE_CAPACITY);
    }

    public AnalyticsEventQueue(int capacity) {
        this.capacity = capacity;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * イベントをキューへ投入（サーバースレッドをブロックしない非ブロッキング処理）
     * 負荷逼迫時（容量の90%以上）はLOW優先度の空間イベントを能動的に破棄して重要イベントを保護
     * キュー満杯時にもHIGH優先度イベント（セッション開始・終了等）は既存要素を追い出して確実に格納
     *
     * @return 正常にキューへ追加された場合は true、破棄された場合は false
     */
    public boolean enqueue(AnalyticsEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        // キュー使用率が90%を超えている場合、LOW優先度（空間サンプル）を破棄してセッション/プレイヤー活動を保護
        if (event.getPriority() == EventPriority.LOW && queue.size() >= (capacity * 0.9)) {
            droppedEvents.incrementAndGet();
            return false;
        }

        boolean offered = queue.offer(event);
        if (!offered) {
            // キュー満杯時: HIGH優先度イベントなら既存の要素を1件追い出してスロットを確保
            if (event.getPriority() == EventPriority.HIGH) {
                AnalyticsEvent evicted = queue.poll();
                if (evicted != null) {
                    droppedEvents.incrementAndGet(); // 追い出された要素をドロップカウント
                    offered = queue.offer(event);
                }
            }

            if (!offered) {
                // それでも入らなかった場合、またはLOW/NORMALイベントは破棄
                droppedEvents.incrementAndGet();
            }
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
