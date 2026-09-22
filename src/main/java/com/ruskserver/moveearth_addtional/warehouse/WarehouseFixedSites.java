package com.ruskserver.moveearth_addtional.warehouse;

import java.util.List;

/** Footprint minima selected from build/terrain/moveearth_terrain.tar.gz (1024-cell tile). */
public final class WarehouseFixedSites {
    public static final List<Site> SITES = List.of(
            new Site(1, 4722, 1484),
            new Site(2, 2450, 1916),
            new Site(3, 4178, 3548),
            new Site(4, 4690, 4956),
            new Site(5, 3954, 3932),
            new Site(6, 1586, 5596),
            new Site(7, 2450, 5820),
            new Site(8, 2930, 6428)
    );

    private WarehouseFixedSites() { }

    public record Site(int region, int minX, int minZ) { }
}
