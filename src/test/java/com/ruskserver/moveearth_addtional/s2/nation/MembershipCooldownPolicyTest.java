package com.ruskserver.moveearth_addtional.s2.nation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembershipCooldownPolicyTest {
    private static final UUID CURRENT = UUID.randomUUID();
    private static final UUID FORMER = UUID.randomUUID();
    private static final UUID DEFENDER = UUID.randomUUID();

    @Test void cooldownLastsFourHoursOfOpenTime() {
        assertEquals(288_000L, MembershipCooldownPolicy.COOLDOWN_OPEN_TICKS);
        long endsAt = MembershipCooldownPolicy.endsAt(1_000L);
        assertTrue(MembershipCooldownPolicy.active(endsAt, 1_000L));
        assertEquals(288_000L, MembershipCooldownPolicy.remaining(endsAt, 1_000L));
        assertTrue(MembershipCooldownPolicy.active(endsAt, endsAt - 1L));
        assertFalse(MembershipCooldownPolicy.active(endsAt, endsAt));
        assertEquals(0L, MembershipCooldownPolicy.remaining(0L, 50L));
    }

    @Test void remainingMinutesRoundUp() {
        assertEquals(0L, MembershipCooldownPolicy.remainingMinutes(0L));
        assertEquals(1L, MembershipCooldownPolicy.remainingMinutes(1L));
        assertEquals(1L, MembershipCooldownPolicy.remainingMinutes(1_200L));
        assertEquals(2L, MembershipCooldownPolicy.remainingMinutes(1_201L));
        assertEquals(240L, MembershipCooldownPolicy.remainingMinutes(MembershipCooldownPolicy.COOLDOWN_OPEN_TICKS));
    }

    @Test void ceasefireFollowsCurrentNationFirst() {
        assertEquals(CURRENT, MembershipCooldownPolicy.ceasefireNation(CURRENT, FORMER, true, true));
    }

    @Test void nationlessPlayerStaysBoundToFormerNationDuringCooldown() {
        assertEquals(FORMER, MembershipCooldownPolicy.ceasefireNation(null, FORMER, true, true));
        assertNull(MembershipCooldownPolicy.ceasefireNation(null, FORMER, false, true));
        assertNull(MembershipCooldownPolicy.ceasefireNation(null, FORMER, true, false));
        assertNull(MembershipCooldownPolicy.ceasefireNation(null, null, true, true));
    }

    @Test void formerNationBlocksAttacksOnItsAlliesAndTrucePartners() {
        assertTrue(MembershipCooldownPolicy.formerNationBlocks(FORMER, DEFENDER, true, false));
        assertTrue(MembershipCooldownPolicy.formerNationBlocks(FORMER, DEFENDER, false, true));
        assertFalse(MembershipCooldownPolicy.formerNationBlocks(FORMER, DEFENDER, false, false));
        assertFalse(MembershipCooldownPolicy.formerNationBlocks(null, DEFENDER, true, true));
        assertFalse(MembershipCooldownPolicy.formerNationBlocks(FORMER, FORMER, true, true));
    }
}
