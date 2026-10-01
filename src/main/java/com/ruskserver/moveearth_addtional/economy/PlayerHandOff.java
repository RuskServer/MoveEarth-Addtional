package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Crash-safe hand-off of ledger-held goods: book what fits, open a hand-off, take the goods off
 * the goods store, commit (which writes the small goods file at once), then move the items. The
 * worst case of a crash is a lost hand-off, never a duplicate; see {@link EconomyGoodsSavedData}.
 *
 * <pre>{@code
 * try (PlayerHandOff.HandOff handOff = PlayerHandOff.begin(ledger)) {
 *     ...reduce claims or rewards...
 *     if (!handOff.commit(player)) return 0;   // nothing moved, the store was rolled back
 * }
 * PlayerHandOff.give(player, stack);
 * }</pre>
 */
public final class PlayerHandOff {
    private static final String SAVE_FAILED_KEY = "message.moveearth_addtional.economy.handoff_save_failed";

    private PlayerHandOff() { }

    /** Opens a hand-off: store changes from here until {@link HandOff#commit} can be rolled back. */
    public static HandOff begin(EconomyLedgerSavedData ledger) {
        EconomyGoodsSavedData goods = ledger.goods();
        goods.beginHandOff();
        return new HandOff(goods);
    }

    /** One open hand-off; closing it without a successful commit rolls its changes back. */
    public static final class HandOff implements AutoCloseable {
        private final EconomyGoodsSavedData goods;

        private HandOff(EconomyGoodsSavedData goods) {
            this.goods = goods;
        }

        /**
         * Writes the goods file now, synchronously. Items may move only when this returns true;
         * on false the store is back as it was and the player has been told.
         */
        public boolean commit(ServerPlayer player) {
            if (goods.commitHandOff(player.server)) return true;
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(SAVE_FAILED_KEY)));
            return false;
        }

        @Override
        public void close() {
            if (goods.handOffOpen()) goods.abortHandOff();
        }
    }

    /** The player's current free space; main slots take new stacks, the offhand only tops up. */
    public static HandOffSpace<ItemStack> space(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int main = inventory.items.size();
        List<ItemStack> keys = new ArrayList<>(main + 1);
        int[] counts = new int[main + 1];
        boolean[] takesNew = new boolean[main + 1];
        for (int slot = 0; slot < main; slot++) {
            ItemStack stack = inventory.items.get(slot);
            keys.add(stack.isEmpty() ? null : stack);
            counts[slot] = stack.getCount();
            takesNew[slot] = true;
        }
        ItemStack offhand = inventory.offhand.get(0);
        keys.add(offhand.isEmpty() ? null : offhand);
        counts[main] = offhand.getCount();
        return new HandOffSpace<>(keys, counts, takesNew, ItemStack::isSameItemSameComponents,
                inventory.getMaxStackSize());
    }

    /** Books up to {@code stack.getCount()} units; the stack itself is not changed. */
    public static int book(HandOffSpace<ItemStack> space, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        // Damaged items never merge (Inventory#add sends them to an empty slot) and stack to one.
        return space.book(stack, stack.getCount(), stack.isDamaged() ? 1 : stack.getMaxStackSize());
    }

    /**
     * Moves an already-booked stack into the inventory. Anything that unexpectedly does not fit
     * (the booking and the insert run in the same tick) is dropped at the player's feet, since it
     * has already left the goods store.
     */
    public static void give(ServerPlayer player, ItemStack stack) {
        ItemStack moving = stack.copy();
        player.getInventory().add(moving);
        if (!moving.isEmpty()) player.drop(moving, false);
    }
}
