package com.ruskserver.moveearth_addtional.analytics.storage;

import com.ruskserver.moveearth_addtional.analytics.config.AnalyticsConfig;
import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 非同期イベントキューから定期的にイベントを排出し、ストレージエンジンへバッチ書き込みおよび
 * 定期ヘルス計測・日次集約メンテをバックグラウンドで実行するワーカースレッド
 */
public class AnalyticsStorageWorker implements Runnable {

    private static final int BATCH_SIZE = 200;
    private static final long HEALTH_METRIC_INTERVAL_MS = 60_000L; // 60秒
    private static final long MAINTENANCE_INTERVAL_MS = 3600_000L; // 1時間

    private final AnalyticsStorageEngine storageEngine;
    private final AnalyticsEventQueue queue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    private long lastFlushTimeMs = 0L;
    private long lastFlushDurationMs = 0L;
    private long lastHealthRecordMs = 0L;
    private long lastMaintenanceMs = 0L;

    public AnalyticsStorageWorker(AnalyticsStorageEngine storageEngine, AnalyticsEventQueue queue) {
        this.storageEngine = storageEngine;
        this.queue = queue;
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, "MoveEarth-Analytics-Storage-Worker");
            thread.setDaemon(true);
            thread.start();
        }
    }

    @Override
    public void run() {
        List<AnalyticsEventQueue.AnalyticsEvent> batch = new ArrayList<>(BATCH_SIZE);

        while (running.get()) {
            try {
                int count = queue.drainTo(batch, BATCH_SIZE);
                long now = System.currentTimeMillis();

                if (count > 0) {
                    long startMs = System.currentTimeMillis();
                    boolean written = false;
                    for (int attempt = 1; attempt <= 3; attempt++) {
                        try {
                            storageEngine.writeBatch(batch);
                            lastFlushDurationMs = System.currentTimeMillis() - startMs;
                            lastFlushTimeMs = System.currentTimeMillis();
                            written = true;
                            break;
                        } catch (Exception e) {
                            if (attempt < 3) {
                                try {
                                    Thread.sleep(attempt * 50L);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            } else {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (!written) {
                        // 3回リトライしても失敗した場合はドロップ数に計上
                        queue.recordDropped(batch.size());
                    }
                    batch.clear();
                } else {
                    Thread.sleep(100);
                }

                // 1. 定期ヘルス指標の自動計測・永続化 (60秒毎)
                if (now - lastHealthRecordMs >= HEALTH_METRIC_INTERVAL_MS) {
                    recordHealthMetric(now);
                    lastHealthRecordMs = now;
                }

                // 2. 定期日次集約・パージの非同期メンテナンス (1時間毎)
                if (now - lastMaintenanceMs >= MAINTENANCE_INTERVAL_MS) {
                    performAsyncMaintenance(now / 1000L);
                    lastMaintenanceMs = now;
                }

            } catch (InterruptedException e) {
                // 停止要求
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 停止時の最終残留フラッシュ（batchおよびqueue）
        try {
            if (!batch.isEmpty()) {
                storageEngine.writeBatch(batch);
                batch.clear();
            }
            flushRemaining();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void recordHealthMetric(long nowMs) {
        if (!storageEngine.isOpen()) return;
        try {
            AnalyticsEventQueue.HealthMetricEvent healthEvent = new AnalyticsEventQueue.HealthMetricEvent(
                    nowMs / 1000L,
                    queue.size(),
                    queue.getDroppedEventsCount(),
                    lastFlushDurationMs,
                    storageEngine.getDatabaseSizeBytes()
            );
            storageEngine.writeBatch(List.of(healthEvent));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void performAsyncMaintenance(long nowSec) {
        if (!storageEngine.isOpen()) return;
        try {
            long cutoffDailySec = nowSec - (AnalyticsConfig.RETENTION_DAILY_DAYS * 86400L);
            long cutoff5mSec = nowSec - (AnalyticsConfig.RETENTION_5M_DAYS * 86400L);
            long cutoffSessionSec = nowSec - (AnalyticsConfig.RETENTION_SESSION_DAYS * 86400L);

            // 日次集約
            storageEngine.aggregateDaily(nowSec);

            // パージ
            storageEngine.purgeOldRecords(cutoff5mSec, cutoffDailySec, cutoffSessionSec);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void flushRemaining() {
        List<AnalyticsEventQueue.AnalyticsEvent> remaining = new ArrayList<>();
        queue.drainTo(remaining, Integer.MAX_VALUE);
        if (!remaining.isEmpty() && storageEngine.isOpen()) {
            try {
                storageEngine.writeBatch(remaining);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * ワーカースレッドを停止し、残存イベントをすべてフラッシュしてDBを閉じる
     */
    public synchronized void stopAndFlush(long timeoutMs) {
        if (running.compareAndSet(true, false)) {
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join(timeoutMs);
                } catch (InterruptedException ignored) {
                }
            }
        }

        flushRemaining();

        try {
            storageEngine.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public long getLastFlushDurationMs() {
        return lastFlushDurationMs;
    }

    public boolean isRunning() {
        return running.get();
    }
}
