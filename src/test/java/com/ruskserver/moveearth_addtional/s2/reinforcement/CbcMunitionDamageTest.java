package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(CbcMunitionDamage.Kind.UTILITY, CbcMunitionDamage.classify("smoke_shell"));
    }
}
