package com.ruskserver.moveearth_addtional.s2.reinforcement;

public enum ReinforcementMaterial {
    COBBLESTONE("cobblestone", 32, 0.25),
    COPPER("copper", 64, 0.5),
    IRON("iron", 128, 1.0),
    GOLD("gold", 192, 1.5),
    DIAMOND("diamond", 320, 2.0);

    private final String id;
    private final int maxDurability;
    private final double addedMass;

    ReinforcementMaterial(String id, int maxDurability, double addedMass) {
        this.id = id;
        this.maxDurability = maxDurability;
        this.addedMass = addedMass;
    }

    /** Additional Sable mass units (an ordinary default block weighs 1), not kilograms. */
    public double addedMass() {
        return addedMass;
    }

    public String id() {
        return id;
    }

    public int maxDurability() {
        return maxDurability;
    }

    public int repairPerItem() {
        return Math.max(1, maxDurability / 4);
    }

    public static ReinforcementMaterial fromId(String id) {
        for (ReinforcementMaterial material : values()) {
            if (material.id.equals(id)) return material;
        }
        return COBBLESTONE;
    }
}
