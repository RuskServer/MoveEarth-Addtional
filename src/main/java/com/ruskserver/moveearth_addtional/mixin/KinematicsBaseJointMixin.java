package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.compat.kinematics.KinematicsJointView;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Absolute Kinematics 1.0.1 joint base; see {@link KinematicsHingeWindupMixin}. */
@Pseudo
@Mixin(targets = "com.rcx.absolutekinematics.blockentity.BaseJointBlockEntity", remap = false)
public abstract class KinematicsBaseJointMixin implements KinematicsJointView {
    @Shadow public DeferredBlock<? extends Block> plateBlock;
    @Shadow public GenericConstraintHandle handle;

    @Shadow public abstract boolean isAssembled();
    @Shadow public abstract boolean isLocking();
    @Shadow public abstract SubLevel getAttachedSubLevel();
    @Shadow public abstract BlockPos getPlatePos();

    @Override
    public boolean moveearth$drivesAttachedBody() {
        Level level = ((BlockEntity) (Object) this).getLevel();
        if (level == null || level.isClientSide || handle == null || !isAssembled() || !isLocking()) return false;
        if (getAttachedSubLevel() == null) return false;
        BlockPos plate = getPlatePos();
        return plate != null && level.getBlockState(plate).is(plateBlock);
    }
}
