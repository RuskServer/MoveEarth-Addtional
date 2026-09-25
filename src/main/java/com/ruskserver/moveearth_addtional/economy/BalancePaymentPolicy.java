package com.ruskserver.moveearth_addtional.economy;

import java.util.UUID;

/** Transport-independent limits for a direct player payment. */
public final class BalancePaymentPolicy {
    public static final long MAX_PAY = 1_000_000L;

    private BalancePaymentPolicy() { }

    public static boolean validRequest(UUID sender, UUID recipient, long amount) {
        return sender != null && recipient != null && !sender.equals(recipient)
                && amount >= 1L && amount <= MAX_PAY;
    }
}
