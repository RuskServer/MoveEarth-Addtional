package com.ruskserver.moveearth_addtional.analytics.storage;

import com.ruskserver.moveearth_addtional.analytics.queue.AnalyticsEventQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 非同期イベントキューから定期的にイベントを排出し、ストレージエンジンへバッチ書き込みを行うワーカースレッド
 */
public class AnalyticsStorageWorker implements Runnable {

    private static final int BATCH_SIZE = 200;
    private static final long FLUSH_INTERVAL_MS = 2000L;

    private final AnalyticsStorageEngine storageEngine;
    private final AnalyticsEventQueue queue;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    private long lastFlushTimeMs = 0L;
    private long lastFlushDurationMs = 0L;

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
                if (count > 0) {
                    long startMs = System.currentTimeMillis();
                    storageEngine.writeBatch(batch);
                    lastFlushDurationMs = System.currentTimeMillis() - startMs;
                    lastFlushTimeMs = System.currentTimeMillis();
                    batch.clear();
                } else {
                    Thread.sleep(100);
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
