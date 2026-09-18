package com.ruskserver.moveearth_addtional.worldgen;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.worldgen.tree.BroadFoliagePlacer;
import com.ruskserver.moveearth_addtional.worldgen.tree.BroadTrunkPlacer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the tree placer types the worldgen datapack refers to. */
public final class WorldgenRegistration {
    public static final DeferredRegister<TrunkPlacerType<?>> TRUNK_PLACERS =
            DeferredRegister.create(BuiltInRegistries.TRUNK_PLACER_TYPE, Moveearth_addtional.MODID);
    public static final DeferredRegister<FoliagePlacerType<?>> FOLIAGE_PLACERS =
            DeferredRegister.create(BuiltInRegistries.FOLIAGE_PLACER_TYPE, Moveearth_addtional.MODID);

    public static final DeferredHolder<TrunkPlacerType<?>, TrunkPlacerType<BroadTrunkPlacer>> BROAD_TRUNK =
            TRUNK_PLACERS.register("broad", () -> new TrunkPlacerType<>(BroadTrunkPlacer.CODEC));
    public static final DeferredHolder<FoliagePlacerType<?>, FoliagePlacerType<BroadFoliagePlacer>> BROAD_FOLIAGE =
            FOLIAGE_PLACERS.register("broad", () -> new FoliagePlacerType<>(BroadFoliagePlacer.CODEC));

    private WorldgenRegistration() { }

    public static void register(IEventBus modEventBus) {
        TRUNK_PLACERS.register(modEventBus);
        FOLIAGE_PLACERS.register(modEventBus);
    }
}
