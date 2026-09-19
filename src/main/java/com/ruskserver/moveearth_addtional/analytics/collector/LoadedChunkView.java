package com.ruskserver.moveearth_addtional.analytics.collector;

import net.minecraft.world.level.chunk.LevelChunk;

/** Safe bridge exposed by the ChunkMap mixin without referencing a mixin class directly. */
public interface LoadedChunkView {
    Iterable<LevelChunk> moveearth$getTickingChunks();
}
