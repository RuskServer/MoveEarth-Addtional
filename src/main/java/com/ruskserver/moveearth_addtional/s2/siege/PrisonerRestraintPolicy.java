package com.ruskserver.moveearth_addtional.s2.siege;

/** The downed grace period delays completion, but never rejects an otherwise valid attempt. */
public final class PrisonerRestraintPolicy {
    private PrisonerRestraintPolicy() { }

    public static int protectionRemaining(long protectionTicks, int downedTicks) {
        return downedTicks < 0 ? 0 : (int) Math.max(0L, protectionTicks - downedTicks);
    }

    public static boolean canComplete(int elapsedTicks, int restraintTicks, int protectionRemaining) {
        return elapsedTicks >= restraintTicks && protectionRemaining == 0;
    }
}
