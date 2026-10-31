package com.ruskserver.moveearth_addtional.compat.paintings;

/**
 * Who may upload an Immersive Paintings image, and how many a day. Pure, so it is tested without a server.
 *
 * <p>Uploads are open to every nation's citizens rather than to staff only, or the mod would hardly be used. What
 * keeps the server's store from filling up is a daily count per player, reset with the open day. Deleting a
 * painting gives nothing back, so uploading and deleting cannot get round it. Staff are not counted.
 */
public final class PaintingUploadPolicy {
    public enum Verdict { ALLOWED, NO_NATION, DAILY_LIMIT }

    private PaintingUploadPolicy() {
    }

    public static Verdict check(boolean staff, boolean inNation, int usedToday, int dailyLimit) {
        if (staff) return Verdict.ALLOWED;
        if (!inNation) return Verdict.NO_NATION;
        return usedToday < dailyLimit ? Verdict.ALLOWED : Verdict.DAILY_LIMIT;
    }

    /** Today's count once the day has turned: a count from another day is spent. */
    public static int usedToday(String countedDay, int count, String today) {
        return today.equals(countedDay) ? count : 0;
    }
}
