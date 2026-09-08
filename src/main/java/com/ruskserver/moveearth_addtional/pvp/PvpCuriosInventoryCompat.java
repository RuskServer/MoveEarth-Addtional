package com.ruskserver.moveearth_addtional.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

/** Optional Curios inventory isolation used by the server-authoritative PvP mode. */
final class PvpCuriosInventoryCompat {
    private static final String CURIOS_API_CLASS = "top.theillusivec4.curios.api.CuriosApi";

    private PvpCuriosInventoryCompat() {
    }

    static boolean isAvailable() {
        try {
            Class.forName(CURIOS_API_CLASS, false, PvpCuriosInventoryCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    /** Returns {@code null} when Curios is absent or its inventory cannot be captured safely. */
    static ListTag capture(ServerPlayer player) {
        if (!isAvailable()) return null;
        try {
            return CuriosIntegration.capture(player);
        } catch (RuntimeException | LinkageError error) {
            Moveearth_addtional.LOGGER.error("Failed to capture Curios inventory before PvP for {}",
                    player.getGameProfile().getName(), error);
            return null;
        }
    }

    static void clear(ServerPlayer player) {
        if (!isAvailable()) return;
        try {
            CuriosIntegration.clear(player);
        } catch (RuntimeException | LinkageError error) {
            Moveearth_addtional.LOGGER.error("Failed to clear Curios inventory for PvP participant {}",
                    player.getGameProfile().getName(), error);
        }
    }

    static void restore(ServerPlayer player, ListTag inventory) {
        if (inventory == null || !isAvailable()) return;
        try {
            CuriosIntegration.restore(player, inventory);
        } catch (RuntimeException | LinkageError error) {
            Moveearth_addtional.LOGGER.error("Failed to restore Curios inventory after PvP for {}",
                    player.getGameProfile().getName(), error);
        }
    }

    private static final class CuriosIntegration {
        private static ListTag capture(ServerPlayer player) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .map(handler -> handler.saveInventory(false).copy()).orElse(null);
        }
        private static void clear(ServerPlayer player) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .ifPresent(handler -> handler.saveInventory(true));
        }
        private static void restore(ServerPlayer player, ListTag inventory) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                handler.saveInventory(true);
                handler.loadInventory(inventory.copy());
            });
        }
    }
}
