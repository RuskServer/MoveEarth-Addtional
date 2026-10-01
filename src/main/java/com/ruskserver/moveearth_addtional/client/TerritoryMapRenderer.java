package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_TerritoryMapPacket;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryMapProjection;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a lightweight territory layer between vanilla map pixels and map decorations.
 *
 * <p>Everything except the pulse of fallen cores is fixed for a given map view and territory
 * version, so the quads, colours and capital labels (text, width, position) are built once per
 * {@link MapKey} and replayed each frame. Labels on item-frame maps further than
 * {@link #LABEL_DISTANCE} blocks away are a few pixels tall and are skipped.
 */
public final class TerritoryMapRenderer {
    private static final float FILL_Z = -0.014F;
    private static final float BORDER_Z = -0.017F;
    private static final float CORE_Z = -0.019F;
    private static final float LABEL_SCALE = 0.42F;
    /** Beyond this an item-frame label is about two pixels tall at 1080p. */
    static final double LABEL_DISTANCE = 12.0D;
    private static final int MAX_CACHED_MAPS = 1024;
    private static final Map<MapKey, ProjectedMap> PROJECTION_CACHE = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MapKey, ProjectedMap> eldest) {
            return size() > MAX_CACHED_MAPS;
        }
    };
    private static long cachedVersion = Long.MIN_VALUE;
    private static final Matrix4f LABEL_MATRIX = new Matrix4f();
    private static final Vector3f MAP_CENTER = new Vector3f();

    private TerritoryMapRenderer() { }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, MapItemSavedData mapData) {
        render(poseStack, buffers, mapData, false);
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, MapItemSavedData mapData,
                              boolean inItemFrame) {
        ResourceLocation dimension = mapData.dimension.location();
        TerritoryMapClientState.requestIfStale(dimension);
        if (!TerritoryMapClientState.enabled()) return;
        List<S2C_TerritoryMapPacket.CoreEntry> dimensionCores = TerritoryMapClientState.cores(dimension);
        if (dimensionCores.isEmpty()) return;
        long version = TerritoryMapClientState.version();
        if (version != cachedVersion) {
            PROJECTION_CACHE.clear();
            cachedVersion = version;
        }
        MapKey key = new MapKey(dimension, mapData.centerX, mapData.centerZ, mapData.scale, version);
        ProjectedMap projected = PROJECTION_CACHE.get(key);
        if (projected == null) {
            projected = project(mapData, dimension, dimensionCores);
            PROJECTION_CACHE.put(key, projected);
        }
        if (projected.quadCount() == 0) return;

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        int pulsingAlpha = fallenFillAlpha();
        float[] geometry = projected.geometry();
        int[] colors = projected.colors();
        for (int index = 0; index < projected.quadCount(); index++) {
            int offset = index * 5;
            int color = colors[index];
            if (color == PULSING) color = projected.pulseRgb()[index] | pulsingAlpha << 24;
            quad(vertices, matrix, geometry[offset], geometry[offset + 1], geometry[offset + 2],
                    geometry[offset + 3], geometry[offset + 4], color);
        }
        if (!projected.labels().isEmpty() && labelsVisible(inItemFrame, matrix)) {
            renderLabels(matrix, buffers, projected.labels());
        }
    }

    /** Hand-held and screen maps always show labels; framed ones only within {@link #LABEL_DISTANCE}. */
    private static boolean labelsVisible(boolean inItemFrame, Matrix4f matrix) {
        if (!inItemFrame) return true;
        // Item-frame poses are camera-relative, so the map centre's translation is its distance.
        matrix.transformPosition(TerritoryMapProjection.MAP_SIZE / 2.0F,
                TerritoryMapProjection.MAP_SIZE / 2.0F, 0.0F, MAP_CENTER);
        return withinLabelDistance(MAP_CENTER.lengthSquared());
    }

    static boolean withinLabelDistance(double distanceSquared) {
        return distanceSquared <= LABEL_DISTANCE * LABEL_DISTANCE;
    }

    private static void renderLabels(Matrix4f pose, MultiBufferSource buffers, List<Label> labels) {
        Font font = Minecraft.getInstance().font;
        for (Label label : labels) {
            LABEL_MATRIX.set(pose).translate(label.x, label.y, CORE_Z).scale(LABEL_SCALE, LABEL_SCALE, 1.0F);
            font.drawInBatch(label.text, 0.0F, 0.0F, label.argb, true, LABEL_MATRIX, buffers,
                    Font.DisplayMode.NORMAL, 0x66000000, LightTexture.FULL_BRIGHT);
        }
    }

    private static ProjectedMap project(MapItemSavedData mapData, ResourceLocation dimension,
                                        List<S2C_TerritoryMapPacket.CoreEntry> cores) {
        QuadList quads = new QuadList();
        List<Label> labels = new ArrayList<>();
        Font font = Minecraft.getInstance().font;
        float thickness = mapData.scale >= 3 ? 1.15F : 0.72F;
        for (S2C_TerritoryMapPacket.CoreEntry core : cores) {
            TerritoryMapProjection.ProjectedRect rect = TerritoryMapProjection.projectChunks(
                    mapData.centerX, mapData.centerZ, mapData.scale,
                    core.centerChunkX(), core.centerChunkZ(), core.radius());
            if (!rect.visible()) continue;
            TerritoryMapProjection.Point marker = TerritoryMapProjection.projectBlock(
                    mapData.centerX, mapData.centerZ, mapData.scale,
                    core.centerChunkX() * 16 + 8, core.centerChunkZ() * 16 + 8);
            S2C_TerritoryMapPacket.NationEntry nation = TerritoryMapClientState.nation(dimension, core.nationId());
            S2C_TerritoryMapPacket.Relation relation = nation == null
                    ? S2C_TerritoryMapPacket.Relation.FOREIGN : nation.relation();
            int relationRgb = relationColor(relation, 0);
            if (core.state() == S2C_TerritoryMapPacket.CoreState.FALLEN) {
                quads.addPulsing(rect.minX(), rect.minY(), rect.maxX(), rect.maxY(), FILL_Z, relationRgb);
            } else {
                int alpha = core.state() == S2C_TerritoryMapPacket.CoreState.EXPOSED ? 38 : 27;
                quads.add(rect.minX(), rect.minY(), rect.maxX(), rect.maxY(), FILL_Z, relationRgb | alpha << 24);
            }
            int border = statusBorder(core.state(), relation);
            quads.add(rect.minX(), rect.minY(), rect.maxX(),
                    Math.min(rect.maxY(), rect.minY() + thickness), BORDER_Z, border);
            quads.add(rect.minX(), Math.max(rect.minY(), rect.maxY() - thickness),
                    rect.maxX(), rect.maxY(), BORDER_Z, border);
            quads.add(rect.minX(), rect.minY(),
                    Math.min(rect.maxX(), rect.minX() + thickness), rect.maxY(), BORDER_Z, border);
            quads.add(Math.max(rect.minX(), rect.maxX() - thickness), rect.minY(),
                    rect.maxX(), rect.maxY(), BORDER_Z, border);
            if (!marker.visible()) continue;
            float size = core.capital() ? 1.8F : 1.25F;
            int markerColor = core.capital() ? argb(255, 216, 80, 235) : argb(245, 248, 250, 220);
            quads.add(marker.x() - size, marker.y() - size, marker.x() + size, marker.y() + size, CORE_Z,
                    markerColor);
            if (core.capital() && nation != null) {
                String text = nation.tag().isBlank() ? nation.name()
                        : "[" + nation.tag() + "] " + nation.name();
                FormattedCharSequence sequence = Component.literal(text).getVisualOrderText();
                float width = font.width(sequence) * LABEL_SCALE;
                labels.add(new Label(marker.x() - width / 2.0F, marker.y() + 2.4F, sequence,
                        relationColor(relation, 255)));
            }
        }
        return quads.build(List.copyOf(labels));
    }

    private static int fallenFillAlpha() {
        return 30 + (int) ((Math.sin(Util.getMillis() / 180.0D) + 1.0D) * 13.0D);
    }

    private static int statusBorder(S2C_TerritoryMapPacket.CoreState state,
                                    S2C_TerritoryMapPacket.Relation relation) {
        return switch (state) {
            case EXPOSED -> argb(255, 160, 35, 235);
            case FALLEN -> argb(255, 38, 64, 245);
            case ACTIVE -> relationColor(relation, 225);
        };
    }

    private static int relationColor(S2C_TerritoryMapPacket.Relation relation, int alpha) {
        return switch (relation) {
            case OWN -> argb(32, 216, 120, alpha);
            case ALLIED -> argb(38, 198, 218, alpha);
            case HOSTILE -> argb(255, 61, 85, alpha);
            case FOREIGN -> argb(154, 160, 170, alpha);
        };
    }

    private static int argb(int red, int green, int blue, int alpha) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, float minX, float minY,
                             float maxX, float maxY, float z, int argb) {
        if (maxX <= minX || maxY <= minY) return;
        int red = argb >> 16 & 0xFF;
        int green = argb >> 8 & 0xFF;
        int blue = argb & 0xFF;
        int alpha = argb >>> 24;
        vertices.addVertex(matrix, minX, maxY, z).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, maxX, maxY, z).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, maxX, minY, z).setColor(red, green, blue, alpha);
        vertices.addVertex(matrix, minX, minY, z).setColor(red, green, blue, alpha);
    }

    /** Colour marker for a fill whose alpha pulses; its RGB lives in {@link ProjectedMap#pulseRgb}. */
    private static final int PULSING = 0;

    /** Growable flat quad storage: five floats (minX, minY, maxX, maxY, z) and one ARGB per quad. */
    private static final class QuadList {
        private float[] geometry = new float[5 * 16];
        private int[] colors = new int[16];
        private int[] pulseRgb = new int[16];
        private int count;

        void add(float minX, float minY, float maxX, float maxY, float z, int argb) {
            // A fully transparent colour would collide with the PULSING marker and draws nothing anyway.
            if (argb == PULSING || maxX <= minX || maxY <= minY) return;
            put(minX, minY, maxX, maxY, z, argb, 0);
        }

        void addPulsing(float minX, float minY, float maxX, float maxY, float z, int rgb) {
            if (maxX <= minX || maxY <= minY) return;
            put(minX, minY, maxX, maxY, z, PULSING, rgb & 0xFFFFFF);
        }

        private void put(float minX, float minY, float maxX, float maxY, float z, int argb, int rgb) {
            if (count == colors.length) {
                geometry = java.util.Arrays.copyOf(geometry, geometry.length * 2);
                colors = java.util.Arrays.copyOf(colors, colors.length * 2);
                pulseRgb = java.util.Arrays.copyOf(pulseRgb, pulseRgb.length * 2);
            }
            int offset = count * 5;
            geometry[offset] = minX;
            geometry[offset + 1] = minY;
            geometry[offset + 2] = maxX;
            geometry[offset + 3] = maxY;
            geometry[offset + 4] = z;
            colors[count] = argb;
            pulseRgb[count] = rgb;
            count++;
        }

        ProjectedMap build(List<Label> labels) {
            return new ProjectedMap(java.util.Arrays.copyOf(geometry, count * 5),
                    java.util.Arrays.copyOf(colors, count), java.util.Arrays.copyOf(pulseRgb, count),
                    count, labels);
        }
    }

    private record Label(float x, float y, FormattedCharSequence text, int argb) { }
    private record ProjectedMap(float[] geometry, int[] colors, int[] pulseRgb, int quadCount,
                                List<Label> labels) { }
    private record MapKey(ResourceLocation dimension, int centerX, int centerZ, int scale, long version) { }
}
