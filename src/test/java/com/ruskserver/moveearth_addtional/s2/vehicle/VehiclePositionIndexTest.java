package com.ruskserver.moveearth_addtional.s2.vehicle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehiclePositionIndexTest {
    @Test
    void tracksRegisterMoveAndRemove() {
        VehiclePositionIndex index = new VehiclePositionIndex();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        index.add("overworld", 10L, first);
        index.add("overworld", 10L, second);
        index.add("nether", 10L, UUID.randomUUID());
        assertEquals(List.of(first, second), List.copyOf(index.at("overworld", 10L)));

        index.remove("overworld", 10L, first);
        index.add("overworld", 11L, first);
        assertEquals(Set.of(second), index.at("overworld", 10L));
        assertEquals(Set.of(first), index.at("overworld", 11L));

        index.remove("overworld", 10L, second);
        assertTrue(index.at("overworld", 10L).isEmpty());
        assertTrue(index.at("the_end", 11L).isEmpty());
        index.clear();
        assertTrue(index.at("overworld", 11L).isEmpty());
    }
}
