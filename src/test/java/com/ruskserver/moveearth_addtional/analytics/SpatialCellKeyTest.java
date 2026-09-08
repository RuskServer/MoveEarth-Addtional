package com.ruskserver.moveearth_addtional.analytics;

import com.ruskserver.moveearth_addtional.analytics.group.GroupRelation;
import com.ruskserver.moveearth_addtional.analytics.model.SpatialCellKey;
import com.ruskserver.moveearth_addtional.analytics.model.YBand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SpatialCellKeyTest {

    @Test
    public void testCellCoordinateConversion() {
        assertEquals(0, SpatialCellKey.toCellCoordinate(0.0));
        assertEquals(0, SpatialCellKey.toCellCoordinate(31.9));
        assertEquals(1, SpatialCellKey.toCellCoordinate(32.0));
        assertEquals(1, SpatialCellKey.toCellCoordinate(63.9));

        // 負の座標 (floorDiv)
        assertEquals(-1, SpatialCellKey.toCellCoordinate(-0.1));
        assertEquals(-1, SpatialCellKey.toCellCoordinate(-32.0));
        assertEquals(-2, SpatialCellKey.toCellCoordinate(-32.1));
    }

    @Test
    public void testYBandClassification() {
        assertEquals(YBand.DEEP_UNDERGROUND, YBand.fromY(-64.0));
        assertEquals(YBand.DEEP_UNDERGROUND, YBand.fromY(-16.1));

        assertEquals(YBand.UNDERGROUND, YBand.fromY(-16.0));
        assertEquals(YBand.UNDERGROUND, YBand.fromY(0.0));
        assertEquals(YBand.UNDERGROUND, YBand.fromY(61.9));

        assertEquals(YBand.SURFACE, YBand.fromY(62.0));
        assertEquals(YBand.SURFACE, YBand.fromY(100.0));
        assertEquals(YBand.SURFACE, YBand.fromY(191.9));

        assertEquals(YBand.HIGH_ALTITUDE, YBand.fromY(192.0));
        assertEquals(YBand.HIGH_ALTITUDE, YBand.fromY(320.0));
    }

    @Test
    public void testSpatialCellKeyCreationAndEquality() {
        UUID groupOwner = UUID.randomUUID();
        SpatialCellKey key1 = SpatialCellKey.of("minecraft:overworld", 10.0, 70.0, 20.0, groupOwner, GroupRelation.MEMBER);
        SpatialCellKey key2 = SpatialCellKey.of("minecraft:overworld", 15.0, 80.0, 25.0, groupOwner, GroupRelation.MEMBER);
        SpatialCellKey key3 = SpatialCellKey.of("minecraft:the_nether", 10.0, 70.0, 20.0, groupOwner, GroupRelation.MEMBER);

        assertEquals(key1, key2); // 同じセル・Y帯・グループ・立場
        assertNotEquals(key1, key3); // 異なるディメンション

        assertEquals(0, key1.cellX());
        assertEquals(0, key1.cellZ());
        assertEquals(YBand.SURFACE, key1.yBand());
        assertEquals(GroupRelation.MEMBER, key1.relation());
    }
}
