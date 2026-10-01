package com.ruskserver.moveearth_addtional.command;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoteRewardPoolTest {
    @Test
    void endStoneIsAQuarterOfThePoolAndBothPickaxesRemain() {
        var counts = new EnumMap<VoteRewardPool.Bonus, Integer>(VoteRewardPool.Bonus.class);
        for (int roll = 0; roll < VoteRewardPool.slotCount(); roll++) {
            counts.merge(VoteRewardPool.bonusAt(roll), 1, Integer::sum);
        }
        assertEquals(12, VoteRewardPool.slotCount());
        assertEquals(3, counts.get(VoteRewardPool.Bonus.END_STONE));
        assertEquals(1, counts.get(VoteRewardPool.Bonus.EFFICIENCY_PICKAXE));
        assertEquals(1, counts.get(VoteRewardPool.Bonus.MENDING_PICKAXE));
        assertEquals(1, counts.get(VoteRewardPool.Bonus.EXTRA_TC_2));
        assertEquals(1, counts.get(VoteRewardPool.Bonus.EXTRA_TC_3));
        assertEquals(2, VoteRewardPool.BASE_TC);
    }
}
