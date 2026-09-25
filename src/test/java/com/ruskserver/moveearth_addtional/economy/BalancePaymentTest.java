package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalancePaymentTest {
    @Test void rejectsSelfAndOutOfRangePayments() {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        assertFalse(BalancePaymentPolicy.validRequest(sender, sender, 10));
        assertFalse(BalancePaymentPolicy.validRequest(sender, recipient, 0));
        assertFalse(BalancePaymentPolicy.validRequest(sender, recipient, BalancePaymentPolicy.MAX_PAY + 1));
        assertTrue(BalancePaymentPolicy.validRequest(sender, recipient, BalancePaymentPolicy.MAX_PAY));
    }
}
