package com.ruskserver.moveearth_addtional.compat.jei;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.entity.ModEntities;
import com.ruskserver.moveearth_addtional.entity.WarehouseRaiderEntity;
import com.ruskserver.moveearth_addtional.entity.ai.RaiderRole;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.raid.AirshipRaidDifficulty;
import com.ruskserver.moveearth_addtional.warehouse.WarehouseRewardTable;
import com.ruskserver.moveearth_addtional.warehouse.WarehouseRewardTable.Drop;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The warehouse loot table, drawn as the encounter rather than as a word list.
 *
 * <p>It replaces the plain info tabs that used to carry these drops. A tab of
 * prose tells a player what an item is worth only if they already know what a
 * warehouse looks like; showing the guard captain above the drops says where
 * they come from without a sentence. The entity is the mod's own boss, equipped
 * exactly as it spawns, so the picture stays right when the loadout changes.
 *
 * <p>Every number shown comes from {@link WarehouseRewardTable}. Nothing here
 * knows a count.
 */
public final class WarehouseRewardCategory implements IRecipeCategory<WarehouseRewardCategory.Display> {

    /**
     * The single entry this category holds.
     *
     * <p>A record rather than a bare marker so JEI can tell two entries apart if
     * the table ever gains a second one, and so the drops are read once at
     * registration instead of on every frame.
     */
    public record Display(List<Drop> guaranteed, List<Drop> bonus) { }

    public static final RecipeType<Display> TYPE =
            RecipeType.create(Moveearth_addtional.MODID, "warehouse_reward", Display.class);

    private static final String TITLE_KEY = "gui.moveearth_addtional.jei.warehouse_reward";
    private static final String BOSS_KEY = "gui.moveearth_addtional.jei.warehouse_reward.boss";
    private static final String GUARANTEED_KEY = "gui.moveearth_addtional.jei.warehouse_reward.guaranteed";
    private static final String BONUS_KEY = "gui.moveearth_addtional.jei.warehouse_reward.bonus";
    private static final String RANGE_KEY = "gui.moveearth_addtional.jei.warehouse_reward.range";

    private static final int WIDTH = 162;

    private static final int MODEL_LEFT = 47;
    private static final int MODEL_TOP = 4;
    private static final int MODEL_RIGHT = 115;
    private static final int MODEL_BOTTOM = 72;
    /** Tall enough to fill the viewport without the helmet touching its top edge. */
    private static final int MODEL_SCALE = 26;
    private static final float MODEL_Y_OFFSET = 0.0F;

    private static final int NAME_Y = 76;
    private static final int GUARANTEED_LABEL_Y = 92;
    /** Coordinates of the frame, not the 16px ingredient inside it. */
    private static final int GUARANTEED_SLOT_Y = 104;
    private static final int SECTION_GAP = 6;
    private static final int LABEL_TO_SLOT = 12;

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_GAP = 4;

    private static final int PANEL = 0xFF0D1117;
    private static final int BORDER = 0xFF28323E;
    private static final int TEXT = 0xFFE8EDF3;
    private static final int MUTED = 0xFF8F9AA8;

    private final IDrawable icon;
    private final IDrawable slotBackground;

    /**
     * Built once and kept, because an entity constructed per frame would re-roll
     * its enchantments every frame and flicker.
     */
    private WarehouseRaiderEntity boss;
    private boolean bossFailed;

    public WarehouseRewardCategory(IGuiHelper guiHelper) {
        this.slotBackground = guiHelper.getSlotDrawable();
        this.icon = guiHelper.createDrawableItemStack(
                new ItemStack(ModItems.PRECISION_FIRING_ASSEMBLY.get()));
    }

    /** The one entry, read from the live table so it cannot fall behind it. */
    public static Display display() {
        return new Display(present(WarehouseRewardTable.guaranteedDrops()),
                present(WarehouseRewardTable.bonusDrops()));
    }

    private static List<Drop> present(List<Drop> drops) {
        List<Drop> installed = new ArrayList<>();
        for (Drop drop : drops) {
            if (drop.exists()) {
                installed.add(drop);
            }
        }
        return List.copyOf(installed);
    }

    @Override public RecipeType<Display> getRecipeType() { return TYPE; }

    @Override public Component getTitle() { return Component.translatable(TITLE_KEY); }

    @Override public IDrawable getIcon() { return icon; }

    @Override public int getWidth() { return WIDTH; }

    @Override public int getHeight() { return bonusSlotY() + slotBackground.getHeight() + SECTION_GAP; }

    private int bonusLabelY() { return GUARANTEED_SLOT_Y + slotBackground.getHeight() + SECTION_GAP; }

    private int bonusSlotY() { return bonusLabelY() + LABEL_TO_SLOT; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Display display, IFocusGroup focuses) {
        place(builder, display.guaranteed(), GUARANTEED_SLOT_Y);
        place(builder, display.bonus(), bonusSlotY());
    }

    private void place(IRecipeLayoutBuilder builder, List<Drop> drops, int y) {
        if (drops.isEmpty()) return;
        int frameWidth = slotBackground.getWidth();
        int insetX = (frameWidth - SLOT_SIZE) / 2;
        int insetY = (slotBackground.getHeight() - SLOT_SIZE) / 2;
        int rowWidth = drops.size() * frameWidth + (drops.size() - 1) * SLOT_GAP;
        int x = (WIDTH - rowWidth) / 2;
        for (Drop drop : drops) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x + insetX, y + insetY)
                    .setBackground(slotBackground, -insetX, -insetY)
                    .addItemStack(drop.least());
            if (drop.varies()) {
                // The stack shows the count a player is always given; the range
                // it can grow to only fits in a tooltip.
                slot.addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable(RANGE_KEY, drop.min(), drop.max())));
            }
            x += frameWidth + SLOT_GAP;
        }
    }

    @Override
    public void draw(Display display, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        graphics.fill(MODEL_LEFT, MODEL_TOP, MODEL_RIGHT, MODEL_BOTTOM, PANEL);
        drawBorder(graphics, MODEL_LEFT, MODEL_TOP, MODEL_RIGHT, MODEL_BOTTOM);
        drawBoss(graphics, mouseX, mouseY);
        Font font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, Component.translatable(BOSS_KEY), WIDTH / 2, NAME_Y, TEXT);
        graphics.drawCenteredString(font, Component.translatable(GUARANTEED_KEY),
                WIDTH / 2, GUARANTEED_LABEL_Y, MUTED);
        graphics.drawCenteredString(font, Component.translatable(BONUS_KEY),
                WIDTH / 2, bonusLabelY(), MUTED);
    }

    private static void drawBorder(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, top + 1, BORDER);
        graphics.fill(left, bottom - 1, right, bottom, BORDER);
        graphics.fill(left, top, left + 1, bottom, BORDER);
        graphics.fill(right - 1, top, right, bottom, BORDER);
    }

    /**
     * Draws the guard captain inside the viewport.
     *
     * <p>The pose is put back into screen space first.
     * {@link InventoryScreen#renderEntityInInventoryFollowsMouse} clips with a
     * scissor, and a scissor is taken in window coordinates while everything
     * else a category draws goes through JEI's translated pose. Handing it the
     * category-local rectangle would clip a box near the top-left of the screen
     * and hide the entity entirely — which is exactly the kind of failure that
     * shows as "nothing rendered" with no error to follow.
     */
    private void drawBoss(GuiGraphics graphics, double mouseX, double mouseY) {
        LivingEntity entity = boss();
        if (entity == null) {
            return;
        }
        Matrix4f pose = graphics.pose().last().pose();
        int dx = (int) pose.m30();
        int dy = (int) pose.m31();
        graphics.pose().pushPose();
        graphics.pose().translate(-dx, -dy, 0.0F);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                MODEL_LEFT + dx + 1, MODEL_TOP + dy + 1, MODEL_RIGHT + dx - 1, MODEL_BOTTOM + dy - 1,
                MODEL_SCALE, MODEL_Y_OFFSET, (float) mouseX + dx, (float) mouseY + dy, entity);
        graphics.pose().popPose();
    }

    private LivingEntity boss() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || bossFailed) {
            return null;
        }
        if (boss != null && boss.level() == level) {
            return boss;
        }
        try {
            WarehouseRaiderEntity created = ModEntities.WAREHOUSE_RAIDER.get().create(level);
            if (created != null) {
                // The same two calls, in the same order, that spawn the real
                // captain. The role picks the gun, so setting it after the
                // loadout would draw a rifle the boss never carries.
                created.setRole(RaiderRole.HEAVY);
                created.equipRaidLoadout(AirshipRaidDifficulty.NORMAL);
                created.setCustomName(Component.translatable(BOSS_KEY));
            }
            boss = created;
        } catch (RuntimeException failure) {
            // The drops are the point; losing the portrait must not take the
            // recipe screen down with it. Logged once so it is not silent.
            bossFailed = true;
            Moveearth_addtional.LOGGER.warn("Warehouse reward category could not build its boss portrait",
                    failure);
            boss = null;
        }
        return boss;
    }
}
