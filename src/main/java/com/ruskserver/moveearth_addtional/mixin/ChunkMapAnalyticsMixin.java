package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.analytics.collector.LoadedChunkView;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.List;

@Mixin(ChunkMap.class)
public abstract class ChunkMapAnalyticsMixin implements LoadedChunkView {
    @Shadow
    protected abstract Iterable<ChunkHolder> getChunks();

    @Override
    public Iterable<LevelChunk> moveearth$getTickingChunks() {
        List<LevelChunk> chunks = new ArrayList<>();
        for (ChunkHolder holder : getChunks()) {
            LevelChunk chunk = holder.getTickingChunk();
            if (chunk != null) chunks.add(chunk);
        }
        return chunks;
    }
}
