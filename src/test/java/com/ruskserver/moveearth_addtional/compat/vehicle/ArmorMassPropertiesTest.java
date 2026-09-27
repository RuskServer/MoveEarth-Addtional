package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementMaterial;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArmorMassPropertiesTest {
    @Test void materialsIncreaseWeightWithoutChangingHp() {
        double previous = 0;
        for (var material : ReinforcementMaterial.values()) {
            assertTrue(material.addedMass() > previous);
            previous = material.addedMass();
        }
        assertEquals(1.0, ReinforcementMaterial.IRON.addedMass());
        assertEquals(128, ReinforcementMaterial.IRON.maxDurability());
    }

    @Test void singleCubeHasIntrinsicInertia() {
        var armor = new ArmorMassProperties();
        armor.add(3, .5, .5, .5);
        assertEquals(3, armor.mass());
        assertEquals(new Vector3d(.5, .5, .5), armor.center());
        assertEquals(.5, armor.inertia().m00(), 1e-10);
    }

    @Test void armorTotalsExceedOverlayLimitWithoutTruncation() {
        var armor = new ArmorMassProperties();
        for (int i = 0; i < 9000; i++) armor.add(1, i % 100 + .5, i / 100 + .5, .5);
        assertEquals(9000, armor.mass());
        assertEquals(10000, armor.merge(1000, new Vector3d(), new Matrix3d(), 0, 0, 0));
        assertTrue(Double.isFinite(armor.inertia().determinant()));
    }

    @Test void separatedArmorAffectsRotationNotJustTotalMass() {
        var armor = new ArmorMassProperties();
        armor.add(1, -2, 0, 0);
        armor.add(1, 2, 0, 0);
        assertEquals(new Vector3d(), armor.center());
        assertEquals(1.0 / 3, armor.inertia().m00(), 1e-10);
        assertEquals(8 + 1.0 / 3, armor.inertia().m11(), 1e-10);
        assertEquals(armor.inertia().m11(), armor.inertia().m22());
    }

    @Test void mergeShiftsComAndPreservesSymmetricTensor() {
        var armor = new ArmorMassProperties();
        armor.add(2, 2, 2, 0);
        var center = new Vector3d();
        var tensor = new Matrix3d().zero();
        assertEquals(4, armor.merge(2, center, tensor, 0, 0, 0));
        assertEquals(new Vector3d(1, 1, 0), center);
        assertEquals(4 + 1.0 / 3, tensor.m00(), 1e-10);
        assertEquals(-4, tensor.m01(), 1e-10);
        assertEquals(tensor.m01(), tensor.m10());
    }

    @Test void repeatedFreshBaseUpdatesDoNotAccumulateArmor() {
        var armor = new ArmorMassProperties();
        armor.add(1, 0, 0, 0);
        for (int tick = 0; tick < 100; tick++) {
            assertEquals(11, armor.merge(10, new Vector3d(), new Matrix3d(), 0, 0, 0));
        }
        // Recreating the cache on load/assembly produces the same result, not double weight.
        var reloaded = new ArmorMassProperties();
        reloaded.add(1, 0, 0, 0);
        assertEquals(11, reloaded.merge(10, new Vector3d(), new Matrix3d(), 0, 0, 0));
    }

    @Test void emptyArmorAndLargePlotOffsetsAreSafe() {
        var center = new Vector3d(30_000_000.5, 64.5, -30_000_000.5);
        var original = new Vector3d(center);
        var tensor = new Matrix3d();
        assertEquals(10, new ArmorMassProperties().merge(10, center, tensor, 0, 0, 0));
        assertEquals(original, center);
        var armor = new ArmorMassProperties();
        armor.add(10, .5, .5, .5);
        assertEquals(20, armor.merge(10, center, tensor, 30_000_000, 64, -30_000_001));
        assertEquals(original, center);
        assertTrue(Double.isFinite(tensor.determinant()));
    }
}
