package com.ruskserver.moveearth_addtional.s2.provenance;

/** Lexically scoped causal origin; try-with-resources restores nested actions even after exceptions. */
public final class ActionContext {
    private static final ThreadLocal<Object> CURRENT = new ThreadLocal<>();
    private ActionContext() { }
    public static Object current() { return CURRENT.get(); }
    public static Scope push(Object origin) {
        Object previous = CURRENT.get();
        CURRENT.set(origin);
        return new Scope(previous);
    }
    public static final class Scope implements AutoCloseable {
        private final Object previous;
        private boolean closed;
        private Scope(Object previous) { this.previous = previous; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
