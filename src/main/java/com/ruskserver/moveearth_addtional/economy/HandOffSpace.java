package com.ruskserver.moveearth_addtional.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A scratch copy of an inventory's free space, filled the way {@code Inventory.add} fills the real
 * one: matching partial stacks first, then empty slots that take new stacks. Claims book what fits
 * here, remove it from the ledger and save that, and only then move the items, so a crash between
 * the player save and the ledger save can lose a hand-off but never duplicate it.
 */
public final class HandOffSpace<K> {
    private final List<K> keys;
    private final int[] counts;
    private final boolean[] takesNewStacks;
    private final BiPredicate<K, K> same;
    private final int containerLimit;

    /**
     * @param keys           slot contents, {@code null} for an empty slot
     * @param counts         the count in each slot
     * @param takesNewStacks whether an empty slot may receive a new stack (the offhand only tops up)
     * @param same           whether two slot keys stack together
     * @param containerLimit the container's own per-slot limit
     */
    public HandOffSpace(List<K> keys, int[] counts, boolean[] takesNewStacks,
                        BiPredicate<K, K> same, int containerLimit) {
        if (keys.size() != counts.length || counts.length != takesNewStacks.length)
            throw new IllegalArgumentException("Slot arrays differ in length");
        this.keys = new ArrayList<>(keys);
        this.counts = counts.clone();
        this.takesNewStacks = takesNewStacks.clone();
        this.same = same;
        this.containerLimit = containerLimit;
    }

    /** Books up to {@code amount} units of {@code key} and returns how many fit. */
    public int book(K key, int amount, int maxStack) {
        int limit = Math.min(maxStack, containerLimit);
        if (key == null || amount <= 0 || limit <= 0) return 0;
        int left = amount;
        for (int slot = 0; slot < counts.length && left > 0; slot++) {
            K held = keys.get(slot);
            if (held == null || counts[slot] >= limit || !same.test(held, key)) continue;
            int taken = Math.min(left, limit - counts[slot]);
            counts[slot] += taken;
            left -= taken;
        }
        for (int slot = 0; slot < counts.length && left > 0; slot++) {
            if (keys.get(slot) != null || !takesNewStacks[slot]) continue;
            int taken = Math.min(left, limit);
            keys.set(slot, key);
            counts[slot] = taken;
            left -= taken;
        }
        return amount - left;
    }
}
