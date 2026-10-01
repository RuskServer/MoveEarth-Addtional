package com.ruskserver.moveearth_addtional.economy;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The ordering rule of a crash-safe hand-off, free of Minecraft types: while a hand-off is open,
 * every change to the goods store records how to undo itself; the goods are given only after the
 * store was persisted, and a failed persist undoes the changes instead of giving anything.
 *
 * <p>So the player file can only ever hold handed-off goods once a store file without them is on
 * disk: a crash can lose a hand-off, never duplicate it. A failed write (disk full, IO error)
 * leaves both the store and the inventory as they were.
 */
public final class HandOffJournal {
    private ArrayDeque<Runnable> undo;

    /** Starts recording; hand-offs never nest. */
    public void begin() {
        if (undo != null) throw new IllegalStateException("A hand-off is already open");
        undo = new ArrayDeque<>();
    }

    public boolean open() { return undo != null; }

    /** Called by the store for each change; ignored outside a hand-off. */
    public void record(Runnable inverse) {
        if (undo != null) undo.push(inverse);
    }

    /**
     * Persists, then gives each item only if that worked; otherwise rolls the store back.
     * Returns whether the items moved.
     */
    public <T> boolean commitThenGive(BooleanSupplier persist, List<T> items, Consumer<T> give) {
        if (!commit(persist)) return false;
        items.forEach(give);
        return true;
    }

    /** Ends the hand-off: true when the store is on disk (or nothing changed), false after a rollback. */
    public boolean commit(BooleanSupplier persist) {
        ArrayDeque<Runnable> changes = closeRecording();
        if (changes.isEmpty() || persist.getAsBoolean()) return true;
        changes.forEach(Runnable::run);
        return false;
    }

    /** Ends a hand-off that was not committed (an early return or exception), undoing its changes. */
    public void abort() {
        if (undo == null) return;
        closeRecording().forEach(Runnable::run);
    }

    private ArrayDeque<Runnable> closeRecording() {
        if (undo == null) throw new IllegalStateException("No hand-off is open");
        ArrayDeque<Runnable> changes = undo;
        // Stop recording before any rollback runs, so the undo steps do not record themselves.
        undo = null;
        return changes;
    }
}
