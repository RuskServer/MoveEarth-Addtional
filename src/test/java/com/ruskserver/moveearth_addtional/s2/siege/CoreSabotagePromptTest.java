package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.s2.siege.CoreSabotagePrompt.Charge;
import com.ruskserver.moveearth_addtional.s2.siege.CoreSabotagePrompt.Kind;
import com.ruskserver.moveearth_addtional.s2.siege.CoreSabotagePrompt.Viewer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoreSabotagePromptTest {
    private static Viewer attacker(boolean near, int tnt) { return new Viewer(false, true, true, near, tnt); }
    private static Viewer defender(boolean tool, boolean near) { return new Viewer(true, true, tool, near, 0); }

    @Test
    void attackerIsGuidedUntilPlantIsPossible() {
        assertEquals(Kind.UNAVAILABLE, CoreSabotagePrompt.decide(attacker(true, 4), false, null).kind());
        assertEquals(Kind.TOO_FAR, CoreSabotagePrompt.decide(attacker(false, 4), true, null).kind());
        CoreSabotagePrompt missing = CoreSabotagePrompt.decide(attacker(true, 2), true, null);
        assertEquals(Kind.PLANT_NEEDS_TNT, missing.kind());
        assertEquals(2, missing.tnt());
        assertEquals(Kind.PLANT, CoreSabotagePrompt.decide(attacker(true, 4), true, null).kind());
    }

    @Test
    void hostileViewersWithoutWelderSeeNothing() {
        Viewer gunner = new Viewer(false, true, false, true, 4);
        assertEquals(Kind.NONE, CoreSabotagePrompt.decide(gunner, true, null).kind());
        assertEquals(Kind.NONE, CoreSabotagePrompt.decide(gunner, true,
                new Charge(true, 0, false, false, false, 0)).kind());
    }

    @Test
    void plantingShowsProgressOnlyToThePlanter() {
        Charge charge = new Charge(false, 100, true, false, false, 0);
        CoreSabotagePrompt own = CoreSabotagePrompt.decide(attacker(true, 4), true, charge);
        assertEquals(Kind.PLANTING, own.kind());
        assertEquals(0.25F, own.progress());
        assertEquals(15, own.seconds());
        CoreSabotagePrompt ally = CoreSabotagePrompt.decide(attacker(true, 4), true,
                new Charge(false, 100, false, false, false, 0));
        assertEquals(Kind.ALLY_PLANTING, ally.kind());
        assertEquals(0F, ally.progress());
    }

    @Test
    void defendersAreWarnedAndDirectedToDefuse() {
        Charge planting = new Charge(false, 0, false, false, false, 0);
        assertEquals(Kind.ENEMY_PLANTING, CoreSabotagePrompt.decide(defender(false, false), false, planting).kind());
        Charge armed = new Charge(true, 200, false, false, false, 0);
        assertEquals(Kind.DEFUSE_NEEDS_TOOL, CoreSabotagePrompt.decide(defender(false, true), false, armed).kind());
        assertEquals(Kind.TOO_FAR, CoreSabotagePrompt.decide(defender(true, false), false, armed).kind());
        CoreSabotagePrompt ready = CoreSabotagePrompt.decide(defender(true, true), false, armed);
        assertEquals(Kind.DEFUSE, ready.kind());
        assertEquals(30, ready.seconds());
    }

    @Test
    void defuseProgressBelongsToTheActiveDefender() {
        CoreSabotagePrompt own = CoreSabotagePrompt.decide(defender(true, true), false,
                new Charge(true, 200, false, true, true, 50));
        assertEquals(Kind.DEFUSING, own.kind());
        assertEquals(0.5F, own.progress());
        assertEquals(Kind.ALLY_DEFUSING, CoreSabotagePrompt.decide(defender(true, true), false,
                new Charge(true, 200, false, true, false, 50)).kind());
    }

    @Test
    void friendlyCoreWithoutChargeAndIncapableViewersStayQuiet() {
        assertEquals(Kind.NONE, CoreSabotagePrompt.decide(defender(true, true), false, null).kind());
        Viewer downed = new Viewer(false, false, true, true, 4);
        assertEquals(Kind.NONE, CoreSabotagePrompt.decide(downed, true, null).kind());
    }
}
