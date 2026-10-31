package com.ruskserver.moveearth_addtional.s2.provenance;

/** Causal context captured when an explosion is constructed; never guessed from nearby entities. */
public interface ExplosionOrigin {
    Object moveearth$origin();
    boolean moveearth$markRejectionLogged();
    net.minecraft.world.level.Level moveearth$level();
    net.minecraft.world.damagesource.DamageSource moveearth$damageSource();
}
