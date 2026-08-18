package com.ruskserver.moveearth_addtional.raid;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class AirshipRaidInstance {
    private final int id;
    private final UUID targetId;
    private final String targetName;
    private final AirshipRaidDifficulty difficulty;
    private final long startedAt;
    private final ResourceKey<Level> levelKey;
    private AirshipRaidPhase phase = AirshipRaidPhase.APPROACH;
    private final Set<UUID> raiderIds = new LinkedHashSet<>();
    private UUID shipId;
    private float hullIntegrity = 1000.0F;
    private int destroyedCores;
    private long phaseStartedAt;
    private boolean salvageWarningSent;
    private final Map<BlockPos, Float> componentDamage = new HashMap<>();

    public AirshipRaidInstance(int id, ServerPlayer target, AirshipRaidDifficulty difficulty, long startedAt) {
        this.id = id;
        this.targetId = target.getUUID();
        this.targetName = target.getGameProfile().getName();
        this.difficulty = difficulty;
        this.startedAt = startedAt;
        this.levelKey = target.level().dimension();
        this.phaseStartedAt = startedAt;
    }

    public int id() { return id; }
    public UUID targetId() { return targetId; }
    public String targetName() { return targetName; }
    public AirshipRaidDifficulty difficulty() { return difficulty; }
    public AirshipRaidPhase phase() { return phase; }
    public long startedAt() { return startedAt; }
    public ResourceKey<Level> levelKey() { return levelKey; }
    public void setPhase(AirshipRaidPhase phase) { this.phase = phase; }
    public void setPhase(AirshipRaidPhase phase, long gameTime) { this.phase = phase; this.phaseStartedAt = gameTime; }
    public Set<UUID> raiderIds() { return raiderIds; }
    public void addRaider(UUID id) { raiderIds.add(id); }
    public UUID shipId() { return shipId; }
    public void setShipId(UUID shipId) { this.shipId = shipId; }
    public float hullIntegrity() { return hullIntegrity; }
    public void damageHull(float damage) { hullIntegrity = Math.max(0.0F, hullIntegrity - damage); }
    public int destroyedCores() { return destroyedCores; }
    public void destroyCore() { destroyedCores++; }
    public long phaseStartedAt() { return phaseStartedAt; }
    public boolean salvageWarningSent() { return salvageWarningSent; }
    public void setSalvageWarningSent() { salvageWarningSent = true; }
    public float damageComponent(BlockPos pos, float damage) {
        return componentDamage.merge(pos.immutable(), damage, Float::sum);
    }
}
