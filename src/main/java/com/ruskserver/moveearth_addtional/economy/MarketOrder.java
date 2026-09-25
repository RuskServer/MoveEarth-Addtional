package com.ruskserver.moveearth_addtional.economy;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** A fixed station and exact item variant; remaining units are escrowed stock or demand. */
public record MarketOrder(UUID id, Side side, UUID owner, UUID stationId, ItemStack item,
                          int remaining, long unitPrice, long expiresAt) {
    public MarketOrder {
        if (id == null || side == null || owner == null || stationId == null
                || item == null || item.isEmpty() || remaining < 0 || unitPrice < 1)
            throw new IllegalArgumentException("Invalid market order");
        item = item.copyWithCount(1);
    }

    public MarketOrder withRemaining(int next) {
        return new MarketOrder(id, side, owner, stationId, item, next, unitPrice, expiresAt);
    }

    public enum Side { SELL, BUY }
}
