package com.ruskserver.moveearth_addtional.client.pvp;

import com.mojang.blaze3d.platform.NativeImage;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpAllyPositionsPacket;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpTacticalMapPacket;
import com.ruskserver.moveearth_addtional.pvp.deploy.PvpTacticalMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.List;

/**
 * The deploy screen's map: the tactical map the server sent, painted once into a texture as a hologram. The
 * terrain's own colours are pulled toward cyan by their brightness, slopes are shaded from the north-west, and
 * a contour line is drawn every few blocks of height, so the ground reads as a sci-fi display rather than a
 * satellite photo.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class PvpTacticalClient {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_tactical_map");
    private static final int CONTOUR_STEP = 4;
    private static final int HOLO = 0x2FD8FF;

    private static PvpTacticalMap map;
    private static DynamicTexture texture;
    private static List<S2C_PvpAllyPositionsPacket.Ally> allies = List.of();

    private PvpTacticalClient() {
    }

    public static PvpTacticalMap map() {
        return map;
    }

    /** The painted map, or null before one arrives. */
    public static ResourceLocation texture() {
        return texture == null ? null : TEXTURE;
    }

    public static List<S2C_PvpAllyPositionsPacket.Ally> allies() {
        return allies;
    }

    public static void updateAllies(List<S2C_PvpAllyPositionsPacket.Ally> next) {
        allies = List.copyOf(next);
    }

    public static void accept(S2C_PvpTacticalMapPacket packet) {
        if (map != null && map.mapId().equals(packet.mapId()) && map.version() == packet.version() && texture != null) {
            return;
        }
        PvpTacticalMap next;
        try {
            next = PvpTacticalMap.fromCompressed(packet.mapId(), packet.version(), packet.originX(), packet.originZ(),
                    packet.cellSize(), packet.width(), packet.depth(), packet.baseY(), packet.data());
        } catch (IllegalArgumentException corrupt) {
            Moveearth_addtional.LOGGER.warn("Ignoring a damaged PvP tactical map: {}", corrupt.getMessage());
            return;
        }
        NativeImage image = new NativeImage(next.width(), next.depth(), true);
        for (int z = 0; z < next.depth(); z++) {
            for (int x = 0; x < next.width(); x++) {
                image.setPixelRGBA(x, z, abgr(paint(next, x, z)));
            }
        }
        release();
        map = next;
        texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
    }

    /** One cell's colour as ARGB. */
    static int paint(PvpTacticalMap map, int x, int z) {
        int id = map.color(x, z);
        if (id == 0) return 0x60061420;
        int rgb = MapColor.byId(id).col;
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        float light = (0.30F * r + 0.59F * g + 0.11F * b) / 255.0F;
        int height = map.height(x, z);
        int west = x > 0 ? map.height(x - 1, z) : height;
        int north = z > 0 ? map.height(x, z - 1) : height;
        float shade = clamp(1.0F + (height - west) * 0.07F + (height - north) * 0.07F, 0.55F, 1.45F);
        boolean water = id == MapColor.WATER.id;
        int holo = water ? 0x1B5FD6 : HOLO;
        float glow = water ? 0.45F : 0.25F + 0.75F * light;
        float keep = 0.22F;
        int outR = channel(((holo >> 16 & 0xFF) * glow * (1 - keep) + r * keep) * shade);
        int outG = channel(((holo >> 8 & 0xFF) * glow * (1 - keep) + g * keep) * shade);
        int outB = channel(((holo & 0xFF) * glow * (1 - keep) + b * keep) * shade);
        boolean contour = Math.floorDiv(height, CONTOUR_STEP) != Math.floorDiv(west, CONTOUR_STEP)
                || Math.floorDiv(height, CONTOUR_STEP) != Math.floorDiv(north, CONTOUR_STEP);
        if (contour && !water) {
            outR = channel(outR + 70);
            outG = channel(outG + 90);
            outB = channel(outB + 90);
        }
        return 0xE8000000 | outR << 16 | outG << 8 | outB;
    }

    private static int abgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF;
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void release() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            texture = null;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        release();
        map = null;
        allies = List.of();
    }
}
