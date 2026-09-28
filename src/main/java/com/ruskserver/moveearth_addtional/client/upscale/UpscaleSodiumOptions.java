package com.ruskserver.moveearth_addtional.client.upscale;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.upscale.UpscaleClientConfig.EdgeDetection;
import com.ruskserver.moveearth_addtional.client.upscale.UpscaleClientConfig.Mode;
import com.ruskserver.moveearth_addtional.client.upscale.UpscaleClientConfig.Quality;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Optional Sodium 0.8 Config API entry point, loaded only when Sodium discovers it. */
public final class UpscaleSodiumOptions implements ConfigEntryPoint {
    private static final String KEY = "options.moveearth_addtional.upscale.";
    private final StorageEventHandler storage = UpscaleClientConfig.SPEC::save;

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        var page = builder.createOptionPage()
                .setName(label("page"))
                .addOptionGroup(builder.createOptionGroup()
                        .setName(label("rendering"))
                        .addOption(builder.createEnumOption(id("mode"), Mode.class)
                                .setName(label("mode"))
                                .setTooltip(label("mode.tooltip"))
                                .setStorageHandler(storage)
                                .setBinding(UpscaleClientConfig::setMode, UpscaleClientConfig::mode)
                                .setDefaultValue(Mode.OFF)
                                .setElementNameProvider(mode -> label("mode." + mode.name().toLowerCase(java.util.Locale.ROOT)))
                                .setApplyHook(state -> UpscaleRenderer.settingsChanged()))
                        .addOption(builder.createEnumOption(id("edges"), EdgeDetection.class)
                                .setName(label("edges"))
                                .setTooltip(label("edges.tooltip"))
                                .setStorageHandler(storage)
                                .setBinding(UpscaleClientConfig::setEdgeDetection, UpscaleClientConfig::edgeDetection)
                                .setDefaultValue(EdgeDetection.DEPTH)
                                .setElementNameProvider(edges -> label("edges." + edges.name().toLowerCase(java.util.Locale.ROOT)))
                                .setApplyHook(state -> UpscaleRenderer.settingsChanged())
                                .setEnabledProvider(state -> state.readEnumOption(id("mode"), Mode.class) != Mode.OFF, id("mode")))
                        .addOption(builder.createEnumOption(id("quality"), Quality.class)
                                .setName(label("quality"))
                                .setTooltip(label("quality.tooltip"))
                                .setStorageHandler(storage)
                                .setBinding(UpscaleClientConfig::setQuality, UpscaleClientConfig::quality)
                                .setDefaultValue(Quality.ULTRA_QUALITY)
                                .setElementNameProvider(quality -> label("quality." + quality.name().toLowerCase(java.util.Locale.ROOT)))
                                .setApplyHook(state -> UpscaleRenderer.settingsChanged())
                                .setEnabledProvider(state -> fsr(state.readEnumOption(id("mode"), Mode.class)), id("mode")))
                        .addOption(builder.createIntegerOption(id("sharpness"))
                                .setName(label("sharpness"))
                                .setTooltip(label("sharpness.tooltip"))
                                .setStorageHandler(storage)
                                .setBinding(UpscaleClientConfig::setSharpnessPercent, UpscaleClientConfig::sharpnessPercent)
                                .setDefaultValue(80)
                                .setRange(0, 100, 5)
                                .setValueFormatter(value -> Component.literal(value + "%"))
                                .setApplyHook(state -> UpscaleRenderer.settingsChanged())
                                .setEnabledProvider(state -> fsr(state.readEnumOption(id("mode"), Mode.class)), id("mode")))
                        .addOption(builder.createBooleanOption(id("status"))
                                .setName(label("status"))
                                .setTooltip(label("status.tooltip"))
                                .setStorageHandler(storage)
                                .setBinding(UpscaleClientConfig::setShowStatus, UpscaleClientConfig::showStatus)
                                .setDefaultValue(false)));
        builder.registerOwnModOptions().addPage(page);
    }

    private static boolean fsr(Mode mode) { return mode == Mode.SMAA_FSR; }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "upscale_" + path);
    }

    private static Component label(String path) { return Component.translatable(KEY + path); }
}
