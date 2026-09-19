package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.analytics.profiler.ChunkProfilerService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BlockEntityChunkProfilerMixin {
    @Shadow @Final private BlockEntity blockEntity;
    @Unique private long moveearth$started;
    @Unique private ServerLevel moveearth$level;
    @Unique private ChunkPos moveearth$chunk;

    @Inject(method = "tick", at = @At("HEAD"))
    private void moveearth$beforeBlockEntityTick(CallbackInfo ci) {
        if (!ChunkProfilerService.INSTANCE.isRunning()) return;
        if (!(blockEntity.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!ChunkProfilerService.INSTANCE.shouldSample(serverLevel)) return;
        BlockPos pos = blockEntity.getBlockPos();
        moveearth$level = serverLevel;
        moveearth$chunk = new ChunkPos(pos);
        moveearth$started = ChunkProfilerService.INSTANCE.begin(serverLevel, moveearth$chunk);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void moveearth$afterBlockEntityTick(CallbackInfo ci) {
        if (moveearth$started == 0L) return;
        ChunkProfilerService.INSTANCE.end(moveearth$level, moveearth$chunk,
                ChunkProfilerService.Category.BLOCK_ENTITY, moveearth$started);
        moveearth$started = 0L;
        moveearth$level = null;
        moveearth$chunk = null;
    }
}
