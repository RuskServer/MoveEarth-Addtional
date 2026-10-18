package com.ruskserver.moveearth_addtional.compat.create;

import java.util.UUID;

/**
 * The rules for Create minecart contraptions, kept to the four things they could otherwise be used for: taking
 * blocks out of another nation's land, putting blocks into it, driving drills and other work heads through it,
 * and slipping cargo away as an item. Anything else a freight cart does is left alone. Pure, so it is tested
 * without a level.
 *
 * <p>A cart belongs to the nation whose land its assembler stands on, or to nobody when assembled on land no
 * nation holds. "Its own land" below means that nation's land; every cart may also use land no nation holds.
 */
public final class MinecartContraptionPolicy {
    public static final int MAX_BLOCKS = 256;
    /** Storage blocks, counted one per block: a double chest is two, a vault one per block of it. */
    public static final int MAX_CONTAINERS = 16;

    private MinecartContraptionPolicy() {
    }

    /** Whether a block on land held by {@code landNation} may be taken by a cart belonging to {@code owner}. */
    public static boolean ownLand(UUID owner, UUID landNation) {
        return landNation == null || landNation.equals(owner);
    }

    public static AssemblyFailure assemblyFailure(boolean allBlocksOnOwnLand, int blocks, int containers,
                                                   boolean hasReinforcement, boolean hasProtectedBlock) {
        if (!allBlocksOnOwnLand) return AssemblyFailure.FOREIGN_BLOCK;
        if (hasReinforcement) return AssemblyFailure.REINFORCED_BLOCK;
        if (hasProtectedBlock) return AssemblyFailure.PROTECTED_BLOCK;
        if (blocks > MAX_BLOCKS) return AssemblyFailure.TOO_LARGE;
        if (containers > MAX_CONTAINERS) return AssemblyFailure.TOO_MANY_CONTAINERS;
        return AssemblyFailure.NONE;
    }

    /** An assembler goes on the placer's own nation's land, or on land no nation holds. */
    public static boolean canPlaceAssembler(UUID actorNation, UUID landNation) {
        return landNation == null || landNation.equals(actorNation);
    }

    /**
     * Whether a cart may put its blocks down here.
     *
     * @param tagged      the cart was assembled under these rules; older carts carry no owner
     * @param atAssembler the cart is being unloaded by an assembler rather than broken or knocked off
     */
    public static boolean canUnload(boolean tagged, UUID owner, UUID landNation, boolean atAssembler) {
        // A cart from before these rules has no owner to check. An assembler stands only where its placer could
        // build, so it unloads there once; anywhere else it must be on land no nation holds.
        if (!tagged) return atAssembler || landNation == null;
        return ownLand(owner, landNation);
    }

    /** Drills, saws, deployers and the like stop on any land that is not the cart's own. */
    public static boolean suspendWorkActors(UUID owner, UUID landNation, boolean hasWorkDevice) {
        return hasWorkDevice && !ownLand(owner, landNation);
    }

    public static boolean isWeaponNamespace(String namespace) {
        if (namespace == null) return false;
        String normalized = namespace.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("createbigcannons") || normalized.equals("tacz")
                || normalized.contains("warnautic");
    }

    public enum AssemblyFailure {
        NONE,
        FOREIGN_BLOCK,
        TOO_LARGE,
        TOO_MANY_CONTAINERS,
        REINFORCED_BLOCK,
        PROTECTED_BLOCK
    }
}
