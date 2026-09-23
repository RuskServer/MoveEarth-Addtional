package com.ruskserver.moveearth_addtional.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A text field whose drawn box, clickable area and text all sit in the same place.
 *
 * <p>Every screen here used to build a plain {@link EditBox}, call
 * {@code setBordered(false)} to be rid of the vanilla sprite, and then paint its
 * own card around it, inflated by a few pixels. That produced two faults at once,
 * both of which read as "the box is slightly off":
 *
 * <ul>
 *   <li><b>The text sat high.</b> {@code EditBox} draws its text at
 *       {@code getY() + (height - 8) / 2} when bordered and at plain
 *       {@code getY()} when not — the vertical centring is part of the border,
 *       not of the field. Turning the border off therefore top-aligned the text,
 *       five pixels above the middle of the card drawn around it.</li>
 *   <li><b>The edges of the box did nothing.</b> Hit testing uses the widget's
 *       own rectangle, which was the inner one; the five pixels of card to either
 *       side and the three above and below were drawn but not clickable, so a
 *       click that plainly landed inside the box did not focus the field.</li>
 * </ul>
 *
 * <p>The fix is to stop keeping two rectangles. The widget <em>is</em> the box:
 * {@code getX()}, {@code getY()}, {@code getWidth()} and {@code getHeight()} all
 * describe what the player sees, so vanilla's own hit testing is already right
 * and none of it is overridden here. The padding is applied where it belongs —
 * to the text — by shifting the pose while the text draws and taking the same
 * shift back off the mouse position when a click is turned into a cursor index.
 * One number, used in both directions, which is what keeps them from drifting.
 */
public final class MoveEarthTextField extends EditBox {

    /** The gap between the edge of the box and the first glyph. */
    public static final int PADDING_X = 5;

    /**
     * The height {@link EditBox} assumes a line of text occupies when it centres
     * one. Vanilla's own constant, reused so a field here lines up with a
     * vanilla-bordered field of the same height.
     */
    private static final int LINE = 8;

    /** The border colour while focused; one screen marks its gold field in gold. */
    private int focusBorder = MoveEarthUi.ACCENT;

    public MoveEarthTextField(Font font, MoveEarthUi.Rect box, Component label) {
        super(font, box.x(), box.y(), box.width(), box.height(), label);
        setBordered(false);
        setTextColor(MoveEarthUi.TEXT);
        setTextColorUneditable(MoveEarthUi.MUTED);
    }

    public MoveEarthTextField(Font font, int x, int y, int width, int height, Component label) {
        this(font, new MoveEarthUi.Rect(x, y, width, height), label);
    }

    /** Changes the colour the border takes while the field has focus. */
    public MoveEarthTextField focusBorder(int color) {
        this.focusBorder = color;
        return this;
    }

    /** The box, which is also the widget's own rectangle. */
    public MoveEarthUi.Rect bounds() {
        return new MoveEarthUi.Rect(getX(), getY(), getWidth(), getHeight());
    }

    /**
     * How much text fits. Unbordered, {@link EditBox} would say the full width
     * and let the last glyph run under the right-hand border.
     */
    @Override
    public int getInnerWidth() {
        return MoveEarthUiMath.textWidth(getWidth(), PADDING_X);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) {
            return;
        }
        MoveEarthUi.Rect box = bounds();
        graphics.fill(box.x(), box.y(), box.right(), box.bottom(), MoveEarthUi.CARD);
        MoveEarthUi.drawBorder(graphics, box, isFocused() ? focusBorder : MoveEarthUi.BORDER);
        graphics.pose().pushPose();
        graphics.pose().translate(PADDING_X, textTop(), 0.0F);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        // The same shift the text was drawn with, undone, so the caret lands
        // under the glyph that was actually clicked.
        super.onClick(mouseX - PADDING_X, mouseY);
    }

    private int textTop() {
        return MoveEarthUiMath.textTop(getHeight(), LINE);
    }
}
