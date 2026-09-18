package com.ruskserver.moveearth_addtional.terrain;

import com.mojang.serialization.MapCodec;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the density function type.
 *
 * <p>{@code DENSITY_FUNCTION_TYPE} is not one of the registries NeoForge syncs
 * to clients, so this can live in a server-only build without the client ever
 * being asked to resolve it.
 */
public final class TerrainRegistration {
    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Moveearth_addtional.MODID);

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<MapFieldDensityFunction>> MAP_FIELD =
            DENSITY_FUNCTION_TYPES.register("map_field", () -> MapFieldDensityFunction.MAP_CODEC);

    private TerrainRegistration() { }

    public static void register(IEventBus modEventBus) {
        DENSITY_FUNCTION_TYPES.register(modEventBus);
    }
}
