package com.ruskserver.moveearth_addtional.pvp.reward;

import net.minecraft.world.item.ItemStack;

/**
 * What PvP points buy. The blaze burner comes lit, a way to one for players who cannot take on a Nether gate;
 * points come only from tasks, a little under 100 a day, so it cannot be farmed. The weapon crate that used to
 * be here was taken out: its guns were too strong for the open world.
 */
public enum PvpExchange {
    BLAZE_BURNER(150, "ブレイズバーナー");

    private final int cost;
    private final String displayName;

    PvpExchange(int cost, String displayName) {
        this.cost = cost;
        this.displayName = displayName;
    }

    public int cost() {
        return cost;
    }

    public String displayName() {
        return displayName;
    }

    public ItemStack stack() {
        return switch (this) {
            case BLAZE_BURNER -> new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "blaze_burner")));
        };
    }

    public static PvpExchange byOrdinal(int ordinal) {
        PvpExchange[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
