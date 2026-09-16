package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.aeronautics.HingeRotaryConstraintHandle;
import com.ruskserver.moveearth_addtional.compat.aeronautics.SwivelConstraintCompatibility;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Replaces supported Create: Simulated rotary constraints with the same
 * five-locked-axis generic constraint shape used by Absolute Kinematics hinges.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.swivel_bearing.SwivelBearingBlockEntity", remap = false)
public abstract class SwivelBearingConstraintMixin implements BlockEntitySubLevelActor {
    private static final Set<ConstraintJointAxis> MOVEARTH$HINGE_LOCKED_AXES = Set.of(
            ConstraintJointAxis.LINEAR_X,
            ConstraintJointAxis.LINEAR_Y,
            ConstraintJointAxis.LINEAR_Z,
            ConstraintJointAxis.ANGULAR_X,
            ConstraintJointAxis.ANGULAR_Z
    );

    @Shadow
    public abstract void updateServoCoefficients();

    /**
     * Simulated normally updates the motor through the moving plate actor. AK
     * also gives the joint base its own physics callback, which keeps the
     * servo alive when the cross-sub-level plate-to-parent lookup is late.
     */
    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (moveearth$usesHingeDrive()) {
            updateServoCoefficients();
        }
    }

    /**
     * A base in the main level is not part of a sub-level actor list. Refresh
     * once on the server tick as a fallback; the regular plate actor still
     * supplies per-substep updates whenever its parent link is available.
     */
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void moveearth$refreshServoFromMainLevel(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() != null && !self.getLevel().isClientSide && moveearth$usesHingeDrive()) {
            updateServoCoefficients();
        }
    }

    @Redirect(
            method = "attachConstraints",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;addConstraint(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Ldev/ryanhcode/sable/api/physics/constraint/PhysicsConstraintConfiguration;)Ldev/ryanhcode/sable/api/physics/constraint/PhysicsConstraintHandle;"
            ),
            require = 0
    )
    private PhysicsConstraintHandle moveearth$useHingeConstraint(
            PhysicsPipeline pipeline,
            PhysicsPipelineBody body1,
            PhysicsPipelineBody body2,
            PhysicsConstraintConfiguration<?> originalConfiguration
    ) {
        if (!(originalConfiguration instanceof RotaryConstraintConfiguration rotary)
                || !SwivelConstraintCompatibility.enabledFor(
                        rotary.normal1().x(), rotary.normal1().y(), rotary.normal1().z(),
                        rotary.normal2().x(), rotary.normal2().y(), rotary.normal2().z()
                )) {
            return moveearth$addOriginal(pipeline, body1, body2, originalConfiguration);
        }

        try {
            GenericConstraintConfiguration hinge = new GenericConstraintConfiguration(
                    rotary.pos1(),
                    rotary.pos2(),
                    moveearth$frameWithLocalYAlong(rotary.normal1()),
                    moveearth$frameWithLocalYAlong(rotary.normal2()),
                    MOVEARTH$HINGE_LOCKED_AXES
            );
            GenericConstraintHandle handle = pipeline.addConstraint(body1, body2, hinge);
            return new HingeRotaryConstraintHandle(handle);
        } catch (RuntimeException failure) {
            SwivelConstraintCompatibility.logFailure(failure);
            return moveearth$addOriginal(pipeline, body1, body2, originalConfiguration);
        }
    }

    private static Quaterniond moveearth$frameWithLocalYAlong(Vector3dc normal) {
        Vector3d normalized = new Vector3d(normal).normalize();
        return new Quaterniond().rotationTo(0.0, 1.0, 0.0, normalized.x, normalized.y, normalized.z);
    }

    private boolean moveearth$usesHingeDrive() {
        BlockState state = ((BlockEntity) (Object) this).getBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return false;
        }
        Direction direction = state.getValue(BlockStateProperties.FACING);
        return SwivelConstraintCompatibility.enabledFor(
                direction.getStepX(), direction.getStepY(), direction.getStepZ(),
                direction.getStepX(), direction.getStepY(), direction.getStepZ()
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static PhysicsConstraintHandle moveearth$addOriginal(
            PhysicsPipeline pipeline,
            PhysicsPipelineBody body1,
            PhysicsPipelineBody body2,
            PhysicsConstraintConfiguration<?> configuration
    ) {
        return pipeline.addConstraint(body1, body2, (PhysicsConstraintConfiguration) configuration);
    }
}
