package com.ruskserver.moveearth_addtional.s2.siege;

import java.util.UUID;

/** Selects a stable nation or individual identity for a destructive action. */
public final class SiegeAttackerPolicy {
    private SiegeAttackerPolicy() { }

    public static Identity resolve(UUID nationId, UUID actorId, boolean continuesIndividualSiege) {
        if (continuesIndividualSiege && actorId != null) return new Identity(actorId, true);
        if (nationId != null) return new Identity(nationId, false);
        return actorId == null ? null : new Identity(actorId, true);
    }

    public record Identity(UUID id, boolean individual) { }
}
