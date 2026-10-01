package com.ruskserver.moveearth_addtional.s2.territory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguringReservationTest {
    @Test
    void aReservationLastsOneHourOfOpenTime() {
        long start = 1_000L;
        assertTrue(ConfiguringReservationPolicy.live(start, start));
        assertTrue(ConfiguringReservationPolicy.live(start, start + ConfiguringReservationPolicy.LIMIT_OPEN_TICKS - 1));
        assertFalse(ConfiguringReservationPolicy.live(start, start + ConfiguringReservationPolicy.LIMIT_OPEN_TICKS));
    }

    private static final UUID NATION = UUID.randomUUID();
    private static final String OVERWORLD = "minecraft:overworld";

    private static ConfiguringReservationPolicy.Claim claim(UUID nation, String dimension, int x, int z, int radius,
                                                            long since) {
        return new ConfiguringReservationPolicy.Claim(nation, dimension, new TerritoryPreviewArea(x, z, radius), since);
    }

    @Test
    void aReplacedOutpostTakesOverTheEarlierStartOnItsLand() {
        long now = 5 * ConfiguringReservationPolicy.LIMIT_OPEN_TICKS;
        var lapsed = claim(NATION, OVERWORLD, 0, 0, 4, 1_000L);
        // Same spot, or shifted but still overlapping: the hour does not restart.
        long start = ConfiguringReservationPolicy.start(NATION, OVERWORLD, new TerritoryPreviewArea(0, 0, 4), now,
                List.of(lapsed));
        assertEquals(1_000L, start);
        assertFalse(ConfiguringReservationPolicy.live(start, now));
        assertEquals(1_000L, ConfiguringReservationPolicy.start(NATION, OVERWORLD, new TerritoryPreviewArea(6, 6, 2),
                now, List.of(lapsed)));
    }

    @Test
    void otherLandNationsAndDimensionsStartFresh() {
        long now = 50_000L;
        List<ConfiguringReservationPolicy.Claim> claims = List.of(
                claim(NATION, OVERWORLD, 0, 0, 4, 1_000L),
                claim(UUID.randomUUID(), OVERWORLD, 20, 0, 4, 2_000L),
                claim(NATION, "minecraft:the_nether", 20, 0, 4, 3_000L));
        assertEquals(now, ConfiguringReservationPolicy.start(NATION, OVERWORLD, new TerritoryPreviewArea(20, 0, 4),
                now, claims));
        assertEquals(now, ConfiguringReservationPolicy.start(NATION, OVERWORLD, new TerritoryPreviewArea(9, 0, 0),
                now, claims));
    }

    @Test
    void theEarliestOverlappingStartWinsAndNeverLater() {
        List<ConfiguringReservationPolicy.Claim> claims = List.of(
                claim(NATION, OVERWORLD, 0, 0, 1, 4_000L),
                claim(NATION, OVERWORLD, 2, 0, 1, 2_000L));
        assertEquals(2_000L, ConfiguringReservationPolicy.start(NATION, OVERWORLD, new TerritoryPreviewArea(1, 0, 0),
                9_000L, claims));
        assertEquals(500L, ConfiguringReservationPolicy.start(NATION, OVERWORLD, new TerritoryPreviewArea(1, 0, 0),
                500L, claims));
    }

    @Test
    void removedOutpostLandStaysTiedForTheReuseCooldown() {
        long removedAt = 7_000L;
        assertTrue(ConfiguringReservationPolicy.retained(removedAt, removedAt));
        assertTrue(ConfiguringReservationPolicy.retained(removedAt,
                removedAt + ConfiguringReservationPolicy.REUSE_COOLDOWN_OPEN_TICKS - 1));
        assertFalse(ConfiguringReservationPolicy.retained(removedAt,
                removedAt + ConfiguringReservationPolicy.REUSE_COOLDOWN_OPEN_TICKS));
    }
}
