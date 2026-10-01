package com.ruskserver.moveearth_addtional.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which orders one market snapshot carries. The viewer's own orders always go out, whatever the search
 * and however many cheaper listings exist, so they can always be found and cancelled. Of everyone
 * else's orders that match the search, each side carries at most {@link #PER_SIDE}, taken round-robin
 * across items: every item's best offer first, then every item's second best, and so on. One item with
 * many cheap listings therefore cannot crowd every other item out, and a search for one item still
 * fills the whole side with that item's offers.
 */
public final class MarketOrderSelection {
    public static final int PER_SIDE = 48;
    /** The order cap per player is {@link MarketOrderRules#MAX_OPEN_ORDERS_PER_PLAYER}; this only bounds the packet. */
    public static final int MAX_OWN = 32;
    /** Most orders one snapshot can hold; the client decoder rejects more. */
    public static final int MAX_TOTAL = 2 * PER_SIDE + MAX_OWN;

    /**
     * @param matches whether the order matches the viewer's current search (ignored for own orders)
     */
    public record Candidate(UUID id, boolean buy, boolean own, String itemKey, long unitPrice, boolean matches) { }

    private MarketOrderSelection() { }

    /** Own orders first (sells, then buys, best price first), then each side's round-robin pick. */
    public static List<Candidate> select(Collection<Candidate> candidates) {
        List<Candidate> result = new ArrayList<>();
        if (candidates == null) return result;
        candidates.stream().filter(Candidate::own)
                .sorted(Comparator.comparing(Candidate::buy).thenComparing(MarketOrderSelection::bestFirst))
                .limit(MAX_OWN).forEach(result::add);
        result.addAll(side(candidates, false));
        result.addAll(side(candidates, true));
        return result;
    }

    private static List<Candidate> side(Collection<Candidate> candidates, boolean buy) {
        Map<String, List<Candidate>> byItem = new HashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate.own() || !candidate.matches() || candidate.buy() != buy) continue;
            byItem.computeIfAbsent(candidate.itemKey() == null ? "" : candidate.itemKey(),
                    ignored -> new ArrayList<>()).add(candidate);
        }
        record Ranked(int rank, Candidate candidate) { }
        List<Ranked> ranked = new ArrayList<>();
        for (List<Candidate> item : byItem.values()) {
            item.sort(MarketOrderSelection::bestFirst);
            for (int rank = 0; rank < item.size() && rank < PER_SIDE; rank++) ranked.add(new Ranked(rank, item.get(rank)));
        }
        ranked.sort(Comparator.comparingInt(Ranked::rank)
                .thenComparing((first, second) -> bestFirst(first.candidate(), second.candidate()))
                .thenComparing(entry -> entry.candidate().itemKey() == null ? "" : entry.candidate().itemKey()));
        return ranked.stream().limit(PER_SIDE).map(Ranked::candidate).toList();
    }

    /** Cheapest sell or highest buy first; ties by id so a snapshot never reshuffles at random. */
    private static int bestFirst(Candidate first, Candidate second) {
        int byPrice = first.buy() ? Long.compare(second.unitPrice(), first.unitPrice())
                : Long.compare(first.unitPrice(), second.unitPrice());
        return byPrice != 0 ? byPrice : first.id().compareTo(second.id());
    }
}
