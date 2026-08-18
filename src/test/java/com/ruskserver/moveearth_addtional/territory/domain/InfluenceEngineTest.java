package com.ruskserver.moveearth_addtional.territory.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceEngineTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final TerritoryOwnerId OWNER_A = owner("00000000-0000-0000-0000-00000000000a");
    private static final TerritoryOwnerId OWNER_B = owner("00000000-0000-0000-0000-00000000000b");
    private final InfluenceEngine engine = new InfluenceEngine();

    @Test
    void influenceFallsWithDistanceAndEnablesConfiguredProtections() {
        InfluenceSource source = source(OWNER_A, 0, 0, 0, 10.0D);

        InfluenceResult result = engine.evaluate(position(20, 0, 0), List.of(source), List.of(), settings());

        assertEquals(100.0D, result.leadingInfluence(), 0.0001D);
        assertEquals(OWNER_A, result.controllingOwner().orElseThrow());
        assertTrue(result.protects(ProtectionAction.CONTAINER_ACCESS));
        assertTrue(result.protects(ProtectionAction.BLOCK_MODIFICATION));
        assertFalse(result.protects(ProtectionAction.SABLE_DAMAGE));
    }

    @Test
    void multipleCoresFromOneOwnerUseTheStrongestValueInsteadOfAdding() {
        InfluenceSource first = source(OWNER_A, 0, 0, 0, 0.0D);
        InfluenceSource second = source(OWNER_A, 10, 0, 0, 0.0D);

        InfluenceResult result = engine.evaluate(position(5, 0, 0),
                List.of(first, second), List.of(), settings());

        assertEquals(95.0D, result.influenceByOwner().get(OWNER_A), 0.0001D);
    }

    @Test
    void closeCompetingOwnersCreateAnUncontrolledContestedArea() {
        InfluenceSource first = source(OWNER_A, -10, 0, 0, 0.0D);
        InfluenceSource second = source(OWNER_B, 10, 0, 0, 0.0D);

        InfluenceResult result = engine.evaluate(position(0, 0, 0),
                List.of(first, second), List.of(), settings());

        assertTrue(result.contested());
        assertTrue(result.controllingOwner().isEmpty());
        assertTrue(result.protectedActions().isEmpty());
    }

    @Test
    void exactTieIsContestedEvenWhenContestMarginIsZero() {
        InfluenceSettings noMargin = new InfluenceSettings(
                100.0D, 2.0D, 1.0D, 0.0D, 80.0D,
                DistanceModel.SPHERE_3D, thresholds());
        InfluenceSource first = source(OWNER_A, -10, 0, 0, 0.0D);
        InfluenceSource second = source(OWNER_B, 10, 0, 0, 0.0D);

        InfluenceResult result = engine.evaluate(position(0, 0, 0),
                List.of(first, second), List.of(), noMargin);

        assertTrue(result.contested());
        assertTrue(result.controllingOwner().isEmpty());
    }

    @Test
    void strongerOwnerControlsAreaOutsideContestMargin() {
        InfluenceSource first = source(OWNER_A, 0, 0, 0, 10.0D);
        InfluenceSource second = source(OWNER_B, 40, 0, 0, 0.0D);

        InfluenceResult result = engine.evaluate(position(0, 0, 0),
                List.of(first, second), List.of(), settings());

        assertFalse(result.contested());
        assertEquals(OWNER_A, result.controllingOwner().orElseThrow());
    }

    @Test
    void enemyRaidSuppressesInfluenceButFriendlyRaidDoesNot() {
        InfluenceSource source = source(OWNER_A, 0, 0, 0, 0.0D);
        RaidEmitter enemy = raid(OWNER_B, 0, 0, 0, 20.0D, 40.0D);
        RaidEmitter friendly = raid(OWNER_A, 0, 0, 0, 20.0D, 90.0D);

        InfluenceResult result = engine.evaluate(position(0, 0, 0),
                List.of(source), List.of(enemy, friendly), settings());

        assertEquals(60.0D, result.leadingInfluence(), 0.0001D);
    }

    @Test
    void overlappingEnemyRaidsRespectGlobalSuppressionCap() {
        InfluenceSource source = source(OWNER_A, 0, 0, 0, 0.0D);
        RaidEmitter first = raid(OWNER_B, 0, 0, 0, 20.0D, 90.0D);
        RaidEmitter second = raid(OWNER_B, 0, 0, 0, 20.0D, 90.0D);

        InfluenceResult result = engine.evaluate(position(0, 0, 0),
                List.of(source), List.of(first, second), settings());

        assertEquals(20.0D, result.leadingInfluence(), 0.0001D);
    }

    @Test
    void cylinderModeIgnoresVerticalDistance() {
        InfluenceSettings cylinder = new InfluenceSettings(
                100.0D, 2.0D, 1.0D, 5.0D, 80.0D,
                DistanceModel.CYLINDER_2D, thresholds());
        InfluenceSource source = source(OWNER_A, 0, 200, 0, 0.0D);

        InfluenceResult result = engine.evaluate(position(0, 0, 0),
                List.of(source), List.of(), cylinder);

        assertEquals(100.0D, result.leadingInfluence(), 0.0001D);
    }

    @Test
    void sourcesInOtherDimensionsDoNotClaimTheQuery() {
        TerritoryCore core = new TerritoryCore(UUID.randomUUID(), OWNER_A,
                new TerritoryPosition("minecraft:the_nether", 0, 0, 0), true);

        InfluenceResult result = engine.evaluate(position(0, 0, 0),
                List.of(new InfluenceSource(core, 0.0D)), List.of(), settings());

        assertTrue(result.controllingOwner().isEmpty());
    }

    private static InfluenceSettings settings() {
        return new InfluenceSettings(
                100.0D, 2.0D, 1.0D, 5.0D, 80.0D,
                DistanceModel.SPHERE_3D, thresholds());
    }

    private static Map<ProtectionAction, Double> thresholds() {
        return Map.of(
                ProtectionAction.PLAYER_DAMAGE, 50.0D,
                ProtectionAction.BLOCK_MODIFICATION, 80.0D,
                ProtectionAction.CONTAINER_ACCESS, 100.0D,
                ProtectionAction.SABLE_DAMAGE, 110.0D
        );
    }

    private static InfluenceSource source(
            TerritoryOwnerId ownerId,
            double x,
            double y,
            double z,
            double industrialScore
    ) {
        return new InfluenceSource(
                new TerritoryCore(UUID.randomUUID(), ownerId, position(x, y, z), true),
                industrialScore
        );
    }

    private static RaidEmitter raid(
            TerritoryOwnerId ownerId,
            double x,
            double y,
            double z,
            double radius,
            double strength
    ) {
        return new RaidEmitter(UUID.randomUUID(), ownerId, position(x, y, z), radius, strength, true);
    }

    private static TerritoryPosition position(double x, double y, double z) {
        return new TerritoryPosition(OVERWORLD, x, y, z);
    }

    private static TerritoryOwnerId owner(String id) {
        return TerritoryOwnerId.of(UUID.fromString(id));
    }
}
