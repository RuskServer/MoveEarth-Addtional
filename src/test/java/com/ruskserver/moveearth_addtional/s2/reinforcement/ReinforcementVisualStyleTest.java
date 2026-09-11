package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReinforcementVisualStyleTest {
    @Test
    void materialsHaveDistinctHealthySurfaceColors() {
        var copper = ReinforcementVisualStyle.forEntry(
                ReinforcementMaterial.COPPER, 64, true, false, 0L);
        var diamond = ReinforcementVisualStyle.forEntry(
                ReinforcementMaterial.DIAMOND, 320, true, false, 0L);
        assertNotEquals(copper.red(), diamond.red());
        assertNotEquals(copper.blue(), diamond.blue());
    }

    @Test
    void damageMakesPassiveCoatingStrongerAndRedder() {
        var healthy = ReinforcementVisualStyle.forEntry(
                ReinforcementMaterial.IRON, 128, true, false, 0L);
        var damaged = ReinforcementVisualStyle.forEntry(
                ReinforcementMaterial.IRON, 32, true, false, 0L);
        assertTrue(damaged.red() > healthy.red());
        assertTrue(damaged.alpha() > healthy.alpha());
    }

    @Test
    void detailedModeIsMoreVisibleThanPassiveMode() {
        var passive = ReinforcementVisualStyle.forEntry(
                ReinforcementMaterial.GOLD, 192, true, false, 0L);
        var detailed = ReinforcementVisualStyle.forEntry(
                ReinforcementMaterial.GOLD, 192, true, true, 0L);
        assertTrue(detailed.alpha() > passive.alpha());
    }
}
