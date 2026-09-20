package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.rns.RnsDepositGate;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a Create: Rock &amp; Stone ore deposit out of regions it does not
 * belong to.
 *
 * <p>Declining to generate is how a vanilla structure says "not here": every
 * one of them returns an empty generation point when the ground, the biome or
 * the spacing is wrong, and the game is built to expect it. So there is nothing
 * to reroute and nothing to keep in step — the placement grid, the weighted
 * choice within the structure set and the biome test all run exactly as before,
 * and the deposit scanner agrees automatically because it searches for
 * structures the same way the world generates them.
 *
 * <p>That is the whole reason this is one short injection where the equivalent
 * for a mod with its own placement code needed two, plus a way to tell whether
 * they had drifted apart.
 */
@Pseudo
@Mixin(targets = "com.bmaster.createrns.content.deposit.worldgen.DepositStructure", remap = false)
public class RnsDepositStructureMixin {

    /**
     * {@code require = 0} because Rock &amp; Stone is an optional dependency: if
     * an update moves this method the server must still start, with deposits
     * generating as the mod intended. {@code RnsDepositGate.consulted()} reports
     * that the hook did attach, so a gate that silently never runs is visible
     * rather than mistaken for a world that happened not to block anything.
     */
    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true, require = 0)
    private void moveearth$refuseOutsideItsRegion(Structure.GenerationContext context,
                                                  CallbackInfoReturnable<Optional<?>> callback) {
        if (!RnsDepositGate.allows((Structure) (Object) this, context.chunkPos())) {
            callback.setReturnValue(Optional.empty());
        }
    }
}
