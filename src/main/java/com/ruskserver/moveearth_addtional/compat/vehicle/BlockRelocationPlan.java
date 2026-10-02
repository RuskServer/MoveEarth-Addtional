package com.ruskserver.moveearth_addtional.compat.vehicle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Per-position metadata that has to follow a block when Sable moves it, as one
 * batch.
 *
 * <p>Every entry is read before any is written, and every source is cleared
 * before any destination is written: a move whose source and destination sets
 * overlap (a rotation inside one plot, a merge into a neighbouring body) must
 * never overwrite an entry that is itself still waiting to move, nor clear one
 * that has just arrived.
 *
 * @param <P> position
 * @param <E> metadata stored at a position
 */
public final class BlockRelocationPlan<P, E> {
    public record Move<P, E>(P from, P to, E value) { }

    private final List<Move<P, E>> moves;

    private BlockRelocationPlan(List<Move<P, E>> moves) { this.moves = List.copyOf(moves); }

    /**
     * @param sources  positions Sable moves, in its order; duplicates are moved once
     * @param mapping  Sable's own source-to-destination transform
     * @param metadata value stored at a source, or null for none (that source is skipped)
     */
    public static <P, E> BlockRelocationPlan<P, E> of(Iterable<? extends P> sources, UnaryOperator<P> mapping,
                                                      Function<? super P, ? extends E> metadata) {
        List<Move<P, E>> moves = new ArrayList<>();
        Set<P> seen = new HashSet<>();
        for (P source : sources) {
            if (!seen.add(source)) continue;
            E value = metadata.apply(source);
            if (value != null) moves.add(new Move<>(source, mapping.apply(source), value));
        }
        return new BlockRelocationPlan<>(moves);
    }

    public List<Move<P, E>> moves() { return moves; }

    public boolean isEmpty() { return moves.isEmpty(); }

    /** Clears every source, then writes every destination. */
    public void apply(Consumer<? super P> clearSource, BiConsumer<? super P, ? super E> writeDestination) {
        for (Move<P, E> move : moves) clearSource.accept(move.from());
        for (Move<P, E> move : moves) writeDestination.accept(move.to(), move.value());
    }
}
