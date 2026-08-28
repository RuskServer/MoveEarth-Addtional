package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.query.format.AnalyticsFormatUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyticsTextFormatterTest {

    @Test
    public void testFormatDuration() {
        assertEquals("45秒", AnalyticsFormatUtil.formatDuration(45L));
        assertEquals("15m", AnalyticsFormatUtil.formatDuration(900L));
        assertEquals("2h 15m", AnalyticsFormatUtil.formatDuration(8100L));
    }

    @Test
    public void testFormatDistance() {
        assertEquals("450m", AnalyticsFormatUtil.formatDistance(450.0));
        assertEquals("1.5km", AnalyticsFormatUtil.formatDistance(1500.0));
    }

    @Test
    public void testFormatPercent() {
        assertEquals("0%", AnalyticsFormatUtil.formatPercent(0, 0));
        assertEquals("50.0%", AnalyticsFormatUtil.formatPercent(50, 100));
        assertEquals("75.0%", AnalyticsFormatUtil.formatPercent(3, 4));
    }

    @Test
    public void testFormatNumberAndDecimal() {
        assertEquals("1,234,567", AnalyticsFormatUtil.formatNumber(1234567L));
        assertEquals("123.5", AnalyticsFormatUtil.formatDecimal(123.456));
    }
}
