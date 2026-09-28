package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.ConfigFileLayout;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * Moves a world's pre-layout server configs into {@code serverconfig/moveearth/}
 * ({@link ConfigFileLayout}). NeoForge reads server configs inside this method,
 * before ServerAboutToStartEvent, so the move has to happen at its head.
 */
@Mixin(value = ServerLifecycleHooks.class, remap = false)
public abstract class ServerConfigLayoutMixin {
    @Shadow
    private static Path getServerConfigPath(MinecraftServer server) {
        throw new AssertionError();
    }

    @Inject(method = "handleServerAboutToStart", at = @At("HEAD"))
    private static void moveearth$migrateServerConfigs(MinecraftServer server, CallbackInfo callback) {
        ConfigFileLayout.migrateWorldDirectory(getServerConfigPath(server))
                .forEach(line -> Moveearth_addtional.LOGGER.info("[MoveEarth] Config layout: {}", line));
    }
}
