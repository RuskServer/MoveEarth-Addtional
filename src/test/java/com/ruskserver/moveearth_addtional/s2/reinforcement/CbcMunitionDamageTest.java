package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CbcMunitionDamageTest {
    @Test
    void classifiesOfficialCbc121EntityIds() {
        assertEquals(CbcMunitionDamage.Kind.SHOT, CbcMunitionDamage.classify("shot"));
        assertEquals(CbcMunitionDamage.Kind.AP_SHOT, CbcMunitionDamage.classify("ap_shot"));
        assertEquals(CbcMunitionDamage.Kind.HE_SHELL, CbcMunitionDamage.classify("he_shell"));
        assertEquals(CbcMunitionDamage.Kind.AP_SHELL, CbcMunitionDamage.classify("ap_shell"));
        assertEquals(CbcMunitionDamage.Kind.MORTAR, CbcMunitionDamage.classify("drop_mortar_shell"));
        assertEquals(CbcMunitionDamage.Kind.FRAGMENTATION, CbcMunitionDamage.classify("shrapnel_burst"));
        assertEquals(CbcMunitionDamage.Kind.AUTOCANNON, CbcMunitionDamage.classify("ap_autocannon"));
        assertEquals(CbcMunitionDamage.Kind.MACHINE_GUN, CbcMunitionDamage.classify("machine_gun_bullet"));
        assertEquals(CbcMunitionDamage.Kind.UTILITY, CbcMunitionDamage.classify("smoke_shell"));
    }

    @Test
    void identifiesMunitionsWhoseTerrainTransformUsesAProtectedBlastArea() {
        assertTrue(CbcMunitionDamage.usesBlastArea("he_shell"));
        assertTrue(CbcMunitionDamage.usesBlastArea("mortar_stone"));
        assertTrue(CbcMunitionDamage.usesBlastArea("flak_autocannon"));
        assertFalse(CbcMunitionDamage.usesBlastArea("shot"));
        assertFalse(CbcMunitionDamage.usesBlastArea("ap_autocannon"));
        assertFalse(CbcMunitionDamage.usesBlastArea("flak_burst"));
        assertFalse(CbcMunitionDamage.usesBlastArea("shrapnel_burst"));
    }

    @Test
    void onlyHeavyMunitionsCanDamageStrategicCores() {
        assertTrue(CbcMunitionDamage.canDamageCore(CbcMunitionDamage.Kind.SHOT));
        assertTrue(CbcMunitionDamage.canDamageCore(CbcMunitionDamage.Kind.HE_SHELL));
        assertFalse(CbcMunitionDamage.canDamageCore(CbcMunitionDamage.Kind.AUTOCANNON));
        assertFalse(CbcMunitionDamage.canDamageCore(CbcMunitionDamage.Kind.MACHINE_GUN));
        assertFalse(CbcMunitionDamage.canDamageCore(CbcMunitionDamage.Kind.FRAGMENTATION));
        assertFalse(CbcMunitionDamage.canDamageCore(CbcMunitionDamage.Kind.UTILITY));
    }

    @Test
    void scalesOnlyHeavyProjectileCoreDamage() {
        assertEquals(144, CbcMunitionDamage.coreDamage(CbcMunitionDamage.Kind.AP_SHOT, 96, 1.5D));
        assertEquals(0, CbcMunitionDamage.coreDamage(CbcMunitionDamage.Kind.AUTOCANNON, 3, 50.0D));
        assertEquals(0, CbcMunitionDamage.coreDamage(CbcMunitionDamage.Kind.MACHINE_GUN, 1, 50.0D));
        assertEquals(0, CbcMunitionDamage.coreDamage(CbcMunitionDamage.Kind.HE_SHELL, 40, 0.0D));
    }
}
