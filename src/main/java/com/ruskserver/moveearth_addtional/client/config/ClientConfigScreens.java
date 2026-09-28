package com.ruskserver.moveearth_addtional.client.config;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.ClientDisplayConfig;
import com.ruskserver.moveearth_addtional.config.ConfigFileLayout;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client entry point for the settings screen opened from the mod list. The
 * YACL screen is used when YetAnotherConfigLib is installed (it is in the
 * MoveEarth pack); otherwise NeoForge's generated screen edits the same files.
 */
@Mod(value = Moveearth_addtional.MODID, dist = Dist.CLIENT)
public final class ClientConfigScreens {
    private static final String YACL_MOD_ID = "yet_another_config_lib_v3";

    public ClientConfigScreens(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientDisplayConfig.SPEC, ConfigFileLayout.CLIENT + "display.toml");
        boolean yacl = ModList.get().isLoaded(YACL_MOD_ID);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> yacl
                ? MoveEarthConfigScreen.create(parent)
                : new ConfigurationScreen(mod, parent));
    }
}
