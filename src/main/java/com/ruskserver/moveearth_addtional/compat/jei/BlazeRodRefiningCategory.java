package com.ruskserver.moveearth_addtional.compat.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.client.nether.BlazeRodRefinerRenderer;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Stick + Nether shard → blaze rod, drawn the way Create draws its machines: the
 * refiner itself stands between the ingredients and the result, its rod turning
 * and its press working, so looking up a blaze rod shows what makes it.
 */
public final class BlazeRodRefiningCategory implements IRecipeCategory<BlazeRodRefiningCategory.Display> {
    public record Display(NetherGateConfig.Display values) {
        List<Component> rules() {
            return List.of(
                    Component.translatable("gui.moveearth_addtional.jei.blaze_rod_refining.power",
                            NetherCategoryStyle.minutes(values.refinerStress()), values.refinerMinRpm()),
                    Component.translatable("gui.moveearth_addtional.jei.blaze_rod_refining.time",
                            NetherCategoryStyle.minutes(values.refinerMinutes()), values.refinerMinRpm(),
                            NetherCategoryStyle.minutes(values.refinerMinutesAtMinimum())),
                    Component.translatable("gui.moveearth_addtional.jei.blaze_rod_refining.heat"),
                    Component.translatable("gui.moveearth_addtional.jei.blaze_rod_refining.belt"));
        }
    }

    public static final RecipeType<Display> TYPE =
            RecipeType.create(Moveearth_addtional.MODID, "blaze_rod_refining", Display.class);

    private static final int SLOT_Y = 20;
    private static final int MACHINE_X = 81;
    private static final int MACHINE_Y = 30;
    private static final float MACHINE_SCALE = 22.0F;
    private static final int RULES_Y = 60;

    private final IDrawable icon;
    private final IDrawable slot;

    public BlazeRodRefiningCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(NetherGateRegistry.BLAZE_ROD_REFINER_ITEM.get()));
        this.slot = gui.getSlotDrawable();
    }

    public static Display display() {
        return new Display(NetherGateConfig.display());
    }

    @Override public RecipeType<Display> getRecipeType() { return TYPE; }
    @Override public Component getTitle() {
        return Component.translatable("gui.moveearth_addtional.jei.blaze_rod_refining");
    }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return NetherCategoryStyle.WIDTH; }

    @Override
    public int getHeight() {
        return NetherCategoryStyle.rulesHeight(display().rules(), RULES_Y);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Display display, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 6, SLOT_Y)
                .setBackground(slot, -1, -1).addItemStack(new ItemStack(Items.STICK));
        builder.addSlot(RecipeIngredientRole.INPUT, 26, SLOT_Y)
                .setBackground(slot, -1, -1).addItemStack(new ItemStack(NetherGateRegistry.NETHER_SHARD.get()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 140, SLOT_Y)
                .setBackground(slot, -1, -1).addItemStack(new ItemStack(Items.BLAZE_ROD));
        // The machine drawn in the middle is also a slot, so it can be looked up from here.
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST)
                .addItemStack(new ItemStack(NetherGateRegistry.BLAZE_ROD_REFINER_ITEM.get()));
    }

    @Override
    public void draw(Display display, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        drawMachine(graphics);
        NetherCategoryStyle.drawRules(graphics, display.rules(), RULES_Y);
    }

    /** The refiner from above at an angle, with its rod turning and its press working. */
    private static void drawMachine(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState state = NetherGateRegistry.BLAZE_ROD_REFINER.get().defaultBlockState();
        float seconds = (System.currentTimeMillis() % 100_000L) / 1000.0F;
        float turn = seconds * 0.75F;   // turns per second
        float angle = (float) (turn * Math.PI * 2.0D);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(MACHINE_X, MACHINE_Y, 150.0F);
        pose.scale(MACHINE_SCALE, -MACHINE_SCALE, MACHINE_SCALE);
        // The same view as a block's inventory icon; the model stands about 1.47 blocks
        // tall from 0.22, so it is centred on its own middle rather than the block's.
        pose.mulPose(Axis.XP.rotationDegrees(30.0F));
        pose.mulPose(Axis.YP.rotationDegrees(225.0F));
        pose.translate(-0.5F, -0.85F, -0.5F);

        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        var blocks = minecraft.getBlockRenderer();
        var models = minecraft.getModelManager();
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;
        blocks.renderSingleBlock(state, pose, buffers, light, overlay);

        pose.pushPose();
        pose.translate(0.0F, 0.5F, 0.5F);
        pose.mulPose(Axis.XP.rotation(angle));
        pose.translate(0.0F, -0.5F, -0.5F);
        blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), state,
                models.getModel(BlazeRodRefinerRenderer.ROD), 1.0F, 1.0F, 1.0F, light, overlay);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.0F, BlazeRodRefinerRenderer.PRESS_TRAVEL
                * BlazeRodRefinerRenderer.lift(turn - (float) Math.floor(turn)), 0.0F);
        blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), state,
                models.getModel(BlazeRodRefinerRenderer.PRESS), 1.0F, 1.0F, 1.0F, light, overlay);
        pose.popPose();

        buffers.endBatch();
        pose.popPose();
    }
}
