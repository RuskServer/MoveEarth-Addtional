package com.ruskserver.moveearth_addtional.mixin;

import com.bmaster.createrns.content.deposit.mining.IDepositBlockMiner;
import com.ruskserver.moveearth_addtional.compat.rns.RnsMinerStress;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells Create to recompute when the rig starts working something else.
 *
 * <p>A stress impact that varies is useless on its own: Create asks a machine
 * what it costs when its network changes and then remembers the answer, so a
 * rig that moved from coal to diamond would keep drawing the coal figure
 * forever. Nothing in Rock &amp; Stone raises this flag, because until now the
 * answer never changed.
 *
 * <p>Setting {@code networkDirty} is how Create is asked to look again; its own
 * tick sees the flag, updates the network and clears it. Comparing first means
 * the flag is raised only when the figure has actually moved, so a rig sitting
 * on one deposit costs nothing extra.
 *
 * <p>Without this the mistake would be invisible in the worst way: the goggle
 * tooltip reads the live value and would show the new cost while the network
 * carried on with the old one.
 *
 * <p>Runs on both sides on purpose. Raising the flag is idempotent, and the
 * client keeps its own view of the network for the stressometer, so letting it
 * recompute too is what keeps the readout matching the server.
 */
@Pseudo
@Mixin(targets = "com.bmaster.createrns.content.deposit.mining.behaviour.MiningBehaviour",
        remap = false)
public abstract class RnsMiningBehaviourStressMixin {

    @Shadow
    protected KineticBlockEntity kBE;

    @Unique
    private float moveearth$lastImpact = Float.NaN;

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void moveearth$markNetworkWhenLoadChanges(CallbackInfo callback) {
        if (!(this instanceof IDepositBlockMiner miner) || kBE == null) {
            return;
        }
        float impact = RnsMinerStress.impactFor(miner);
        // Only when it has actually moved: a rig sitting on one deposit would
        // otherwise ask the network to recompute every tick for no change.
        if (Float.isNaN(moveearth$lastImpact) || Math.abs(impact - moveearth$lastImpact) > 0.01F) {
            moveearth$lastImpact = impact;
            kBE.networkDirty = true;
        }
    }
}
