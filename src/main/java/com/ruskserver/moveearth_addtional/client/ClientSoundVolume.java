package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.ClientDisplayConfig;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Applies the player's announcer and notice volume settings. Both sounds are
 * sent by the server on the master channel, so the vanilla sliders cannot
 * single them out; the client scales them as they start playing.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class ClientSoundVolume {
    private ClientSoundVolume() { }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof TickableSoundInstance) return;
        ResourceLocation id = sound.getLocation();
        double volume = switch (ClientSoundChannel.of(id.getNamespace(), id.getPath())) {
            case ANNOUNCER -> ClientDisplayConfig.ANNOUNCER_VOLUME.get();
            case NOTICE -> ClientDisplayConfig.NOTICE_VOLUME.get();
            case NONE -> 1.0D;
        };
        if (volume >= 1.0D) return;
        event.setSound(volume <= 0.0D ? null : new Scaled(sound, (float) volume));
    }

    /** Plays {@code delegate} unchanged except for its volume. */
    private record Scaled(SoundInstance delegate, float factor) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return delegate.getLocation(); }
        @Override public WeighedSoundEvents resolve(SoundManager manager) { return delegate.resolve(manager); }
        @Override public Sound getSound() { return delegate.getSound(); }
        @Override public SoundSource getSource() { return delegate.getSource(); }
        @Override public boolean isLooping() { return delegate.isLooping(); }
        @Override public boolean isRelative() { return delegate.isRelative(); }
        @Override public int getDelay() { return delegate.getDelay(); }
        @Override public float getVolume() { return delegate.getVolume() * factor; }
        @Override public float getPitch() { return delegate.getPitch(); }
        @Override public double getX() { return delegate.getX(); }
        @Override public double getY() { return delegate.getY(); }
        @Override public double getZ() { return delegate.getZ(); }
        @Override public Attenuation getAttenuation() { return delegate.getAttenuation(); }
        @Override public boolean canStartSilent() { return delegate.canStartSilent(); }
        @Override public boolean canPlaySound() { return delegate.canPlaySound(); }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
            return delegate.getStream(library, sound, looping);
        }
    }
}
