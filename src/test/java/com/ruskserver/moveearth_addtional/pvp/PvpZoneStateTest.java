package com.ruskserver.moveearth_addtional.pvp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PvpZoneStateTest {
    @Test
    void derivesZoneStateFromOccupants() {
        assertEquals(PvpZoneState.NEUTRAL, PvpZoneState.fromCounts(0, 0));
        assertEquals(PvpZoneState.RED, PvpZoneState.fromCounts(1, 0));
        assertEquals(PvpZoneState.BLUE, PvpZoneState.fromCounts(0, 2));
        assertEquals(PvpZoneState.CONTESTED, PvpZoneState.fromCounts(1, 1));
    }

    @Test
    void networkIdsRoundTripAndUnknownIdsAreSafe() {
        for (PvpZoneState state : PvpZoneState.values()) {
            assertEquals(state, PvpZoneState.byNetworkId(state.networkId()));
        }
        assertEquals(PvpZoneState.NEUTRAL, PvpZoneState.byNetworkId(-1));
        assertEquals(PvpZoneState.NEUTRAL, PvpZoneState.byNetworkId(999));
    }
}
