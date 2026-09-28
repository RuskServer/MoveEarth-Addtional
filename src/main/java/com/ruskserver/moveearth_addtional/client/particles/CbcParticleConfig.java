package com.ruskserver.moveearth_addtional.client.particles;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CbcParticleConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue PROFILE = BUILDER.define("profile", false);
    public static final ModConfigSpec.BooleanValue LIMIT = BUILDER.define("limitDecorativeSmoke", false);
    public static final ModConfigSpec.IntValue NEAR_BUDGET = BUILDER.defineInRange("nearSmokePerTick", 128, 1, 4096);
    public static final ModConfigSpec.IntValue FAR_BUDGET = BUILDER.defineInRange("farSmokePerTick", 32, 1, 4096);
    public static final ModConfigSpec.IntValue EXPLOSION_NEAR_BUDGET = BUILDER.defineInRange("nearExplosionSmokePerTick", 256, 1, 8192);
    public static final ModConfigSpec.IntValue EXPLOSION_FAR_BUDGET = BUILDER.defineInRange("farExplosionSmokePerTick", 64, 1, 8192);
    public static final ModConfigSpec.BooleanValue COLLISION_LOD = BUILDER.define("distantSmokeCollisionLod", false);
    public static final ModConfigSpec.DoubleValue COLLISION_DISTANCE = BUILDER.defineInRange("smokeCollisionDistance", 64.0, 16.0, 512.0);
    public static final ModConfigSpec.DoubleValue NEAR_DISTANCE = BUILDER.defineInRange("nearDistance", 48.0, 8.0, 256.0);
    public static final ModConfigSpec SPEC = BUILDER.build();

    private CbcParticleConfig() { }
}
