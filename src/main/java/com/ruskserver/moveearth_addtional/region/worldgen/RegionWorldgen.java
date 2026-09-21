package com.ruskserver.moveearth_addtional.region.worldgen;

import com.mojang.serialization.MapCodec;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Worldgen types the region system adds. */
public final class RegionWorldgen {

    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, Moveearth_addtional.MODID);

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS,
                    Moveearth_addtional.MODID);

    public static final DeferredHolder<PlacementModifierType<?>,
            PlacementModifierType<RegionGatePlacement>> REGION_GATE =
            PLACEMENT_MODIFIERS.register("region_gate",
                    () -> (PlacementModifierType<RegionGatePlacement>) () -> RegionGatePlacement.CODEC);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>,
            MapCodec<RegionGateOresModifier>> REGION_GATE_ORES =
            BIOME_MODIFIERS.register("region_gate_ores", () -> RegionGateOresModifier.CODEC);

    private RegionWorldgen() { }
}
