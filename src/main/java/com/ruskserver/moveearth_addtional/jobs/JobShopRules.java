package com.ruskserver.moveearth_addtional.jobs;

/** Pure validation helpers for shop purchases and configuration. */
public final class JobShopRules {
    public static final int MAX_PRICE = 1_000_000;
    public static final int MAX_PURCHASE_LIMIT = 1_000_000;

    private JobShopRules() {
    }

    public static boolean validConfiguration(int price, int purchaseLimit) {
        return price >= 1 && price <= MAX_PRICE
                && purchaseLimit >= 0 && purchaseLimit <= MAX_PURCHASE_LIMIT;
    }

    public static PurchaseCheck checkPurchase(int points, int price, int purchased, int purchaseLimit,
                                              boolean enabled) {
        if (!enabled) return PurchaseCheck.DISABLED;
        if (price <= 0) return PurchaseCheck.INVALID_PRODUCT;
        if (purchaseLimit > 0 && purchased >= purchaseLimit) return PurchaseCheck.LIMIT_REACHED;
        if (points < price) return PurchaseCheck.NOT_ENOUGH_POINTS;
        return PurchaseCheck.ALLOWED;
    }

    public static int remainingPurchases(int purchased, int purchaseLimit) {
        return purchaseLimit <= 0 ? -1 : Math.max(0, purchaseLimit - Math.max(0, purchased));
    }

    public enum PurchaseCheck {
        ALLOWED,
        DISABLED,
        INVALID_PRODUCT,
        LIMIT_REACHED,
        NOT_ENOUGH_POINTS
    }
}
