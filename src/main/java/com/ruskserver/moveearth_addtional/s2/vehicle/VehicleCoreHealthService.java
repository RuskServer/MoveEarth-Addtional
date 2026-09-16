package com.ruskserver.moveearth_addtional.s2.vehicle;

import com.ruskserver.moveearth_addtional.block.entity.VehicleCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Applies and presents server-authoritative damage to mobile vehicle cores. */
public final class VehicleCoreHealthService {
    private VehicleCoreHealthService() { }

    public static VehicleSavedData.VehicleRecord damage(ServerLevel level, BlockPos pos, int amount) {
        VehicleSavedData data = VehicleSavedData.get(level.getServer());
        VehicleSavedData.VehicleRecord before = data.at(level.dimension().location(), pos).orElse(null);
        if (before == null || amount <= 0) return before;
        VehicleSavedData.VehicleRecord after = data.damage(before.id(), amount);
        if (after == null || after.health() == before.health()) return after;
        if (level.getBlockEntity(pos) instanceof VehicleCoreBlockEntity blockEntity) blockEntity.bind(after);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5D, pos.getY() + 0.7D,
                pos.getZ() + 0.5D, Math.min(28, 6 + amount / 4), 0.3D, 0.25D, 0.3D, 0.08D);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 3.5F,
                after.health() == 0 ? 0.48F : 0.82F);
        return after;
    }
}
