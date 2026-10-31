package com.ruskserver.moveearth_addtional.client.wiki;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.ruskserver.moveearth_addtional.client.ui.MoveEarthUi.*;

/** The site's diagrams drawn in the game: voxel scenes, rounds charts and Mermaid PNGs. */
final class WikiDiagrams {
    private static final int TITLE_LINE = 11;

    private WikiDiagrams() { }

    private static List<FormattedCharSequence> wrap(Font font, String text, int width, boolean bold) {
        if (text == null || text.isBlank()) return List.of();
        Component component = Component.literal(text);
        if (bold) component = component.copy().withStyle(style -> style.withBold(true));
        return font.split(component, Math.max(20, width));
    }

    private static int drawLines(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines, int x, int y,
                                 int color) {
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, x, y, color, false);
            y += TITLE_LINE;
        }
        return y;
    }

    /**
     * A voxel scene in the site's 2:1 isometric projection, painted back to front with the faces against a
     * solid neighbour left out, as moveearth-web's src/lib/voxel/render.js does. Cutaway faces can be hidden.
     */
    static final class VoxelElement implements WikiLayout.Element {
        private static final float TILE_W = 32.0F;
        private static final float TILE_H = 16.0F;
        private static final float TILE_Z = 26.0F;
        private static final float PAD = 14.0F;
        private static final float MAX_SCALE = 0.5F;
        private static final int MAX_HEIGHT = 230;
        private static final int TOGGLE_W = 96;
        private static final int TOGGLE_H = 14;

        private record Face(float[] points, int argb, boolean cutaway) { }

        private record LegendEntry(int x, int y, VoxelScene.Kind kind) { }

        private final VoxelScene scene;
        private final List<Face> faces = new ArrayList<>();
        private final List<FormattedCharSequence> title;
        private final List<FormattedCharSequence> caption;
        private final List<LegendEntry> legend = new ArrayList<>();
        private final int width;
        private final float scale;
        private final float originX;
        private final float originY;
        private final int figureWidth;
        private final int figureHeight;
        private final int legendHeight;
        private boolean cutawayHidden;

        VoxelElement(Font font, VoxelScene scene, int width) {
            this.scene = scene;
            this.width = width;
            this.title = wrap(font, scene.title(), width - (scene.hasCutaway() ? TOGGLE_W + 6 : 0), true);
            this.caption = wrap(font, scene.caption(), width, false);
            Map<String, VoxelScene.Block> index = new HashMap<>();
            for (VoxelScene.Block block : scene.blocks()) index.put(block.x() + "," + block.y() + "," + block.z(), block);
            List<VoxelScene.Block> ordered = new ArrayList<>(scene.blocks());
            ordered.sort(java.util.Comparator.comparingInt(block -> block.x() + block.y() + block.z()));
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (VoxelScene.Block block : ordered) {
                VoxelScene.Kind kind = block.kind();
                int alpha = kind.ghost() ? 71 : 255;
                float ox = (block.x() - block.z()) * TILE_W;
                float oy = (block.x() + block.z()) * TILE_H - block.y() * TILE_Z;
                float[] top = {ox, oy - TILE_Z, ox + TILE_W, oy - TILE_Z + TILE_H, ox, oy - TILE_Z + TILE_H * 2,
                        ox - TILE_W, oy - TILE_Z + TILE_H};
                List<float[]> quads = new ArrayList<>();
                List<Integer> colors = new ArrayList<>();
                if (!occupied(index, block.x(), block.y() + 1, block.z())) {
                    quads.add(top);
                    colors.add(shade(kind.rgb(), 1.18F, alpha));
                }
                if (!occupied(index, block.x(), block.y(), block.z() + 1)) {
                    quads.add(new float[] {top[4], top[5], top[6], top[7], top[6], top[7] + TILE_Z, top[4], top[5] + TILE_Z});
                    colors.add(shade(kind.rgb(), 0.72F, alpha));
                }
                if (!occupied(index, block.x() + 1, block.y(), block.z())) {
                    quads.add(new float[] {top[2], top[3], top[4], top[5], top[4], top[5] + TILE_Z, top[2], top[3] + TILE_Z});
                    colors.add(shade(kind.rgb(), 0.92F, alpha));
                }
                for (int i = 0; i < quads.size(); i++) {
                    float[] quad = quads.get(i);
                    for (int p = 0; p < 8; p += 2) {
                        minX = Math.min(minX, quad[p]);
                        maxX = Math.max(maxX, quad[p]);
                        minY = Math.min(minY, quad[p + 1]);
                        maxY = Math.max(maxY, quad[p + 1]);
                    }
                    faces.add(new Face(quad, colors.get(i), kind.cutaway()));
                }
            }
            float svgWidth = faces.isEmpty() ? 1 : maxX - minX + PAD * 2;
            float svgHeight = faces.isEmpty() ? 1 : maxY - minY + PAD * 2;
            this.scale = Math.min(MAX_SCALE, Math.min(width / svgWidth, MAX_HEIGHT / svgHeight));
            this.figureWidth = (int) Math.ceil(svgWidth * scale);
            this.figureHeight = faces.isEmpty() ? 0 : (int) Math.ceil(svgHeight * scale);
            this.originX = PAD - (faces.isEmpty() ? 0 : minX);
            this.originY = PAD - (faces.isEmpty() ? 0 : minY);
            int lx = 0;
            int ly = 0;
            for (VoxelScene.Kind kind : scene.legend()) {
                int entryWidth = 12 + font.width(kind.label()) + 12;
                if (lx > 0 && lx + entryWidth > width) {
                    lx = 0;
                    ly += 11;
                }
                legend.add(new LegendEntry(lx, ly, kind));
                lx += entryWidth;
            }
            this.legendHeight = scene.legend().isEmpty() ? 0 : ly + 11;
        }

        private static boolean occupied(Map<String, VoxelScene.Block> index, int x, int y, int z) {
            VoxelScene.Block found = index.get(x + "," + y + "," + z);
            return found != null && !found.kind().ghost();
        }

        private static int shade(int rgb, float factor, int alpha) {
            int r = Math.min(255, Math.round((rgb >> 16 & 0xFF) * factor));
            int g = Math.min(255, Math.round((rgb >> 8 & 0xFF) * factor));
            int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
            return alpha << 24 | r << 16 | g << 8 | b;
        }

        private int titleHeight() {
            return Math.max(title.size() * TITLE_LINE, scene.hasCutaway() ? TOGGLE_H + 2 : 0);
        }

        @Override
        public int height() {
            return titleHeight() + figureHeight + (legendHeight > 0 ? 4 + legendHeight : 0)
                    + (caption.isEmpty() ? 0 : 3 + caption.size() * TITLE_LINE);
        }

        private Rect toggle(int x, int y) {
            return new Rect(x + width - TOGGLE_W, y, TOGGLE_W, TOGGLE_H);
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            drawLines(graphics, font, title, x, y, TEXT);
            if (scene.hasCutaway()) {
                Rect toggle = toggle(x, y);
                drawButton(graphics, font, toggle, Component.literal(cutawayHidden ? "手前の壁を表示" : "手前の壁を隠す"),
                        ACCENT, toggle.contains(mouseX, mouseY), true);
            }
            int figureY = y + titleHeight();
            int figureX = x + Math.max(0, (width - figureWidth) / 2);
            graphics.pose().pushPose();
            graphics.pose().translate(figureX, figureY, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.pose().translate(originX, originY, 0.0F);
            Matrix4f matrix = graphics.pose().last().pose();
            VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.gui());
            for (Face face : faces) {
                if (face.cutaway() && cutawayHidden) continue;
                quad(vertices, matrix, face.points(), face.argb());
            }
            graphics.pose().popPose();
            graphics.flush();
            int below = figureY + figureHeight + 4;
            for (LegendEntry entry : legend) {
                int ex = x + entry.x();
                int ey = below + entry.y();
                graphics.fill(ex, ey + 1, ex + 8, ey + 9, 0xFF000000 | entry.kind().rgb());
                graphics.drawString(font, entry.kind().label(), ex + 11, ey + 1, MUTED, false);
            }
            if (!caption.isEmpty()) drawLines(graphics, font, caption, x, below + legendHeight + 3, MUTED);
        }

        /** GUI quads are drawn with one winding; a face projected the other way round is turned over. */
        private static void quad(VertexConsumer vertices, Matrix4f matrix, float[] p, int argb) {
            float area = 0.0F;
            for (int i = 0; i < 8; i += 2) {
                int j = (i + 2) % 8;
                area += p[i] * p[j + 1] - p[j] * p[i + 1];
            }
            int[] order = area < 0 ? new int[] {0, 2, 4, 6} : new int[] {6, 4, 2, 0};
            for (int index : order) vertices.addVertex(matrix, p[index], p[index + 1], 0.0F).setColor(argb);
        }

        @Override
        public boolean click(Font font, int x, int y, double mouseX, double mouseY, Consumer<String> open) {
            if (scene.hasCutaway() && toggle(x, y).contains(mouseX, mouseY)) {
                cutawayHidden = !cutawayHidden;
                return true;
            }
            return false;
        }
    }

    /**
     * Shots each material takes, as a bar of its health divided where each shot lands; the last, partly
     * used shot is marked. A port of moveearth-web's src/lib/chart/render.js.
     */
    static final class RoundsElement implements WikiLayout.Element {
        private static final int ROW = 16;
        private static final int BAR = 10;
        private static final int COUNT_W = 34;
        private final RoundsChart chart;
        private final List<FormattedCharSequence> title;
        private final List<FormattedCharSequence> key;
        private final List<FormattedCharSequence> caption;
        private final int width;
        private final int labelWidth;
        private final float pointWidth;

        RoundsElement(Font font, RoundsChart chart, int width) {
            this.chart = chart;
            this.width = width;
            this.title = wrap(font, chart.title(), width, true);
            this.key = wrap(font, "1区切り = 1発（" + chart.damage() + "ダメージ）。金色は最後の1発で、使い切らなくても1発分掛かります。",
                    width, false);
            this.caption = wrap(font, chart.caption(), width, false);
            int widestLabel = 0;
            for (RoundsChart.Row row : chart.rows()) widestLabel = Math.max(widestLabel, font.width(row.label() + "  " + row.health()));
            this.labelWidth = Math.min(Math.max(60, widestLabel + 8), width / 3);
            int widest = chart.rows().stream().mapToInt(RoundsChart.Row::health).max().orElse(1);
            this.pointWidth = Math.max(0.01F, (width - labelWidth - COUNT_W - 6) / (float) Math.max(1, widest));
        }

        @Override
        public int height() {
            if (chart.rows().isEmpty() || chart.damage() <= 0) return title.size() * TITLE_LINE;
            return title.size() * TITLE_LINE + 2 + chart.rows().size() * ROW + 2 + key.size() * TITLE_LINE
                    + (caption.isEmpty() ? 0 : 2 + caption.size() * TITLE_LINE);
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            int rowY = drawLines(graphics, font, title, x, y, TEXT) + 2;
            if (chart.rows().isEmpty() || chart.damage() <= 0) return;
            for (RoundsChart.Row row : chart.rows()) {
                RoundsChart.Shots shots = RoundsChart.shotsFor(row.health(), chart.damage());
                String hp = Integer.toString(row.health());
                graphics.drawString(font, font.plainSubstrByWidth(row.label(), labelWidth - font.width(hp) - 10),
                        x, rowY + 2, TEXT, false);
                graphics.drawString(font, hp, x + labelWidth - 6 - font.width(hp), rowY + 2, MUTED, false);
                for (int shot = 0; shot < shots.shots(); shot++) {
                    boolean last = shot == shots.shots() - 1;
                    int spent = last ? shots.remainder() : chart.damage();
                    int left = x + labelWidth + Math.round(shot * chart.damage() * pointWidth);
                    int right = Math.max(left + 1, x + labelWidth + Math.round((shot * chart.damage() + spent) * pointWidth) - 1);
                    graphics.fill(left, rowY, right, rowY + BAR, last && shots.remainder() < chart.damage() ? GOLD : 0xFF4FA3D1);
                }
                int barEnd = x + labelWidth + Math.round(chart.rows().stream().mapToInt(RoundsChart.Row::health).max().orElse(0) * pointWidth);
                graphics.drawString(font, shots.shots() + "発", barEnd + 6, rowY + 2, TEXT, false);
                rowY += ROW;
            }
            int below = drawLines(graphics, font, key, x, rowY + 2, MUTED);
            if (!caption.isEmpty()) drawLines(graphics, font, caption, x, below + 2, MUTED);
        }
    }

    /** A Mermaid diagram as the PNG the site renders for the game; a note in its place when this build has none. */
    static final class MermaidElement implements WikiLayout.Element {
        private static final int MAX_HEIGHT = 320;
        private final String key;
        private final int[] size;
        private final int drawWidth;
        private final int drawHeight;
        private final List<FormattedCharSequence> missing;

        MermaidElement(Font font, String key, int[] size, int width) {
            this.key = key;
            this.size = size;
            if (size == null || size[0] <= 0 || size[1] <= 0) {
                this.drawWidth = width;
                this.drawHeight = 0;
                this.missing = wrap(font, "この図はゲーム内版に含まれていません。右上の「Webで開く」で見られます。", width - 16, false);
            } else {
                float fit = Math.min(1.0F, Math.min((width - 8) / (float) size[0], MAX_HEIGHT / (float) size[1]));
                this.drawWidth = Math.max(1, Math.round(size[0] * fit));
                this.drawHeight = Math.max(1, Math.round(size[1] * fit));
                this.missing = List.of();
            }
        }

        @Override
        public int height() {
            return missing.isEmpty() ? drawHeight + 8 : missing.size() * TITLE_LINE + 12;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
            if (!missing.isEmpty()) {
                graphics.fill(x, y, x + drawWidth, y + height(), 0xC0151B22);
                drawBorder(graphics, new Rect(x, y, drawWidth, height()), 0x803A4A5C);
                drawLines(graphics, font, missing, x + 8, y + 6, MUTED);
                return;
            }
            graphics.fill(x, y, x + drawWidth + 8, y + drawHeight + 8, 0xFF16211A);
            int textureWidth = size[0] * 2;
            int textureHeight = size[1] * 2;
            graphics.blit(WikiLibrary.diagramTexture(key), x + 4, y + 4, drawWidth, drawHeight, 0.0F, 0.0F,
                    textureWidth, textureHeight, textureWidth, textureHeight);
        }
    }
}
