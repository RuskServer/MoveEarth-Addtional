package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleRepairPolicyTest {
    private VehicleRepairPolicy.Result repair(int hp, long now, VehicleRepairPolicy.State state) {
        return VehicleRepairPolicy.repair(hp, 600, now, state, 5, 30, 50, 100, 40);
    }

    @Test void emergencyRepairClampsAtHalfAndDoesNotConsumeCooldownWhenBlocked() {
        var state = VehicleRepairPolicy.State.EMPTY.hit(100, 2400);
        var first = repair(298, 100, state);
        assertEquals(2, first.gain());
        assertTrue(first.emergency());
        var blocked = repair(300, 200, first.state());
        assertEquals(0, blocked.gain());
        assertEquals(2300, blocked.waitTicks());
        assertEquals(first.state(), blocked.state());
    }

    @Test void multipleRepairersShareCooldownAndHitsNeverResetIt() {
        var first = repair(100, 100, VehicleRepairPolicy.State.EMPTY.hit(100, 2400));
        assertEquals(5, first.gain());
        var hit = first.state().hit(101, 2400);
        assertEquals(200, hit.nextRepairAt());
        assertEquals(0, repair(105, 100, hit).gain());
        assertEquals(0, repair(105, 199, hit).gain());
        assertEquals(5, repair(105, 200, hit).gain());
    }

    @Test void fullRepairsResumeAtQuietDeadlineAndCannotOverheal() {
        var state = VehicleRepairPolicy.State.EMPTY.hit(100, 2400);
        assertEquals(0, repair(599, 2499, state).gain());
        var normal = repair(599, 2500, state);
        assertFalse(normal.emergency());
        assertEquals(1, normal.gain());
        assertEquals(2540, normal.state().nextRepairAt());
        assertEquals(0, repair(600, 2600, normal.state()).gain());
    }

    @Test void disabledCoreRequiresQuietBeforeRecovery() {
        var state = VehicleRepairPolicy.State.EMPTY.hit(10, 2400);
        assertEquals(0, repair(0, 2409, state).gain());
        assertEquals(30, repair(0, 2410, state).gain());
    }

    @Test void persistedDeadlinesRemainEffectiveAndLegacyStateWorks() {
        var state = repair(100, 100, VehicleRepairPolicy.State.EMPTY.hit(100, 2400)).state();
        var restored = new VehicleRepairPolicy.State(state.combatUntil(), state.nextRepairAt());
        assertEquals(0, repair(105, 150, restored).gain());
        assertTrue(repair(105, 200, restored).emergency());
        assertEquals(30, repair(100, 100, VehicleRepairPolicy.State.EMPTY).gain());
    }
}
