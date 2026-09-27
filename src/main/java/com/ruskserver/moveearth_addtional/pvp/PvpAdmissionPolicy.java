package com.ruskserver.moveearth_addtional.pvp;

/** Shared admission checks for registration, late joins and the end of map voting. */
public final class PvpAdmissionPolicy {
    private PvpAdmissionPolicy() { }

    /** An isolated participant changes a selection only; no new teleport or snapshot is needed. */
    public static boolean canUpdateSelection(boolean activeParticipant, boolean eligibleForAdmission) {
        return activeParticipant || eligibleForAdmission;
    }

    public static boolean canStart(int redPlayers, int bluePlayers) {
        return redPlayers > 0 && bluePlayers > 0;
    }

    public static boolean eligible(boolean alive, boolean spectator, boolean downed,
                                   boolean combatTagged, boolean movementRestricted) {
        return alive && !spectator && !downed && !combatTagged && !movementRestricted;
    }
}
