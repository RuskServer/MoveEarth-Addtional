package com.ruskserver.moveearth_addtional.client.config;

import com.ruskserver.moveearth_addtional.client.scope.ScopePipConfig;
import com.ruskserver.moveearth_addtional.config.ClientDisplayConfig;
import com.ruskserver.moveearth_addtional.config.StartupClientConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Locale;

/**
 * The mod's client settings screen, built with YetAnotherConfigLib. Values stay
 * in the NeoForge config files; this screen only edits and saves them, and
 * every option applies without a restart.
 *
 * <p>Only reached when YACL is loaded; see {@link ClientConfigScreens}.
 */
final class MoveEarthConfigScreen {
    private static final String KEY = "moveearth_addtional.config.";

    private MoveEarthConfigScreen() { }

    static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable(KEY + "title"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable(KEY + "scope"))
                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable(KEY + "scope.quality"))
                                .option(toggle("scope.enabled", ScopePipConfig.ENABLED, false))
                                .option(percent("scope.resolution", ScopePipConfig.RESOLUTION, 0.5D, 0.25D, 1.0D))
                                .option(toggle("scope.smooth", ScopePipConfig.SMOOTH_LENS, false))
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable(KEY + "scope.interval"))
                                        .description(describe("scope.interval"))
                                        .binding(2, ScopePipConfig.UPDATE_INTERVAL::get, ScopePipConfig.UPDATE_INTERVAL::set)
                                        .controller(option -> IntegerSliderControllerBuilder.create(option)
                                                .range(1, 3).step(1)
                                                .formatValue(value -> Component.translatable(value == 1
                                                        ? KEY + "scope.interval.every" : KEY + "scope.interval.nth", value)))
                                        .build())
                                .option(Option.<Double>createBuilder()
                                        .name(Component.translatable(KEY + "scope.magnification"))
                                        .description(describe("scope.magnification"))
                                        .binding(2.0D, ScopePipConfig.MINIMUM_MAGNIFICATION::get,
                                                ScopePipConfig.MINIMUM_MAGNIFICATION::set)
                                        .controller(option -> DoubleSliderControllerBuilder.create(option)
                                                .range(1.25D, 8.0D).step(0.25D)
                                                .formatValue(value -> Component.literal(
                                                        String.format(Locale.ROOT, "%.2fx", value))))
                                        .build())
                                .build())
                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable(KEY + "scope.advanced"))
                                .option(toggle("scope.iris", ScopePipConfig.IRIS_EXPERIMENTAL, false))
                                .option(toggle("scope.debug", ScopePipConfig.DEBUG, false))
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable(KEY + "hud"))
                        .option(toggle("hud.balance", ClientDisplayConfig.SHOW_BALANCE, true))
                        .option(toggle("hud.event", ClientDisplayConfig.SHOW_EVENT_HUD, true))
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable(KEY + "effects"))
                        .option(percent("effects.shield_flash", ClientDisplayConfig.SHIELD_FLASH_STRENGTH, 1.0D, 0.0D, 1.0D))
                        .option(toggle("effects.calm_death", ClientDisplayConfig.CALM_DEATH_SCREEN, false))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.translatable(KEY + "effects.reduced_motion"))
                                .description(describe("effects.reduced_motion"))
                                .binding(false, StartupClientConfig::reducedMotion, StartupClientConfig::setReducedMotion)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable(KEY + "audio"))
                        .option(percent("audio.announcer", ClientDisplayConfig.ANNOUNCER_VOLUME, 1.0D, 0.0D, 1.0D))
                        .option(percent("audio.notice", ClientDisplayConfig.NOTICE_VOLUME, 1.0D, 0.0D, 1.0D))
                        .build())
                .save(() -> {
                    ScopePipConfig.SPEC.save();
                    ClientDisplayConfig.SPEC.save();
                    StartupClientConfig.SPEC.save();
                })
                .build()
                .generateScreen(parent);
    }

    private static Option<Boolean> toggle(String key, ModConfigSpec.BooleanValue value, boolean fallback) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable(KEY + key))
                .description(describe(key))
                .binding(fallback, value::get, value::set)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Double> percent(String key, ModConfigSpec.DoubleValue value, double fallback,
                                          double min, double max) {
        return Option.<Double>createBuilder()
                .name(Component.translatable(KEY + key))
                .description(describe(key))
                .binding(fallback, value::get, value::set)
                .controller(option -> DoubleSliderControllerBuilder.create(option)
                        .range(min, max).step(0.05D)
                        .formatValue(number -> Component.literal(Math.round(number * 100.0D) + "%")))
                .build();
    }

    private static OptionDescription describe(String key) {
        return OptionDescription.of(Component.translatable(KEY + key + ".desc"));
    }
}
