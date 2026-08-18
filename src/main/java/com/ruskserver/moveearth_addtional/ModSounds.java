package com.ruskserver.moveearth_addtional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Moveearth_addtional.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SERVER_NOTICE = SOUND_EVENTS.register("server_notice",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "server_notice")));
}
