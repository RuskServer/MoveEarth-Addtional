package com.ruskserver.moveearth_addtional.compat.create;

import com.ruskserver.moveearth_addtional.config.CreateIndustryConfig;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Makes steam boilers pay for the stress their engines actually deliver.
 *
 * <p>Create burns a boiler's blaze burners at a constant rate whatever the
 * engines are driving. Here each engine reports how much of its network's
 * capacity is in use, the boiler keeps a short average, and the burners below
 * it scale their burn speed from that average. Boilers without a recent report
 * (no engines, unloaded engines, whistle-only boilers) keep Create's own rate.
 */
public final class SteamBoilerFuel {
    private static final long SAMPLE_TICKS = 20L;
    private static final long STALE_TICKS = 60L;
    /** Blaze burner burn ticks from one piece of coal (Create uses the item's furnace burn time). */
    private static final double COAL_BURN_TICKS = 1600.0D;
    private static final double TICKS_PER_MINUTE = 1200.0D;
    /** Keyed by the boiler's controller; entries vanish with the block entity. */
    private static final Map<FluidTankBlockEntity, Sample> SAMPLES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SteamBoilerFuel() {
    }

    public static void reportEngine(FluidTankBlockEntity boiler, float stress, float capacity, long gameTime) {
        double load = SteamFuelMath.load(stress, capacity);
        synchronized (SAMPLES) {
            SAMPLES.computeIfAbsent(boiler, ignored -> new Sample()).add(load, gameTime);
        }
    }

    /** Burn ticks per game tick for the burner at {@code burnerPos}; 1 is Create's own rate. */
    public static double burnRate(Level level, BlockPos burnerPos) {
        if (!CreateIndustryConfig.steamFuelEnabled()) return 1.0D;
        if (!(level.getBlockEntity(burnerPos.above()) instanceof FluidTankBlockEntity tank)) return 1.0D;
        FluidTankBlockEntity boiler = tank.getControllerBE();
        if (boiler == null || boiler.boiler.attachedEngines <= 0) return 1.0D;
        double load;
        synchronized (SAMPLES) {
            Sample sample = SAMPLES.get(boiler);
            load = sample == null ? Double.NaN : sample.load(level.getGameTime());
        }
        if (Double.isNaN(load)) return 1.0D;
        return SteamFuelMath.burnRate(load,
                CreateIndustryConfig.idleBurnRate(), CreateIndustryConfig.fullLoadBurnRate());
    }

    /**
     * Adds the boiler's fuel use to its goggle readout: coal-equivalent per
     * minute across its lit burners, and their rate against Create's normal one.
     */
    public static void appendGoggleTooltip(FluidTankBlockEntity boiler, List<Component> tooltip) {
        Level level = boiler.getLevel();
        if (level == null || boiler.boiler.attachedEngines <= 0) return;
        BlockPos origin = boiler.getBlockPos();
        int width = boiler.getWidth();
        int lit = 0;
        double totalRate = 0.0D;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                if (!(level.getBlockEntity(origin.offset(x, -1, z)) instanceof BlazeBurnerBlockEntity burner)
                        || burner.isCreative()
                        || burner.getActiveFuel() == BlazeBurnerBlockEntity.FuelType.NONE) continue;
                lit++;
                totalRate += ((BoilerBurnerFuelView) burner).moveearth$getBurnRate();
            }
        }
        if (lit == 0) return;
        double coalPerMinute = totalRate * TICKS_PER_MINUTE / COAL_BURN_TICKS;
        long percent = Math.round(totalRate / lit * 100.0D);
        tooltip.add(CommonComponents.EMPTY);
        CreateLang.builder().add(Component.translatable("tooltip.moveearth_addtional.boiler_fuel.coal",
                        Component.literal(String.format(Locale.ROOT, "%.1f", coalPerMinute))
                                .withStyle(ChatFormatting.AQUA)))
                .style(ChatFormatting.GRAY).forGoggles(tooltip);
        CreateLang.builder().add(Component.translatable("tooltip.moveearth_addtional.boiler_fuel.rate",
                        percent, lit))
                .style(ChatFormatting.DARK_GRAY).forGoggles(tooltip, 1);
    }

    /** Average engine load over the last completed window of {@link #SAMPLE_TICKS}. */
    private static final class Sample {
        private double sum;
        private int count;
        private long windowStart = Long.MIN_VALUE;
        private double published = Double.NaN;
        private long publishedAt;

        void add(double load, long gameTime) {
            if (windowStart == Long.MIN_VALUE || gameTime < windowStart) {
                sum = 0.0D;
                count = 0;
                windowStart = gameTime;
            } else if (gameTime - windowStart >= SAMPLE_TICKS) {
                published = sum / count;
                publishedAt = gameTime;
                sum = 0.0D;
                count = 0;
                windowStart = gameTime;
            }
            sum += load;
            count++;
        }

        double load(long gameTime) {
            if (Double.isNaN(published) || gameTime - publishedAt > STALE_TICKS) return Double.NaN;
            return published;
        }
    }
}
