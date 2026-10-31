package com.ruskserver.moveearth_addtional.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.ruskserver.moveearth_addtional.s2.provenance.ActionContext;
import com.ruskserver.moveearth_addtional.s2.provenance.ExplosionOrigin;
import com.ruskserver.moveearth_addtional.s2.provenance.WorldMutationGuard;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class WorldMutationExplosionMixin implements ExplosionOrigin {
    @Shadow @Final private Level level;
    @Shadow @Final private net.minecraft.world.damagesource.DamageSource damageSource;
    @Override public net.minecraft.world.damagesource.DamageSource moveearth$damageSource() { return damageSource; }
    @Override public Level moveearth$level() { return level; }
    @Unique private Object moveearth$causalOrigin;
    @Unique private boolean moveearth$rejectionLogged;
    @Override public boolean moveearth$markRejectionLogged() {
        if (moveearth$rejectionLogged) return false;
        moveearth$rejectionLogged = true;
        return true;
    }
    @Inject(method = "<init>", at = @At("RETURN"))
    private void moveearth$captureExactOrigin(CallbackInfo callback) {
        if (moveearth$causalOrigin == null) moveearth$causalOrigin = WorldMutationGuard.captureExplosionOrigin(
                (Explosion) (Object) this, ActionContext.current());
    }
    @Override public Object moveearth$origin() { return moveearth$causalOrigin; }

    @WrapMethod(method = "explode")
    private void moveearth$traceBlast(Operation<Void> original) {
        try (var scope = ActionContext.push(WorldMutationGuard.explosionOrigin((Explosion) (Object) this))) {
            original.call();
        }
    }
    @WrapMethod(method = "finalizeExplosion")
    private void moveearth$traceAftermath(boolean particles, Operation<Void> original) {
        try (var scope = ActionContext.push(WorldMutationGuard.explosionOrigin((Explosion) (Object) this))) {
            original.call(particles);
        }
    }
}
