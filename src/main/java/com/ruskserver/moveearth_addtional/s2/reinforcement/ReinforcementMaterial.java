package com.ruskserver.moveearth_addtional.s2.reinforcement;

public enum ReinforcementMaterial {
    COBBLESTONE("cobblestone", 32),
    COPPER("copper", 64),
    IRON("iron", 128),
    GOLD("gold", 192),
    DIAMOND("diamond", 320);

    private final String id;
    private final int maxDurability;

    ReinforcementMaterial(String id, int maxDurability) {
        this.id = id;
        this.maxDurability = maxDurability;
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
