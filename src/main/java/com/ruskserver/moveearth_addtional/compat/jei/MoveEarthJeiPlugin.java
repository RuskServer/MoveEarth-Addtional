package com.ruskserver.moveearth_addtional.compat.jei;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.TechnologyScreen;
import com.ruskserver.moveearth_addtional.client.compat.JeiTechnologyBridge;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.warehouse.WarehouseEncounterState;
import com.ruskserver.moveearth_addtional.warehouse.WarehouseRewardTable;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

/** Optional JEI adapter: technology nodes open output recipes and reserve the whole custom screen. */
@JeiPlugin
public final class MoveEarthJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "technology");

    @Override public ResourceLocation getPluginUid() { return UID; }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(ModItems.PRECISION_FIRING_ASSEMBLY.get(),
                Component.literal("倉庫拠点の警備隊長を倒し、建物内の輸送コンテナから回収"),
                Component.literal("1攻略で確定" + WarehouseRewardTable.PRECISION_ASSEMBLIES + "個。"
                        + "戦利品は共有され、先に回収した側が獲得"),
                Component.literal("再稼働までサーバー開放時間 "
                        + (WarehouseEncounterState.COOLDOWN_TICKS / 20 / 60 / 60) + "時間"));
        Component alternative = Component.literal("倉庫拠点攻略時、精密射撃機構と一緒に"
                + "ネザー素材1種類が少量出る場合があります。通常のネザー入手経路も残ります");
        registration.addIngredientInfo(Items.NETHER_WART, alternative);
        registration.addIngredientInfo(Items.QUARTZ, alternative);
        registration.addIngredientInfo(Items.GLOWSTONE_DUST, alternative);
        registration.addIngredientInfo(Items.BLAZE_ROD, alternative);
        registration.addIngredientInfo(Items.GHAST_TEAR, alternative);
    }

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
