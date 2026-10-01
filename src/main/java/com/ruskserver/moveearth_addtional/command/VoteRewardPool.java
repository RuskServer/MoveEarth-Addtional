package com.ruskserver.moveearth_addtional.command;

/** Equal-weight vote bonus slots. Keep progression-gated industrial parts out of this pool. */
final class VoteRewardPool {
    static final int BASE_TC = 2;

    enum Bonus {
        END_STONE,
        EFFICIENCY_PICKAXE,
        MENDING_PICKAXE,
        EXTRA_TC_2,
        EXTRA_TC_3,
        ANDESITE_ALLOY,
        BRASS_INGOT,
        ELECTRON_TUBE,
        COPPER_SHEET,
        GUNPOWDER
    }

    private static final Bonus[] SLOTS = {
            Bonus.END_STONE,
            Bonus.END_STONE,
            Bonus.END_STONE,
            Bonus.EFFICIENCY_PICKAXE,
            Bonus.MENDING_PICKAXE,
            Bonus.EXTRA_TC_2,
            Bonus.EXTRA_TC_3,
            Bonus.ANDESITE_ALLOY,
            Bonus.BRASS_INGOT,
            Bonus.ELECTRON_TUBE,
            Bonus.COPPER_SHEET,
            Bonus.GUNPOWDER
    };

    private VoteRewardPool() { }

    static int slotCount() { return SLOTS.length; }

    static Bonus bonusAt(int roll) { return SLOTS[roll]; }
}
