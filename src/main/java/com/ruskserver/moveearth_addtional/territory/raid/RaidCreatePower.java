package com.ruskserver.moveearth_addtional.territory.raid;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class RaidCreatePower {
    private static final ResourceLocation CREATIVE_MOTOR =
            ResourceLocation.fromNamespaceAndPath("create", "creative_motor");

    private RaidCreatePower() {
    }

    public static Snapshot read(KineticBlockEntity blockEntity) {
        double speed = Math.abs(blockEntity.getSpeed());
        if (!blockEntity.hasNetwork()
                || blockEntity.isOverStressed()
                || speed < TerritoryRaidConfig.MINIMUM_SPEED.get()) {
            return Snapshot.inactive(speed);
        }

        KineticNetwork network = blockEntity.getOrCreateNetwork();
        if (!network.members.containsKey(blockEntity)) {
            return Snapshot.inactive(speed);
        }

        double totalCapacity = network.sources.keySet().stream()
                .filter(RaidCreatePower::isStillLoaded)
                .mapToDouble(network::getActualCapacityOf)
                .sum();
        double countableCapacity = network.sources.keySet().stream()
                .filter(RaidCreatePower::isStillLoaded)
                .filter(source -> !isCreativeMotor(source))
                .mapToDouble(network::getActualCapacityOf)
                .sum();
        double totalStress = network.members.keySet().stream()
                .filter(RaidCreatePower::isStillLoaded)
                .mapToDouble(network::getActualStressOf)
                .sum();
        if (!Double.isFinite(totalCapacity) || !Double.isFinite(totalStress)
                || totalCapacity <= 0.0D || totalStress > totalCapacity
                || countableCapacity <= 0.0D) {
            return Snapshot.inactive(speed);
        }

        double countableRatio = Math.min(1.0D, countableCapacity / totalCapacity);
        double validStress = network.getActualStressOf(blockEntity) * countableRatio;
        double strength = RaidPowerBalance.strength(
                validStress,
                TerritoryRaidConfig.STRENGTH_SCALE.get(),
                TerritoryRaidConfig.MAX_STRENGTH.get()
        );
        return new Snapshot(validStress, speed, strength, strength > 0.0D);
    }

    private static boolean isCreativeMotor(KineticBlockEntity source) {
        return CREATIVE_MOTOR.equals(BuiltInRegistries.BLOCK.getKey(source.getBlockState().getBlock()));
    }

    private static boolean isStillLoaded(KineticBlockEntity blockEntity) {
        return blockEntity.getLevel() != null
                && blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity;
    }

    public record Snapshot(double validStress, double speed, double strength, boolean active) {
        private static Snapshot inactive(double speed) {
            return new Snapshot(0.0D, speed, 0.0D, false);
        }
    }
}
