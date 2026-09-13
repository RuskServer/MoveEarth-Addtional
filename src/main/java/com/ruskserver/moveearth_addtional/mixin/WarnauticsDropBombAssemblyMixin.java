package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.warnautics.WarnauticsBombSavedData;
import dev.ryanhcode.sable.api.block.BlockSubLevelAssemblyListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Keeps bomb ownership attached to the block when Sable moves it into or between plots. */
@Pseudo
@Mixin(targets = "com.cbc_more_content.block.DropBombBlock", remap = false)
public abstract class WarnauticsDropBombAssemblyMixin implements BlockSubLevelAssemblyListener {
    @Override
    public void afterMove(ServerLevel sourceLevel, ServerLevel destinationLevel, BlockState state,
                          BlockPos sourcePos, BlockPos destinationPos) {
        WarnauticsBombSavedData.move(sourceLevel, destinationLevel, sourcePos, destinationPos);
    }
}
