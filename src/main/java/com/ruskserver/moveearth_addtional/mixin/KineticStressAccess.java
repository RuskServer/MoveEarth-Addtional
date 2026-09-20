package com.ruskserver.moveearth_addtional.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches Create's record of the stress a machine last applied.
 *
 * <p>Needed because {@code @Shadow} only sees fields declared on the target
 * class itself, and this one lives two classes up the hierarchy from the mining
 * rig whose cost is being changed.
 *
 * <p>Leaving it alone was the tempting shortcut and is wrong: Create seeds a
 * network's running total from this field when a machine loads, and writes it
 * to the chunk as {@code AddedStress}. A rig that reported one figure while
 * recording another would make the network's total drift every time the world
 * reloaded.
 */
@Mixin(KineticBlockEntity.class)
public interface KineticStressAccess {

    @Accessor("lastStressApplied")
    void moveearth$setLastStressApplied(float value);
}
