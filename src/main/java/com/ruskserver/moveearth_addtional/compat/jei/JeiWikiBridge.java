package com.ruskserver.moveearth_addtional.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;

/**
 * What JEI shows under the mouse, for the in-game wiki key. Only touched when JEI is loaded; the runtime is
 * handed over by {@link MoveEarthJeiPlugin}.
 */
public final class JeiWikiBridge {
    private static IJeiRuntime runtime;

    private JeiWikiBridge() { }

    static void runtime(IJeiRuntime value) {
        runtime = value;
    }

    /** The item JEI has under the mouse: in its list, its bookmarks or a recipe screen; empty if none. */
    public static ItemStack hoveredItem() {
        IJeiRuntime current = runtime;
        if (current == null) return ItemStack.EMPTY;
        ItemStack listed = current.getIngredientListOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (listed != null && !listed.isEmpty()) return listed;
        ItemStack bookmarked = current.getBookmarkOverlay().getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
        if (bookmarked != null && !bookmarked.isEmpty()) return bookmarked;
        return current.getRecipesGui().getIngredientUnderMouse(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
    }

    /** JEI's search box is being typed in, so a key press is text, not a command. */
    public static boolean typing() {
        IJeiRuntime current = runtime;
        return current != null && current.getIngredientListOverlay().hasKeyboardFocus();
    }
}
