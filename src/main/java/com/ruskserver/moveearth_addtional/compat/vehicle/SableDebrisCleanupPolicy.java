package com.ruskserver.moveearth_addtional.compat.vehicle;

public final class SableDebrisCleanupPolicy {
    private SableDebrisCleanupPolicy() { }
    public enum Action { KEEP, WARN, WAIT, REMOVE }
    public record Decision(Action action, long pendingSince) { }

    public static Decision review(boolean protectedBody, int nearby, int max, long now,
                                  Long pendingSince, long graceTicks) {
        if (protectedBody || nearby <= max) return new Decision(Action.KEEP, -1);
        if (pendingSince == null || now < pendingSince) return new Decision(Action.WARN, now);
        if (now - pendingSince < graceTicks) return new Decision(Action.WAIT, pendingSince);
        return new Decision(Action.REMOVE, pendingSince);
    }
}
