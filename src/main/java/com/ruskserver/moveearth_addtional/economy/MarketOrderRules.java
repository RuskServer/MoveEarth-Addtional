package com.ruskserver.moveearth_addtional.economy;

/** Server-side limits shared by the order book and the station UI. */
public final class MarketOrderRules {
    public static final int MAX_ORDER_QUANTITY = 4096;
    public static final long MAX_UNIT_PRICE = 1_000_000L;
    public static final int MAX_OPEN_ORDERS_PER_PLAYER = 16;
    public static final int MAX_OUTSTANDING_ITEMS_PER_STATION = 16_384;
    public static final long MAX_LIFETIME_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private MarketOrderRules() { }

    public static long totalPrice(int quantity, long unitPrice) {
        if (quantity < 1 || quantity > MAX_ORDER_QUANTITY
                || unitPrice < 1 || unitPrice > MAX_UNIT_PRICE) return -1L;
        return Math.multiplyExact((long) quantity, unitPrice);
    }

    public static boolean canFill(int requested, int remaining, int available, int capacity) {
        return requested > 0 && requested <= remaining && requested <= available
                && requested <= capacity;
    }

    public static boolean inReach(double distanceSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared <= 64.0D;
    }

    public static boolean stillOpen(long nowMillis, long expiresAtMillis) {
        return expiresAtMillis > nowMillis;
    }
}
