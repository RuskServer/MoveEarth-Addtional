package com.ruskserver.moveearth_addtional.territory.raid;

import com.ruskserver.moveearth_addtional.block.entity.TerritoryRaidBlockEntity;
import com.ruskserver.moveearth_addtional.territory.domain.RaidEmitter;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryPosition;
import net.minecraft.server.level.ServerLevel;

public final class TerritoryRaidService {
    private TerritoryRaidService() {
    }

    public static State refresh(TerritoryRaidBlockEntity blockEntity) {
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return State.INACTIVE;
        }
        if (!blockEntity.isArmed() || blockEntity.getOwnerUUID() == null) {
            TerritoryRaidRegistry.remove(level.getServer(), blockEntity.getEmitterId());
            return blockEntity.isArmed() ? State.UNCLAIMED : State.DISARMED;
        }

        SableRaidLocator.Location location = SableRaidLocator.locate(blockEntity).orElse(null);
        if (location == null) {
            TerritoryRaidRegistry.remove(level.getServer(), blockEntity.getEmitterId());
            return State.NOT_ON_SABLE_SHIP;
        }

        RaidCreatePower.Snapshot power = RaidCreatePower.read(blockEntity);
        if (!power.active()) {
            TerritoryRaidRegistry.remove(level.getServer(), blockEntity.getEmitterId());
            return State.INSUFFICIENT_POWER;
        }

        RaidEmitter emitter = new RaidEmitter(
                blockEntity.getEmitterId(),
                TerritoryOwnerId.of(blockEntity.getOwnerUUID()),
                new TerritoryPosition(
                        level.dimension().location().toString(),
                        location.worldPosition().x,
                        location.worldPosition().y,
                        location.worldPosition().z
                ),
                TerritoryRaidConfig.RADIUS.get(),
                power.strength(),
                true
        );
        TerritoryRaidRegistry.update(
                level.getServer(),
                emitter,
                location.shipId(),
                power.validStress(),
                level.getGameTime()
        );
        blockEntity.setRuntimePower(power.validStress(), power.strength());
        return State.ACTIVE;
    }

    public static void remove(TerritoryRaidBlockEntity blockEntity) {
        if (blockEntity.getLevel() instanceof ServerLevel level) {
            TerritoryRaidRegistry.remove(level.getServer(), blockEntity.getEmitterId());
        }
    }

    public enum State {
        INACTIVE,
        UNCLAIMED,
        DISARMED,
        NOT_ON_SABLE_SHIP,
        INSUFFICIENT_POWER,
        ACTIVE
    }
}
