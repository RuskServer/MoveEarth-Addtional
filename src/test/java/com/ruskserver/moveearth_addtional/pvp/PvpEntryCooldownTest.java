package com.ruskserver.moveearth_addtional.pvp;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PvpEntryCooldownTest {
    private final UUID player = UUID.randomUUID();

    @Test void firstTransitionIsAllowed() {
        assertEquals(0, new PvpEntryCooldown(100).remainingTicks(player, 5));
    }

    @Test void transitionBlocksUntilCooldownElapses() {
        PvpEntryCooldown cooldown = new PvpEntryCooldown(100);
        cooldown.record(player, 1000);
        assertEquals(100, cooldown.remainingTicks(player, 1000));
        assertEquals(1, cooldown.remainingTicks(player, 1099));
        assertEquals(0, cooldown.remainingTicks(player, 1100));
        assertEquals(0, cooldown.remainingTicks(UUID.randomUUID(), 1000), "cooldown is per player");
    }

    @Test void clockGoingBackwardsNeverBlocks() {
        PvpEntryCooldown cooldown = new PvpEntryCooldown(100);
        cooldown.record(player, 1000);
        assertEquals(0, cooldown.remainingTicks(player, 10));
    }

    @Test void clearForgetsEveryone() {
        PvpEntryCooldown cooldown = new PvpEntryCooldown(100);
        cooldown.record(player, 1000);
        cooldown.clear();
        assertEquals(0, cooldown.remainingTicks(player, 1001));
    }

    @Test void pruningKeepsPlayersStillCoolingDown() {
        PvpEntryCooldown cooldown = new PvpEntryCooldown(100);
        UUID recent = UUID.randomUUID();
        for (int i = 0; i < 70; i++) cooldown.record(UUID.randomUUID(), 0);
        cooldown.record(recent, 950);
        cooldown.record(UUID.randomUUID(), 1000);
        assertEquals(50, cooldown.remainingTicks(recent, 1000));
    }

    @Test void secondsRoundUp() {
        assertEquals(0, PvpEntryCooldown.secondsCeil(0));
        assertEquals(1, PvpEntryCooldown.secondsCeil(1));
        assertEquals(1, PvpEntryCooldown.secondsCeil(20));
        assertEquals(5, PvpEntryCooldown.secondsCeil(100));
        assertEquals(5, PvpEntryCooldown.secondsCeil(81));
    }
}
