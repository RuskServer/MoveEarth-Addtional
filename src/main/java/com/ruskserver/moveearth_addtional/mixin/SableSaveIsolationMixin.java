package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.UUID;

/**
 * Keeps one sub-level that cannot be saved from losing every other one.
 *
 * <p>Sable's {@code saveAll} writes each loaded sub-level back to its holding file in one loop, and a
 * sub-level is only in memory while loaded: loading takes it out of the file. A single sub-level whose
 * serialisation threw, such as one left half-assembled by a crash with no blocks and so no centre of
 * mass, ended the loop. At shutdown that left every sub-level after it unwritten and the rest of the
 * level's save undone, and the next start found the server's vehicles gone. Each sub-level's save is
 * now its own: a failure is logged with what failed and the loop goes on.
 */
@Pseudo
@Mixin(value = SubLevelHoldingChunkMap.class, remap = false)
public abstract class SableSaveIsolationMixin {
    @WrapOperation(method = "saveAll", remap = false, at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunkMap;moveAndSaveSubLevel(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Lnet/minecraft/world/level/ChunkPos;Ljava/util/List;)V",
            remap = false))
    private void moveearth$saveEachOnItsOwn(SubLevelHoldingChunkMap map, ServerSubLevel subLevel, ChunkPos chunk,
                                           List<UUID> dependencies, Operation<Void> original) {
        try {
            original.call(map, subLevel, chunk, dependencies);
        } catch (RuntimeException failure) {
            String id;
            try {
                id = subLevel.getUniqueId() + " (" + subLevel.getName() + ")";
            } catch (Throwable unreadable) {
                id = "<unreadable>";
            }
            Moveearth_addtional.LOGGER.error("[MoveEarth] Sable sub-level {} at chunk {} could not be saved and was "
                    + "skipped; every other sub-level is still saved", id, chunk, failure);
        }
    }
}
