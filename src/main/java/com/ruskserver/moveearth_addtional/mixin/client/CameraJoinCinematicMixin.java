package com.ruskserver.moveearth_addtional.mixin.client;

import com.ruskserver.moveearth_addtional.client.cinematic.JoinCinematic;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Places the camera for the join cinematic ({@link JoinCinematic}) after vanilla has set it up. */
@Mixin(Camera.class)
public abstract class CameraJoinCinematicMixin {
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(Vec3 position);

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void moveearth$joinCinematic(BlockGetter level, Entity entity, boolean detachedView,
                                         boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        JoinCinematic.CameraPose pose = JoinCinematic.pose(entity, partialTick);
        if (pose == null) return;
        setRotation(pose.yaw(), pose.pitch());
        setPosition(pose.position());
        detached = pose.detached();
    }
}
