package com.ruskserver.moveearth_addtional.nether;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetherGateBattlesTest {
    private static final UUID RED = new UUID(1L, 1L);
    private static final UUID BLUE = new UUID(2L, 2L);
    private static final String OVERWORLD = "minecraft:overworld";

    private static NetherGateBattles.Battle battle(UUID nation, String dimension, int x, int z) {
        return new NetherGateBattles.Battle(UUID.randomUUID(), nation, dimension, x, 64, z);
    }

    private static NetherGateBattles.Verdict open(List<NetherGateBattles.Battle> active, UUID nation, int x, int z) {
        return NetherGateBattles.canOpen(active, nation, OVERWORLD, x, 64, z, 1, 3, 96);
    }

    @Test
    void firstBattleOpens() {
        assertEquals(NetherGateBattles.Verdict.ALLOWED, open(List.of(), RED, 0, 0));
    }

    @Test
    void oneBattlePerNationByDefault() {
        List<NetherGateBattles.Battle> active = List.of(battle(RED, OVERWORLD, 5000, 5000));
        assertEquals(NetherGateBattles.Verdict.NATION_LIMIT, open(active, RED, 0, 0));
        assertEquals(NetherGateBattles.Verdict.ALLOWED, open(active, BLUE, 0, 0));
    }

    @Test
    void serverWideLimitAppliesToEveryNation() {
        List<NetherGateBattles.Battle> active = List.of(
                battle(new UUID(3L, 3L), OVERWORLD, 1000, 0),
                battle(new UUID(4L, 4L), OVERWORLD, 2000, 0),
                battle(new UUID(5L, 5L), OVERWORLD, 3000, 0));
        assertEquals(NetherGateBattles.Verdict.SERVER_LIMIT, open(active, RED, 0, 0));
    }

    @Test
    void activeGatesKeepTheirDistanceInTheSameDimensionOnly() {
        List<NetherGateBattles.Battle> near = List.of(battle(BLUE, OVERWORLD, 95, 0));
        assertEquals(NetherGateBattles.Verdict.TOO_CLOSE, open(near, RED, 0, 0));
        List<NetherGateBattles.Battle> edge = List.of(battle(BLUE, OVERWORLD, 96, 0));
        assertEquals(NetherGateBattles.Verdict.ALLOWED, open(edge, RED, 0, 0));
        List<NetherGateBattles.Battle> elsewhere = List.of(battle(BLUE, "minecraft:the_nether", 0, 0));
        assertEquals(NetherGateBattles.Verdict.ALLOWED, open(elsewhere, RED, 0, 0));
    }

    @Test
    void zeroDistanceTurnsTheSpacingRuleOff() {
        List<NetherGateBattles.Battle> active = List.of(battle(BLUE, OVERWORLD, 1, 0));
        assertEquals(NetherGateBattles.Verdict.ALLOWED,
                NetherGateBattles.canOpen(active, RED, OVERWORLD, 0, 64, 0, 1, 3, 0));
    }

    @Test
    void largerNationLimitAllowsAnotherBattle() {
        List<NetherGateBattles.Battle> active = List.of(battle(RED, OVERWORLD, 5000, 0));
        assertEquals(NetherGateBattles.Verdict.ALLOWED,
                NetherGateBattles.canOpen(active, RED, OVERWORLD, 0, 64, 0, 2, 3, 96));
    }

    @Test
    void desertedAfterTheTimeoutInclusive() {
        assertFalse(NetherGateBattles.deserted(100L, 699L, 600));
        assertTrue(NetherGateBattles.deserted(100L, 700L, 600));
    }
}
