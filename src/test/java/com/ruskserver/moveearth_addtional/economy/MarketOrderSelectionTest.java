package com.ruskserver.moveearth_addtional.economy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketOrderSelectionTest {
    private static MarketOrderSelection.Candidate sell(String item, long price, boolean own) {
        return new MarketOrderSelection.Candidate(UUID.randomUUID(), false, own, item, price, true);
    }

    private static MarketOrderSelection.Candidate buy(String item, long price) {
        return new MarketOrderSelection.Candidate(UUID.randomUUID(), true, false, item, price, true);
    }

    @Test void ownOrdersAreSentEvenBehindManyCheaperListings() {
        List<MarketOrderSelection.Candidate> all = new ArrayList<>();
        for (int index = 0; index < 500; index++) all.add(sell("minecraft:diamond", 1L, false));
        List<MarketOrderSelection.Candidate> own = new ArrayList<>();
        for (int index = 0; index < 16; index++) own.add(sell("minecraft:diamond", 900L + index, true));
        all.addAll(own);
        List<MarketOrderSelection.Candidate> selected = MarketOrderSelection.select(all);
        assertTrue(selected.containsAll(own));
        assertEquals(16 + MarketOrderSelection.PER_SIDE, selected.size());
        assertTrue(selected.size() <= MarketOrderSelection.MAX_TOTAL);
    }

    @Test void ownOrdersIgnoreTheSearch() {
        var own = new MarketOrderSelection.Candidate(UUID.randomUUID(), true, true, "minecraft:iron_ingot", 5L, false);
        var miss = new MarketOrderSelection.Candidate(UUID.randomUUID(), true, false, "minecraft:iron_ingot", 5L, false);
        List<MarketOrderSelection.Candidate> selected = MarketOrderSelection.select(List.of(own, miss));
        assertEquals(List.of(own), selected);
    }

    @Test void oneItemCannotCrowdOutTheOthers() {
        List<MarketOrderSelection.Candidate> all = new ArrayList<>();
        for (int index = 0; index < 200; index++) all.add(sell("minecraft:dirt", 1L, false));
        var diamond = sell("minecraft:diamond", 5_000L, false);
        var emerald = buy("minecraft:emerald", 1L);
        all.add(diamond);
        all.add(emerald);
        List<MarketOrderSelection.Candidate> selected = MarketOrderSelection.select(all);
        assertTrue(selected.contains(diamond));
        assertTrue(selected.contains(emerald));
        assertEquals(MarketOrderSelection.PER_SIDE + 1, selected.size());
    }

    @Test void eachItemsBestOfferComesFirstThenTheNextBest() {
        var dirtCheap = sell("minecraft:dirt", 1L, false);
        var dirtDear = sell("minecraft:dirt", 9L, false);
        var stone = sell("minecraft:stone", 5L, false);
        List<MarketOrderSelection.Candidate> selected = MarketOrderSelection.select(List.of(dirtDear, stone, dirtCheap));
        assertEquals(List.of(dirtCheap, stone, dirtDear), selected);
        var highBid = buy("minecraft:dirt", 9L);
        var lowBid = buy("minecraft:dirt", 1L);
        assertEquals(List.of(highBid, lowBid), MarketOrderSelection.select(List.of(lowBid, highBid)));
    }

    @Test void aSearchForOneItemFillsTheSideWithIt() {
        List<MarketOrderSelection.Candidate> all = new ArrayList<>();
        for (int index = 0; index < 100; index++) all.add(sell("minecraft:diamond", 10L + index, false));
        for (int index = 0; index < 100; index++)
            all.add(new MarketOrderSelection.Candidate(UUID.randomUUID(), false, false, "minecraft:dirt", 1L, false));
        List<MarketOrderSelection.Candidate> selected = MarketOrderSelection.select(all);
        assertEquals(MarketOrderSelection.PER_SIDE, selected.size());
        assertTrue(selected.stream().allMatch(candidate -> candidate.itemKey().equals("minecraft:diamond")));
        assertEquals(10L, selected.getFirst().unitPrice());
    }

    @Test void searchMatchesTextOrClientResolvedKeys() {
        MarketSearch search = MarketSearch.of("  ダイヤ ", List.of("minecraft:diamond", "gun:tacz:ak47"));
        assertEquals("ダイヤ", search.text());
        assertTrue(search.matches("minecraft:diamond", "minecraft:diamond", null, "Diamond"));
        assertTrue(search.matches("gun:tacz:ak47", "tacz:modern_kinetic_gun", "tacz:ak47", "Gun"));
        assertFalse(search.matches("minecraft:dirt", "minecraft:dirt", null, "Dirt"));
        MarketSearch english = MarketSearch.of("DIA", null);
        assertTrue(english.matches("minecraft:diamond", "minecraft:diamond", null, "Diamond"));
        assertTrue(english.matches("x", null, null, "My Diamond Sword"));
        assertTrue(MarketSearch.NONE.matches("minecraft:dirt"));
        assertTrue(MarketSearch.of("   ", List.of("minecraft:diamond")).itemKeys().isEmpty());
    }

    @Test void searchIsBounded() {
        MarketSearch search = MarketSearch.of("x".repeat(500), java.util.stream.IntStream.range(0, 500)
                .mapToObj(index -> "minecraft:item_" + index).toList());
        assertEquals(MarketSearch.MAX_TEXT_LENGTH, search.text().length());
        assertEquals(MarketSearch.MAX_ITEM_KEYS, search.itemKeys().size());
        assertTrue(MarketSearch.of("a", List.of("k".repeat(MarketSearch.MAX_ITEM_KEY_LENGTH + 1))).itemKeys().isEmpty());
        assertEquals(Set.of(), MarketSearch.NONE.itemKeys());
        assertEquals("gun:tacz:ak47", MarketSearch.itemKey("tacz:modern_kinetic_gun", "tacz:ak47"));
        assertEquals("minecraft:dirt", MarketSearch.itemKey("minecraft:dirt", null));
    }
}
