package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.ruskserver.moveearth_addtional.config.MekanismBalanceConfig;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Applies MoveEarth's territory to Mekanism radiation.
 *
 * <ul>
 *   <li>Sources in the wilderness, and sources near any territory core, decay
 *   faster than Mekanism's rate, so neither open ground nor a core can be held
 *   with lasting contamination.</li>
 *   <li>Exposure from a source under different control than the place being
 *   measured is weakened, so radiation does not spill far across a border in
 *   either direction.</li>
 * </ul>
 *
 * <p>Mekanism's radiation classes only know block positions. Its per-level
 * decay pass and its exposure queries are therefore bracketed by the mixins,
 * which note the level being worked on for the length of the call.
 */
public final class MekanismRadiationTerritory {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private MekanismRadiationTerritory() {
    }

    public static void begin(Level level, BlockPos measuredAt) {
        if (!(level instanceof ServerLevel serverLevel) || !MekanismBalanceConfig.territoryRulesEnabled()) return;
        CURRENT.set(new Context(serverLevel, TerritorySavedData.get(serverLevel.getServer()),
                serverLevel.dimension().location(), measuredAt));
    }

    public static void end() {
        CURRENT.remove();
    }

    /** Decay factor for the source at {@code pos}, given Mekanism's configured one. */
    public static double decayRate(double mekanismRate, BlockPos pos) {
        Context context = CURRENT.get();
        if (context == null) return mekanismRate;
        MekanismRadiationPolicy.Zone zone;
        if (!context.territory.coresNear(context.dimension, pos, MekanismBalanceConfig.coreRadiusBlocks()).isEmpty()) {
            zone = MekanismRadiationPolicy.Zone.NEAR_CORE;
        } else if (context.controller(pos).isEmpty()) {
            zone = MekanismRadiationPolicy.Zone.WILDERNESS;
        } else {
            zone = MekanismRadiationPolicy.Zone.TERRITORY;
        }
        return MekanismRadiationPolicy.decayRate(mekanismRate, zone,
                MekanismBalanceConfig.wildernessSourceDecayRate(), MekanismBalanceConfig.coreSourceDecayRate());
    }

    /** Squared distance used for exposure from {@code source} at {@code measured}. */
    public static double exposureDistanceSqr(double distanceSqr, BlockPos measured, Vec3i source) {
        Context context = CURRENT.get();
        if (context == null) return distanceSqr;
        boolean crossBorder = !context.measuredController(measured).equals(context.controller(new BlockPos(source)));
        return MekanismRadiationPolicy.exposureDistanceSqr(distanceSqr, crossBorder,
                MekanismBalanceConfig.crossBorderExposureMultiplier());
    }

    private static final class Context {
        private final ServerLevel level;
        private final TerritorySavedData territory;
        private final ResourceLocation dimension;
        private final BlockPos measuredAt;
        private Optional<UUID> measuredController;

        private Context(ServerLevel level, TerritorySavedData territory, ResourceLocation dimension, BlockPos measuredAt) {
            this.level = level;
            this.territory = territory;
            this.dimension = dimension;
            this.measuredAt = measuredAt;
        }

        Optional<UUID> controller(BlockPos pos) {
            return territory.controllingNation(level.getServer(), dimension, pos);
        }

        /** The measured point is the same for every source in one query, so it is looked up once. */
        Optional<UUID> measuredController(BlockPos measured) {
            if (measuredAt == null || !measuredAt.equals(measured)) return controller(measured);
            if (measuredController == null) measuredController = controller(measured);
            return measuredController;
        }
    }
}
