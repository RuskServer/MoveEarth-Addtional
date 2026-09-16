package com.ruskserver.moveearth_addtional.s2;

import java.util.EnumSet;
import java.util.Set;

/** Stable nation permission bits shared by persistence, packets and the GUI. */
public enum S2Permission {
    MANAGE_MEMBERS(0),
    MANAGE_ROLES(1),
    MANAGE_TERRITORY(2),
    MANAGE_DIPLOMACY(3),
    MANAGE_TREASURY(4),
    MANAGE_SIEGE(5),
    OWNER(6),
    MANAGE_REINFORCEMENT(7),
    BASTION_ACCESS(8),
    MANAGE_NOTIFICATIONS(9),
    MANAGE_TECHNOLOGY(10),
    MANAGE_PRISONERS(11),
    MANAGE_DISPATCH(12);

    private final long mask;

    S2Permission(int bit) {
        this.mask = 1L << bit;
    }

    public long mask() {
        return mask;
    }

    public static long toMask(Iterable<S2Permission> permissions) {
        long result = 0L;
        for (S2Permission permission : permissions) result |= permission.mask;
        return result;
    }

    public static Set<S2Permission> fromMask(long mask) {
        EnumSet<S2Permission> result = EnumSet.noneOf(S2Permission.class);
        for (S2Permission permission : values()) {
            if ((mask & permission.mask) != 0L) result.add(permission);
        }
        return Set.copyOf(result);
    }
}
