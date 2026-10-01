package com.ruskserver.moveearth_addtional.compat.jei;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.nether.NetherGateConfig;
import com.ruskserver.moveearth_addtional.nether.NetherGateRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Where Nether shards come from: a charged gate generator and a won fight. */
public final class NetherGateCategory implements IRecipeCategory<NetherGateCategory.Display> {
    public record Display(NetherGateConfig.Display values) {
        List<Component> rules() {
            return List.of(
                    Component.translatable("gui.moveearth_addtional.jei.nether_gate.charge",
                            values.gateMinRpm(), NetherCategoryStyle.minutes(values.chargeMinutes())),
                    Component.translatable("gui.moveearth_addtional.jei.nether_gate.fight",
                            values.waves(), NetherCategoryStyle.minutes(values.fightSeconds() / 60.0D)),
                    Component.translatable("gui.moveearth_addtional.jei.nether_gate.players"),
                    Component.translatable("gui.moveearth_addtional.jei.nether_gate.territory"));
        }
    }

    public static final RecipeType<Display> TYPE =
            RecipeType.create(Moveearth_addtional.MODID, "nether_gate", Display.class);

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;

    public NetherGateCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(NetherGateRegistry.GATE_GENERATOR_ITEM.get()));
        this.slot = gui.getSlotDrawable();
        this.arrow = gui.getRecipeArrow();
    }

    public static Display display() {
        return new Display(NetherGateConfig.display());
    }

    @Override public RecipeType<Display> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("gui.moveearth_addtional.jei.nether_gate"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return NetherCategoryStyle.WIDTH; }

    @Override
    public int getHeight() {
        return NetherCategoryStyle.rulesHeight(display().rules());
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Display display, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, 44, NetherCategoryStyle.SLOT_ROW_Y + 1)
                .setBackground(slot, -1, -1)
                .addItemStack(new ItemStack(NetherGateRegistry.GATE_GENERATOR_ITEM.get()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 102, NetherCategoryStyle.SLOT_ROW_Y + 1)
                .setBackground(slot, -1, -1)
                .addItemStack(new ItemStack(NetherGateRegistry.NETHER_SHARD.get(),
                        Math.max(1, display.values().shards())));
    }

    @Override
    public void draw(Display display, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 69, NetherCategoryStyle.SLOT_ROW_Y + 1);
        NetherCategoryStyle.drawRules(graphics, display.rules());
    }
}
