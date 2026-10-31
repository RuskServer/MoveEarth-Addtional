package com.ruskserver.moveearth_addtional.s2.provenance;

import java.util.UUID;

/** Known actors may be nationless; a nation-only automated weapon is also accountable. */
public record ActionSource(UUID actor, UUID nation, Kind kind) {
    public enum Kind { ATTRIBUTED, MOB, NATURAL, UNKNOWN }
    public static final ActionSource UNKNOWN = new ActionSource(null, null, Kind.UNKNOWN);
    public static final ActionSource NATURAL = new ActionSource(null, null, Kind.NATURAL);
    public static final ActionSource MOB = new ActionSource(null, null, Kind.MOB);
    public boolean allowed() {
        return kind == Kind.NATURAL || kind == Kind.MOB
                || kind == Kind.ATTRIBUTED && (actor != null || nation != null);
    }
    public boolean attributable() { return kind == Kind.ATTRIBUTED && (actor != null || nation != null); }
}
