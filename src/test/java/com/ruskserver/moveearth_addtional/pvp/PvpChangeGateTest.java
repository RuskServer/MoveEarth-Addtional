package com.ruskserver.moveearth_addtional.pvp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PvpChangeGateTest {
    @Test void sendsFirstValueThenOnlyChanges() {
        PvpChangeGate<String> gate = new PvpChangeGate<>(0);
        assertTrue(gate.shouldSend("a", 0));
        for (int tick = 1; tick < 200; tick++) assertFalse(gate.shouldSend("a", tick));
        assertTrue(gate.shouldSend("b", 200));
        assertFalse(gate.shouldSend("b", 201));
        assertTrue(gate.shouldSend("a", 202));
    }

    @Test void keepAliveResendsUnchangedValue() {
        PvpChangeGate<String> gate = new PvpChangeGate<>(100);
        assertTrue(gate.shouldSend("a", 0));
        assertFalse(gate.shouldSend("a", 99));
        assertTrue(gate.shouldSend("a", 100));
        assertFalse(gate.shouldSend("a", 150));
        assertTrue(gate.shouldSend("b", 151), "a change does not wait for the keep-alive");
        assertFalse(gate.shouldSend("b", 250));
        assertTrue(gate.shouldSend("b", 251));
    }

    @Test void resetForcesNextSend() {
        PvpChangeGate<String> gate = new PvpChangeGate<>(0);
        assertTrue(gate.shouldSend("a", 0));
        gate.reset();
        assertTrue(gate.shouldSend("a", 1));
    }

    @Test void hudCountdownSendsOncePerDisplayedSecond() {
        record Hud(int red, int blue, int seconds, String hill) {}
        PvpChangeGate<Hud> gate = new PvpChangeGate<>(0);
        int sends = 0;
        // 10 seconds of a neutral hill: ticksLeft counts down every tick.
        for (int tick = 0, ticksLeft = 11999; tick < 200; tick++, ticksLeft--) {
            if (gate.shouldSend(new Hud(0, 0, ticksLeft / 20, "neutral"), tick)) sends++;
        }
        assertEquals(10, sends, "20 Hz countdown collapses to the 1 Hz the client can show");
    }

    @Test void nullKeysAreSupported() {
        PvpChangeGate<String> gate = new PvpChangeGate<>(0);
        assertTrue(gate.shouldSend(null, 0));
        assertFalse(gate.shouldSend(null, 1));
        assertTrue(gate.shouldSend("a", 2));
    }
}
