package com.ruskserver.moveearth_addtional.s2.tip;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Minecraft-independent per-player tip state and transition rules. */
public final class TipProgress {
    private boolean enabled;
    private int remainingSeconds;
    private final Set<String> seen = new LinkedHashSet<>();
    private final Deque<String> history = new ArrayDeque<>();

    TipProgress(boolean enabled, int remainingSeconds) {
        this.enabled = enabled;
        this.remainingSeconds = Math.max(0, remainingSeconds);
    }

    void advanceSecond() {
        if (enabled && remainingSeconds > 0) remainingSeconds--;
    }

    void setEnabled(boolean enabled, int initialDelaySeconds) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) remainingSeconds = Math.max(0, initialDelaySeconds);
    }

    void markShown(String tipId, int intervalSeconds, int historySize) {
        seen.add(tipId);
        history.addLast(tipId);
        while (history.size() > Math.max(1, historySize)) history.removeFirst();
        remainingSeconds = Math.max(1, intervalSeconds);
    }

    void resetSeen() { seen.clear(); }
    void restoreSeen(String id) { seen.add(id); }
    void restoreHistory(String id) { history.addLast(id); }

    public boolean enabled() { return enabled; }
    public int remainingSeconds() { return remainingSeconds; }
    public Set<String> seen() { return Set.copyOf(seen); }
    public List<String> history() { return List.copyOf(history); }
    public String previous() { return history.peekLast(); }
    public boolean hasSeenAll(List<String> ids) { return seen.containsAll(ids); }
}
