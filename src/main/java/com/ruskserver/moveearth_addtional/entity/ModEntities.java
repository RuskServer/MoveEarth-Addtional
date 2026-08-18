package com.ruskserver.moveearth_addtional.entity;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, Moveearth_addtional.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<AirshipRaiderEntity>> AIRSHIP_RAIDER =
            ENTITY_TYPES.register("airship_raider", () -> EntityType.Builder
                    .of(AirshipRaiderEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(Moveearth_addtional.MODID + ":airship_raider"));

    private ModEntities() {
    }
}
