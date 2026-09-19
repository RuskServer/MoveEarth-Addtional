package com.ruskserver.moveearth_addtional.s2.reinforcement;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BreachRepairTest {
    @Test
    void replacingDestroyedBlockDoesNotEraseCooldownAndSaveRetainsIt() {
        var data = new RepairCooldowns();
        long pos = 1234L;
        data.hit(pos, 100, 1200);
        var loaded = new RepairCooldowns();
        data.snapshot().forEach(loaded::restore);
        assertEquals(1300, loaded.until(pos, 200));
        var replacement = ReinforcementEntry.pending(ReinforcementMaterial.IRON, 200,
                loaded.until(pos, 200));
        assertFalse(replacement.advance(1899).enabled());
        assertTrue(replacement.advance(1900).enabled());
        assertEquals(2000, loaded.until(pos, 2000));
        assertTrue(loaded.expire(2000));
        assertTrue(loaded.snapshot().isEmpty());
    }

    @Test
    void laterHitsExtendButNeverShortenWait() {
        var data = new RepairCooldowns();
        long pos = 1L;
        data.hit(pos, 100, 1200);
        data.hit(pos, 200, 1200);
        data.hit(pos, 300, 10);
        assertEquals(1400, data.until(pos, 300));
        assertEquals(300, data.until(2L, 300));
    }

    @Test
    void disabledWaitDoesNotCreateCooldown() {
        var data = new RepairCooldowns();
        data.hit(1L, 100, 0);
        assertEquals(100, data.until(1L, 100));
    }

    @Test
    void hitDuringConstructionStopsAutomaticHealing() {
        var building = ReinforcementEntry.pending(ReinforcementMaterial.IRON, 0).advance(1200);
        var hit = building.damage(10);
        assertTrue(hit.enabled());
        assertEquals(building.durability() - 10, hit.durability());
        assertEquals(hit, hit.advance(10000));
        assertTrue(hit.repair().durability() > hit.durability());
        assertEquals(building, building.damage(0));
    }
}
