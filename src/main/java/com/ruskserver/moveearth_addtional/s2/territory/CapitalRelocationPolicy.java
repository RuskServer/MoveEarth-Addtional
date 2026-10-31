package com.ruskserver.moveearth_addtional.s2.territory;

/** Rules shared by the authoritative commit and the candidate preview. */
public final class CapitalRelocationPolicy {
    private CapitalRelocationPolicy() { }

    public enum Result {
        READY, SUCCESS, OWNER_ONLY, STALE, SIEGE_LOCKED, RECOVERY_LOCKED, COOLDOWN,
        INVALID_TARGET, NOT_SEALED, DAMAGED, VAULT_CONFLICT, NOT_LOADED
    }

    public static Result authority(boolean owner, boolean fresh, boolean siegeLocked,
                                   boolean recovering, long remainingTicks) {
        if (!owner) return Result.OWNER_ONLY;
        if (!fresh) return Result.STALE;
        if (siegeLocked) return Result.SIEGE_LOCKED;
        if (recovering) return Result.RECOVERY_LOCKED;
        return remainingTicks > 0 ? Result.COOLDOWN : Result.READY;
    }

    public static Result candidate(boolean properPair, boolean bothSealed, boolean bothHealthy,
                                   boolean vaultConflict, boolean loaded) {
        if (!properPair) return Result.INVALID_TARGET;
        if (!bothSealed) return Result.NOT_SEALED;
        if (!bothHealthy) return Result.DAMAGED;
        if (vaultConflict) return Result.VAULT_CONFLICT;
        return loaded ? Result.READY : Result.NOT_LOADED;
    }

    public static long remaining(long now, long until) { return Math.max(0L, until - now); }
}
