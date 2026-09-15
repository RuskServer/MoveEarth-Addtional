package com.ruskserver.moveearth_addtional.compat.jei;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.TechnologyScreen;
import com.ruskserver.moveearth_addtional.client.compat.JeiTechnologyBridge;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Optional JEI adapter: technology nodes open output recipes and reserve the whole custom screen. */
@JeiPlugin
public final class MoveEarthJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "technology");

    @Override public ResourceLocation getPluginUid() { return UID; }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(TechnologyScreen.class, screen -> {
            Minecraft minecraft = Minecraft.getInstance();
            return new Properties(TechnologyScreen.class, 0, 0,
                    minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
                    minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        });
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiTechnologyBridge.install(id -> {
            if (!BuiltInRegistries.ITEM.containsKey(id)) return false;
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(
                    RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
            runtime.getRecipesGui().show(focus);
            return true;
        });
    }

    @Override public void onRuntimeUnavailable() { JeiTechnologyBridge.clear(); }

    private record Properties(Class<? extends net.minecraft.client.gui.screens.Screen> screenClass,
                              int guiLeft, int guiTop, int guiXSize, int guiYSize,
                              int screenWidth, int screenHeight) implements IGuiProperties { }
}
