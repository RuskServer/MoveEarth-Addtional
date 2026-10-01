package com.ruskserver.moveearth_addtional.compat.cbc;

import com.ruskserver.moveearth_addtional.s2.reinforcement.CbcMunitionDamage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CbcMunitionSelectionPolicyTest {
    @Test
    void projectilesBurstsAndNamedMunitionsQualify() {
        assertTrue(CbcMunitionSelectionPolicy.isMunition(true, false, false));
        assertTrue(CbcMunitionSelectionPolicy.isMunition(false, true, false));
        assertTrue(CbcMunitionSelectionPolicy.isMunition(false, false,
                CbcMunitionDamage.classify("grapeshot_burst") != CbcMunitionDamage.Kind.UTILITY));
    }

    @Test
    void mountsAndContraptionsNeverQualify() {
        assertFalse(CbcMunitionSelectionPolicy.isMunition(false, false,
                CbcMunitionDamage.classify("cannon_carriage") != CbcMunitionDamage.Kind.UTILITY));
        assertFalse(CbcMunitionSelectionPolicy.isMunition(false, false,
                CbcMunitionDamage.classify("pitch_contraption") != CbcMunitionDamage.Kind.UTILITY));
    }
}
