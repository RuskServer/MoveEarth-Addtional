package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.analytics.profiler.ChunkProfilerService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelChunkProfilerMixin {
    @Unique private long moveearth$entityStarted;
    @Unique private ChunkPos moveearth$entityChunk;
    @Unique private long moveearth$scheduledStarted;
    @Unique private ChunkPos moveearth$scheduledChunk;

    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void moveearth$beforeEntityTick(Entity entity, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!ChunkProfilerService.INSTANCE.shouldSample(level)) return;
        moveearth$entityChunk = entity.chunkPosition();
        moveearth$entityStarted = ChunkProfilerService.INSTANCE.begin(level, moveearth$entityChunk);
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void moveearth$afterEntityTick(Entity entity, CallbackInfo ci) {
        if (moveearth$entityStarted == 0L) return;
        ChunkProfilerService.INSTANCE.end((ServerLevel) (Object) this, moveearth$entityChunk,
                ChunkProfilerService.Category.ENTITY, moveearth$entityStarted);
        moveearth$entityStarted = 0L;
        moveearth$entityChunk = null;
    }

    @Inject(method = "tickBlock", at = @At("HEAD"))
    private void moveearth$beforeScheduledBlock(BlockPos pos, Block block, CallbackInfo ci) {
        moveearth$beginScheduled(pos);
    }

    @Inject(method = "tickBlock", at = @At("RETURN"))
    private void moveearth$afterScheduledBlock(BlockPos pos, Block block, CallbackInfo ci) {
        moveearth$endScheduled();
    }

    @Inject(method = "tickFluid", at = @At("HEAD"))
    private void moveearth$beforeScheduledFluid(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        moveearth$beginScheduled(pos);
    }

    @Inject(method = "tickFluid", at = @At("RETURN"))
    private void moveearth$afterScheduledFluid(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        moveearth$endScheduled();
    }

    @Unique
    private void moveearth$beginScheduled(BlockPos pos) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!ChunkProfilerService.INSTANCE.shouldSample(level)) return;
        moveearth$scheduledChunk = new ChunkPos(pos);
        moveearth$scheduledStarted = ChunkProfilerService.INSTANCE.begin(level, moveearth$scheduledChunk);
    }

    @Unique
    private void moveearth$endScheduled() {
        if (moveearth$scheduledStarted == 0L) return;
        ChunkProfilerService.INSTANCE.end((ServerLevel) (Object) this, moveearth$scheduledChunk,
                ChunkProfilerService.Category.SCHEDULED_TICK, moveearth$scheduledStarted);
        moveearth$scheduledStarted = 0L;
        moveearth$scheduledChunk = null;
    }
}
