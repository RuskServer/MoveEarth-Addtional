package com.ruskserver.moveearth_addtional.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NationTreasuryHelperTest {

    @Test
    void testFormatCurrencyAndNumber() {
        assertEquals("1,000 TC", NationTreasuryHelper.formatCurrency(1000L));
        assertEquals("1,234,567 TC", NationTreasuryHelper.formatCurrency(1234567L));
        assertEquals("0 TC", NationTreasuryHelper.formatCurrency(0L));
        assertEquals("1,000", NationTreasuryHelper.formatNumber(1000L));
    }

    @Test
    void testCalculateCoverageHours() {
        assertEquals(240L, NationTreasuryHelper.calculateCoverageHours(100_000L, 10_000L, 24));
        assertEquals(480L, NationTreasuryHelper.calculateCoverageHours(100_000L, 10_000L, 48));
        assertEquals(12L, NationTreasuryHelper.calculateCoverageHours(10_000L, 10_000L, 12));
        assertEquals(0L, NationTreasuryHelper.calculateCoverageHours(5_000L, 10_000L, 24));
        assertEquals(0L, NationTreasuryHelper.calculateCoverageHours(0L, 10_000L, 24));
        assertEquals(0L, NationTreasuryHelper.calculateCoverageHours(100_000L, 0L, 24));
        assertEquals(0L, NationTreasuryHelper.calculateCoverageHours(-500L, 10_000L, 24));
        assertEquals(Long.MAX_VALUE,
                NationTreasuryHelper.calculateCoverageHours(Long.MAX_VALUE, 1L, 720));
    }

    @Test
    void testParseAmount() {
        assertEquals(500L, NationTreasuryHelper.parseAmount("500"));
        assertEquals(1500L, NationTreasuryHelper.parseAmount("1500"));
        assertEquals(0L, NationTreasuryHelper.parseAmount(""));
        assertEquals(0L, NationTreasuryHelper.parseAmount("abc"));
        assertEquals(123456L, NationTreasuryHelper.parseAmount("123abc456"));
        assertEquals(Long.MAX_VALUE, NationTreasuryHelper.parseAmount("99999999999999999999999"));
    }

    @Test
    void testApplyIncrement() {
        assertEquals(1500L, NationTreasuryHelper.applyIncrement(500L, 1000L, 5000L));
        assertEquals(5000L, NationTreasuryHelper.applyIncrement(4500L, 1000L, 5000L)); // clamped to max
        assertEquals(0L, NationTreasuryHelper.applyIncrement(500L, -1000L, 5000L)); // clamped to 0
    }

    @Test
    void testParseTransaction() {
        var tx1 = NationTreasuryHelper.ParsedTransaction.fromRaw("+1000  treasury_deposit");
        assertTrue(tx1.incoming());
        assertEquals(1000L, tx1.amount());
        assertEquals("treasury_deposit", tx1.reason());

        var tx2 = NationTreasuryHelper.ParsedTransaction.fromRaw("-5000  upkeep_charge");
        assertFalse(tx2.incoming());
        assertEquals(5000L, tx2.amount());
        assertEquals("upkeep_charge", tx2.reason());

        var tx3 = NationTreasuryHelper.ParsedTransaction.fromRaw("");
        assertTrue(tx3.incoming());
        assertEquals(0L, tx3.amount());
        assertEquals("", tx3.reason());

        var tx4 = NationTreasuryHelper.ParsedTransaction.fromRaw(null);
        assertTrue(tx4.incoming());
        assertEquals(0L, tx4.amount());
        assertEquals("", tx4.reason());

        var tx5 = NationTreasuryHelper.ParsedTransaction.fromRaw("7500 peace_compensation");
        assertTrue(tx5.incoming());
        assertEquals(7500L, tx5.amount());
        assertEquals("peace_compensation", tx5.reason());
    }

    @Test
    void testApplyIncrementOverflowAndZeroCap() {
        assertEquals(0L, NationTreasuryHelper.applyIncrement(0L, 1000L, 0L));
        assertEquals(10_000L, NationTreasuryHelper.applyIncrement(Long.MAX_VALUE - 100L, 500L, 10_000L));
    }
}
