package com.ruskserver.moveearth_addtional.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryMapChangeTest {
    private static final UUID NATION = new UUID(1L, 2L);

    private record Nation(UUID id, String name, String relation) { }

    private record Core(UUID nation, int x, int z, String state) { }

    @Test
    void repeatedRefreshIsNotAChange() {
        assertFalse(TerritoryMapChange.changes(List.of(core("ACTIVE")), Map.of(NATION, nation("OWN")),
                List.of(core("ACTIVE")), List.of(nation("OWN")), Nation::id));
    }

    @Test
    void firstPacketIsAChange() {
        assertTrue(TerritoryMapChange.changes(null, null,
                List.of(core("ACTIVE")), List.of(nation("OWN")), Nation::id));
    }

    @Test
    void coreStateRelationAndNationCountChangesAreChanges() {
        var cores = List.of(core("ACTIVE"));
        var nations = Map.of(NATION, nation("OWN"));

        assertTrue(TerritoryMapChange.changes(cores, nations,
                List.of(core("FALLEN")), List.of(nation("OWN")), Nation::id));
        assertTrue(TerritoryMapChange.changes(cores, nations,
                List.of(core("ACTIVE")), List.of(nation("HOSTILE")), Nation::id));
        assertTrue(TerritoryMapChange.changes(cores, nations, List.of(core("ACTIVE")),
                List.of(nation("OWN"), new Nation(new UUID(3L, 4L), "Other", "FOREIGN")), Nation::id));
    }

    private static Nation nation(String relation) {
        return new Nation(NATION, "Rusk", relation);
    }

    private static Core core(String state) {
        return new Core(NATION, 3, -4, state);
    }
}
