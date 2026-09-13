package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ruskserver.moveearth_addtional.network.S2C_TerritoryMapPacket;
import com.ruskserver.moveearth_addtional.s2.territory.TerritoryMapProjection;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Draws a lightweight territory layer between vanilla map pixels and map decorations. */
public final class TerritoryMapRenderer {
    private static final float FILL_Z = -0.014F;
    private static final float BORDER_Z = -0.017F;
    private static final float CORE_Z = -0.019F;
    private static final int MAX_CACHED_MAPS = 1024;
    private static final Map<MapKey, List<ProjectedCore>> PROJECTION_CACHE = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MapKey, List<ProjectedCore>> eldest) {
            return size() > MAX_CACHED_MAPS;
        }
    };

    private TerritoryMapRenderer() { }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, MapItemSavedData mapData) {
        TerritoryMapClientState.requestIfStale();
        if (!TerritoryMapClientState.enabled()) return;
        ResourceLocation dimension = mapData.dimension.location();
        List<S2C_TerritoryMapPacket.CoreEntry> dimensionCores = TerritoryMapClientState.cores(dimension);
        if (dimensionCores.isEmpty()) return;
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();
        List<Label> labels = new ArrayList<>();
        MapKey key = new MapKey(dimension, mapData.centerX, mapData.centerZ, mapData.scale,
                TerritoryMapClientState.version());
        List<ProjectedCore> projected = PROJECTION_CACHE.computeIfAbsent(key,
                ignored -> project(mapData, dimensionCores));
        for (ProjectedCore projectedCore : projected) {
            S2C_TerritoryMapPacket.CoreEntry core = projectedCore.core;
            TerritoryMapProjection.ProjectedRect rect = projectedCore.rect;
            S2C_TerritoryMapPacket.NationEntry nation = TerritoryMapClientState.nation(core.nationId());
            S2C_TerritoryMapPacket.Relation relation = nation == null
                    ? S2C_TerritoryMapPacket.Relation.FOREIGN : nation.relation();
            Color fill = relationColor(relation, fillAlpha(core.state()));
            Color border = statusBorder(core.state(), relation);
            quad(vertices, matrix, rect.minX(), rect.minY(), rect.maxX(), rect.maxY(), FILL_Z, fill);
            float thickness = mapData.scale >= 3 ? 1.15F : 0.72F;
            border(vertices, matrix, rect, thickness, border);
            TerritoryMapProjection.Point marker = projectedCore.marker;
            if (marker.visible()) {
                float size = core.capital() ? 1.8F : 1.25F;
                Color markerColor = core.capital() ? new Color(255, 216, 80, 235)
                        : new Color(245, 248, 250, 220);
                quad(vertices, matrix, marker.x() - size, marker.y() - size,
                        marker.x() + size, marker.y() + size, CORE_Z, markerColor);
                if (core.capital() && nation != null) {
                    String label = nation.tag().isBlank() ? nation.name()
                            : "[" + nation.tag() + "] " + nation.name();
                    labels.add(new Label(marker, label, relationColor(relation, 255)));
                }
            }
        }
        renderLabels(poseStack, buffers, labels);
    }

    private static void renderLabels(PoseStack poseStack, MultiBufferSource buffers, List<Label> labels) {
        if (labels.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        for (Label label : labels) {
            float scale = 0.42F;
            float width = font.width(label.text) * scale;
            poseStack.pushPose();
            poseStack.translate(label.point.x() - width / 2.0F, label.point.y() + 2.4F, CORE_Z);
            poseStack.scale(scale, scale, 1.0F);
            int argb = label.color.alpha << 24 | label.color.red << 16
                    | label.color.green << 8 | label.color.blue;
            font.drawInBatch(Component.literal(label.text), 0.0F, 0.0F, argb, true,
                    poseStack.last().pose(), buffers, net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                    0x66000000, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
        }
    }

    private static List<ProjectedCore> project(MapItemSavedData mapData,
                                                List<S2C_TerritoryMapPacket.CoreEntry> cores) {
        List<ProjectedCore> projected = new ArrayList<>();
        for (S2C_TerritoryMapPacket.CoreEntry core : cores) {
            TerritoryMapProjection.ProjectedRect rect = TerritoryMapProjection.projectChunks(
                    mapData.centerX, mapData.centerZ, mapData.scale,
                    core.centerChunkX(), core.centerChunkZ(), core.radius());
            if (!rect.visible()) continue;
            TerritoryMapProjection.Point marker = TerritoryMapProjection.projectBlock(
                    mapData.centerX, mapData.centerZ, mapData.scale,
                    core.centerChunkX() * 16 + 8, core.centerChunkZ() * 16 + 8);
            projected.add(new ProjectedCore(core, rect, marker));
        }
        return List.copyOf(projected);
    }

    private static int fillAlpha(S2C_TerritoryMapPacket.CoreState state) {
        if (state == S2C_TerritoryMapPacket.CoreState.FALLEN) {
            return 30 + (int) ((Math.sin(Util.getMillis() / 180.0D) + 1.0D) * 13.0D);
        }
        return state == S2C_TerritoryMapPacket.CoreState.EXPOSED ? 38 : 27;
    }

    private static Color statusBorder(S2C_TerritoryMapPacket.CoreState state,
                                      S2C_TerritoryMapPacket.Relation relation) {
        return switch (state) {
            case EXPOSED -> new Color(255, 160, 35, 235);
            case FALLEN -> new Color(255, 38, 64, 245);
            case ACTIVE -> relationColor(relation, 225);
        };
    }

    private static Color relationColor(S2C_TerritoryMapPacket.Relation relation, int alpha) {
        return switch (relation) {
            case OWN -> new Color(32, 216, 120, alpha);
            case ALLIED -> new Color(38, 198, 218, alpha);
            case HOSTILE -> new Color(255, 61, 85, alpha);
            case FOREIGN -> new Color(154, 160, 170, alpha);
        };
    }

    private static void border(VertexConsumer vertices, Matrix4f matrix,
                               TerritoryMapProjection.ProjectedRect rect, float thickness, Color color) {
        quad(vertices, matrix, rect.minX(), rect.minY(), rect.maxX(),
                Math.min(rect.maxY(), rect.minY() + thickness), BORDER_Z, color);
        quad(vertices, matrix, rect.minX(), Math.max(rect.minY(), rect.maxY() - thickness),
                rect.maxX(), rect.maxY(), BORDER_Z, color);
        quad(vertices, matrix, rect.minX(), rect.minY(),
                Math.min(rect.maxX(), rect.minX() + thickness), rect.maxY(), BORDER_Z, color);
        quad(vertices, matrix, Math.max(rect.minX(), rect.maxX() - thickness), rect.minY(),
                rect.maxX(), rect.maxY(), BORDER_Z, color);
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, float minX, float minY,
                             float maxX, float maxY, float z, Color color) {
        if (maxX <= minX || maxY <= minY) return;
        vertices.addVertex(matrix, minX, maxY, z).setColor(color.red, color.green, color.blue, color.alpha);
        vertices.addVertex(matrix, maxX, maxY, z).setColor(color.red, color.green, color.blue, color.alpha);
        vertices.addVertex(matrix, maxX, minY, z).setColor(color.red, color.green, color.blue, color.alpha);
        vertices.addVertex(matrix, minX, minY, z).setColor(color.red, color.green, color.blue, color.alpha);
    }

    private record Color(int red, int green, int blue, int alpha) { }
    private record Label(TerritoryMapProjection.Point point, String text, Color color) { }
    private record ProjectedCore(S2C_TerritoryMapPacket.CoreEntry core,
                                 TerritoryMapProjection.ProjectedRect rect,
                                 TerritoryMapProjection.Point marker) { }
    private record MapKey(ResourceLocation dimension, int centerX, int centerZ, int scale, long version) { }
}
