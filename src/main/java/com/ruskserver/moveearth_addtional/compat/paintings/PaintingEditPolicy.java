package com.ruskserver.moveearth_addtional.compat.paintings;

import java.util.UUID;

/**
 * Who may change what an Immersive Paintings painting shows. Pure, so it is tested without a server.
 *
 * <p>The mod lets anyone standing next to a painting swap its image for one of their own, with no trace of who
 * did it. Breaking a painting stays open, as for vanilla paintings and item frames: that is part of a war, and
 * is seen. Swapping is not: on a nation's land, or on its vehicle, only that nation and its allies may do it.
 */
public final class PaintingEditPolicy {
    private PaintingEditPolicy() {
    }

    /**
     * @param guardian the nation whose land or vehicle holds the painting, or {@code null} for no one's
     * @param actorNation the player's nation, or {@code null}
     * @param allied whether {@code actorNation} is allied with {@code guardian}
     */
    public static boolean mayEdit(boolean staff, UUID guardian, UUID actorNation, boolean allied) {
        if (staff || guardian == null) return true;
        return guardian.equals(actorNation) || (actorNation != null && allied);
    }
}
