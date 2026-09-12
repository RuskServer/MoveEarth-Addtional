package com.ruskserver.moveearth_addtional.s2.territory;

/** Progressive territory penalty derived from continuous unpaid time. */
public enum UpkeepPenalty {
    CURRENT,
    GRACE,
    WEAKENED,
    DISABLED;

    public boolean bastionEnabled() {
        return this != DISABLED;
    }

    public boolean reinforcementProtectionEnabled() {
        return this != DISABLED;
    }
}
