package com.ruskserver.moveearth_addtional.mixin;

import com.bmaster.createrns.content.deposit.mining.IMinerHolderBE;
import com.ruskserver.moveearth_addtional.compat.rns.RnsMinerStress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Makes a mining rig's stress depend on what it is mining.
 *
 * <p>Declared rather than injected: the bearing inherits
 * {@code calculateStressApplied} from Create and does not override it, so this
 * method is merged in and overrides the inherited one. Everything that asks
 * what the rig costs goes through it — the network's capacity check, the
 * stressometer and the goggle tooltip — so all three agree without any being
 * touched separately.
 *
 * <p>Create's own version does one thing besides returning: it records the
 * figure on {@code lastStressApplied}, which a network is seeded from when the
 * machine loads and which is written to the chunk. That field belongs to a
 * class two levels up, so it is reached through {@link KineticStressAccess}
 * rather than shadowed — {@code @Shadow} only sees a target's own fields, and
 * asking it for an inherited one stops the mod loading outright.
 */
@Pseudo
@Mixin(targets = "com.bmaster.createrns.content.deposit.mining.contraption.MinerBearingBlockEntity",
        remap = false)
public abstract class RnsMinerBearingStressMixin {

    public float calculateStressApplied() {
        float impact = RnsMinerStress.impactFor(((IMinerHolderBE) (Object) this).getMiner());
        ((KineticStressAccess) this).moveearth$setLastStressApplied(impact);
        return impact;
    }
}
