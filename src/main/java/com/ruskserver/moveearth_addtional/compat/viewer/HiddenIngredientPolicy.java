package com.ruskserver.moveearth_addtional.compat.viewer;

import com.ruskserver.moveearth_addtional.compat.mekanism.MekanismRuntimeRestrictionPolicy;
import com.ruskserver.moveearth_addtional.handler.EnderChestRestrictionPolicy;

/**
 * Items a recipe viewer should leave out of its ingredient list. Their recipes
 * are already removed from the server's recipe manager, so viewers never show
 * them; this hides the items themselves, which would otherwise sit in the list
 * with nothing that makes them.
 *
 * <p>Reuses the restriction policies, so the viewer and the server always agree.
 */
public final class HiddenIngredientPolicy {
    private static final String MEKANISM = "mekanism";

    private HiddenIngredientPolicy() { }

    public static boolean isHidden(String namespace, String path) {
        return MekanismRuntimeRestrictionPolicy.isRestrictedItem(namespace, path)
                || EnderChestRestrictionPolicy.isRestrictedId(namespace, path)
                || isOreMultiplicationIntermediate(namespace, path);
    }

    /**
     * Clumps, shards, crystals and dirty dust exist only for Mekanism's x3-x5
     * ore chains, which MekanismRecipePolicy removes entirely.
     */
    private static boolean isOreMultiplicationIntermediate(String namespace, String path) {
        return MEKANISM.equals(namespace)
                && (path.startsWith("clump_")
                || path.startsWith("shard_")
                || path.startsWith("crystal_")
                || path.startsWith("dirty_dust_"));
    }
}
