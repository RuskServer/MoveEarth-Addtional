package com.ruskserver.moveearth_addtional.territory.data;

import com.ruskserver.moveearth_addtional.territory.domain.TerritoryCore;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryPosition;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryCoreRegistryTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void registeringAnotherCoreAtSamePositionReplacesStaleEntry() {
        TerritoryCoreRegistry registry = new TerritoryCoreRegistry();
        TerritoryCore first = core(UUID.randomUUID(), UUID.randomUUID(), 10, 64, 20);
        TerritoryCore replacement = core(UUID.randomUUID(), UUID.randomUUID(), 10, 64, 20);

        registry.register(first);
        registry.register(replacement);

        assertEquals(1, registry.size());
        assertEquals(replacement, registry.allCores().iterator().next());
    }

    @Test
    void unregisterAtOnlyRemovesMatchingDimensionAndPosition() {
        TerritoryCoreRegistry registry = new TerritoryCoreRegistry();
        TerritoryCore core = core(UUID.randomUUID(), UUID.randomUUID(), 10, 64, 20);
        registry.register(core);

        assertFalse(registry.unregisterAt("minecraft:the_nether", 10, 64, 20));
        assertTrue(registry.unregisterAt(OVERWORLD, 10, 64, 20));
        assertEquals(0, registry.size());
    }

    private static TerritoryCore core(UUID coreId, UUID ownerId, int x, int y, int z) {
        return new TerritoryCore(
                coreId,
                TerritoryOwnerId.of(ownerId),
                new TerritoryPosition(OVERWORLD, x + 0.5D, y + 0.5D, z + 0.5D),
                true
        );
    }
}
