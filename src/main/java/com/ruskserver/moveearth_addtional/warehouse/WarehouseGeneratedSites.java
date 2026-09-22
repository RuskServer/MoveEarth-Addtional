package com.ruskserver.moveearth_addtional.warehouse;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.region.RegionResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Discovers completed vanilla structure starts and makes them visible to protection and encounters. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WarehouseGeneratedSites {
    private WarehouseGeneratedSites() { }

    @SubscribeEvent
    public static void onLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)
                || !(event.getChunk() instanceof LevelChunk chunk)) return;
        // Chunk loading may complete off-thread. SavedData must only be touched on the server thread.
        level.getServer().execute(() -> {
            if (level.getServer().isStopped()) return;
            for (var entry : chunk.getAllStarts().entrySet()) {
                if (!(entry.getKey() instanceof WarehouseStructure) || !entry.getValue().isValid()) continue;
                var box = entry.getValue().getBoundingBox();
                int region = RegionResolver.regionAt(box.minX(), box.minZ());
                if (region <= 0) continue;
                var site = new WarehouseSites.Site(region, level.dimension().location(),
                        new net.minecraft.core.BlockPos(box.minX(), box.minY(), box.minZ()));
                if (WarehouseSites.get(level.getServer()).add(site)) {
                    Moveearth_addtional.LOGGER.info("Registered generated warehouse in region {} at {}",
                            region, site.min());
                }
            }
        });
    }
}
