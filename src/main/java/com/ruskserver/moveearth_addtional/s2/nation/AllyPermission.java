package com.ruskserver.moveearth_addtional.s2.nation;

/**
 * What a host nation lets an allied nation, or one of its members, do inside the host's territory.
 * Bits are persisted and sent over the network; never renumber an existing entry.
 */
public enum AllyPermission {
    BUILD(0),
    STORAGE(1),
    REINFORCE(2);

    public static final int ALL_MASK = (1 << values().length) - 1;

    private final int bit;

    AllyPermission(int bit) {
        this.bit = bit;
    }

    public int mask() {
        return 1 << bit;
    }

    public int networkId() {
        return bit;
    }

    public static AllyPermission fromNetworkId(int id) {
        for (AllyPermission permission : values()) if (permission.bit == id) return permission;
        return null;
    }
}
