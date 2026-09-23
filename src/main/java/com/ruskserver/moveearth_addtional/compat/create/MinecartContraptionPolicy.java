package com.ruskserver.moveearth_addtional.compat.create;

import java.util.UUID;

/** Pure decisions for keeping minecart contraptions in the freight role. */
public final class MinecartContraptionPolicy {
    public static final int MAX_BLOCKS = 256;
    public static final int MAX_CONTAINERS = 16;

    private MinecartContraptionPolicy() {
    }

    public static AssemblyFailure assemblyFailure(boolean hasHomeTerritory, boolean allBlocksAtHome,
                                                   int blocks, int containers,
                                                   boolean hasReinforcement, boolean hasProtectedBlock) {
        if (!hasHomeTerritory) return AssemblyFailure.NO_HOME_TERRITORY;
        if (!allBlocksAtHome) return AssemblyFailure.CROSSES_TERRITORY;
        if (blocks > MAX_BLOCKS) return AssemblyFailure.TOO_LARGE;
        if (containers > MAX_CONTAINERS) return AssemblyFailure.TOO_MANY_CONTAINERS;
        if (hasReinforcement) return AssemblyFailure.REINFORCED_BLOCK;
        if (hasProtectedBlock) return AssemblyFailure.PROTECTED_BLOCK;
        return AssemblyFailure.NONE;
    }

    public static boolean canPlaceAssembler(UUID actorNation, UUID controllingNation,
                                            boolean canManageTerritory) {
        return actorNation != null && actorNation.equals(controllingNation) && canManageTerritory;
    }

    /** Wilderness is a valid unloading destination; controlled land must belong to the cart owner. */
    public static boolean canDisassemble(UUID ownerNation, UUID controllingNation) {
        return controllingNation == null || ownerNation != null && ownerNation.equals(controllingNation);
    }

    public static boolean suspendWorkActors(UUID ownerNation, UUID controllingNation, boolean hasWorkDevice) {
        return hasWorkDevice && controllingNation != null
                && (ownerNation == null || !ownerNation.equals(controllingNation));
    }

    public static boolean isWeaponNamespace(String namespace) {
        if (namespace == null) return false;
        String normalized = namespace.toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("createbigcannons") || normalized.equals("tacz")
                || normalized.contains("warnautic");
    }

    public enum AssemblyFailure {
        NONE,
        NO_HOME_TERRITORY,
        CROSSES_TERRITORY,
        TOO_LARGE,
        TOO_MANY_CONTAINERS,
        REINFORCED_BLOCK,
        PROTECTED_BLOCK
    }
}
