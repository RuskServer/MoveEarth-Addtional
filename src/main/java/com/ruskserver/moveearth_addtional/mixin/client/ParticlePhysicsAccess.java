package com.ruskserver.moveearth_addtional.mixin.client;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticlePhysicsAccess {
    @Accessor("hasPhysics") boolean moveearth$hasPhysics();
    @Accessor("hasPhysics") void moveearth$setPhysics(boolean enabled);
    @Accessor("age") int moveearth$age();
    @Accessor("x") double moveearth$positionX();
    @Accessor("y") double moveearth$positionY();
    @Accessor("z") double moveearth$positionZ();
}
