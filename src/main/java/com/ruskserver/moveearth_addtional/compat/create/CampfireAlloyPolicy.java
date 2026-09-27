package com.ruskserver.moveearth_addtional.compat.create;

/** Pure rule for campfire alloying. Kept free of Minecraft classes for unit tests. */
public final class CampfireAlloyPolicy {
    private CampfireAlloyPolicy() {
    }

    /**
     * Whether a basin recipe's heat requirement counts as met.
     *
     * @param requiresHeated   the recipe asks for "heated" (superheated recipes never qualify)
     * @param createAllows     Create's own verdict for the heat source under the basin
     * @param campfireLit      a lit campfire sits directly under the basin
     * @param campfireAlloy    the recipe makes an item in the campfire-alloy tag
     */
    public static boolean heatMet(boolean requiresHeated, boolean createAllows,
                                  boolean campfireLit, boolean campfireAlloy) {
        return createAllows || (requiresHeated && campfireLit && campfireAlloy);
    }
}
