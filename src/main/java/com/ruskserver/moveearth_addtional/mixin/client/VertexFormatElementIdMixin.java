package com.ruskserver.moveearth_addtional.mixin.client;

import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moves a clashing vertex format element aside instead of refusing to start.
 *
 * <p>Element ids are a shared table of thirty-two slots. Vanilla takes the first
 * six and NeoForge hands mods {@link VertexFormatElement#findNextId()} for the
 * rest, but a mod that writes a number in by hand claims that slot against
 * everything else, and the second claimant gets an exception out of a static
 * initialiser -- which is fatal twice over, because the class it was
 * initialising stays poisoned for the rest of the run.
 *
 * <p>Seen with Immersive Ballistic, whose {@code BlockFragmentRenderType} asks
 * for slot ten, and something loaded earlier already had it. Rather than pick a
 * side between two mods we do not own, any clash is given the next free slot.
 *
 * <p>Safe because the id is an internal handle: a caller uses the element this
 * returns, and the id only ever appears again through that element, in the mask
 * a {@link com.mojang.blaze3d.vertex.VertexFormat} builds from it. The mod that
 * claimed the slot first keeps it, so nothing that already worked moves.
 *
 * <p>It is still a patch over someone else's bug. The fix belongs upstream, in
 * the mod that hardcoded the number.
 */
@Mixin(VertexFormatElement.class)
public class VertexFormatElementIdMixin {

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private static void moveearth$relocateClashingId(
            int id, int index, VertexFormatElement.Type type, VertexFormatElement.Usage usage,
            int count, CallbackInfoReturnable<VertexFormatElement> callback) {
        VertexFormatElement taken = VertexFormatElement.byId(id);
        if (taken == null) {
            return;
        }
        int free = VertexFormatElement.findNextId();
        Moveearth_addtional.LOGGER.warn(
                "Vertex format element id {} is already {}; moving the new {},{},{} element to id {}."
                        + " A mod hardcoded an element id instead of calling findNextId().",
                id, taken, count, usage, type, free);
        // Re-entering register is why this injects at HEAD and not elsewhere:
        // the slot it is given is free, so this same check falls straight
        // through and the vanilla body does the registering.
        callback.setReturnValue(VertexFormatElement.register(free, index, type, usage, count));
    }
}
