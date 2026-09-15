package com.ruskserver.moveearth_addtional.client.compat;

import net.minecraft.resources.ResourceLocation;

import java.util.function.Predicate;

/** API-free boundary so clients without JEI never resolve JEI classes. */
public final class JeiTechnologyBridge {
    private static volatile Predicate<ResourceLocation> recipeOpener = ignored -> false;
    private JeiTechnologyBridge() { }
    public static boolean showRecipes(ResourceLocation itemId) { return recipeOpener.test(itemId); }
    public static void install(Predicate<ResourceLocation> opener) { recipeOpener = opener == null ? ignored -> false : opener; }
    public static void clear() { recipeOpener = ignored -> false; }
}
