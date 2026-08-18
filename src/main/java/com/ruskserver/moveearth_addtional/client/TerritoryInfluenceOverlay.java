package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.C2S_RequestTerritoryInfluenceOverlayPacket;
import com.ruskserver.moveearth_addtional.network.S2C_TerritoryInfluenceOverlayPacket;
import com.ruskserver.moveearth_addtional.territory.overlay.TerritoryOverlayCell;
import com.simibubi.create.AllSpecialTextures;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class TerritoryInfluenceOverlay {
    private static final int REQUEST_INTERVAL_TICKS = 40;
    private static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.moveearth_addtional.territory_overlay",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.moveearth_addtional"
    );

    private static boolean enabled;
    private static int requestCooldown;
    private static int lastCellCount;
    private static S2C_TerritoryInfluenceOverlayPacket snapshot;

    private TerritoryInfluenceOverlay() {
    }

    public static void update(S2C_TerritoryInfluenceOverlayPacket packet) {
        if (!enabled) {
            return;
        }
        clearOutlines();
        snapshot = packet;
        lastCellCount = packet.cells().length;

        for (int index = 0; index < packet.cells().length; index++) {
            int packed = packet.cells()[index];
            int relation = TerritoryOverlayCell.relation(packed);
            if (relation == TerritoryOverlayCell.RELATION_NONE || packet.heights()[index] == Short.MIN_VALUE) {
                continue;
            }

            int xIndex = index % packet.width();
            int zIndex = index / packet.width();
            double x = packet.originX() + xIndex * packet.step();
            double z = packet.originZ() + zIndex * packet.step();
            double y = packet.heights()[index] + 0.035D;
            double inset = 0.08D;
            AABB area = new AABB(
                    x + inset, y, z + inset,
                    x + packet.step() - inset, y + 0.055D, z + packet.step() - inset
            );
            Outliner.getInstance()
                    .showAABB(new OverlaySlot(index), area, REQUEST_INTERVAL_TICKS + 15)
                    .lineWidth(1.0F / 32.0F)
                    .colored(colorFor(relation, TerritoryOverlayCell.influence(packed)))
                    .withFaceTexture(AllSpecialTextures.THIN_CHECKERED)
                    .disableLineNormals()
                    .disableCull();
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (TOGGLE_KEY.consumeClick()) {
            enabled = !enabled;
            requestCooldown = 0;
            if (!enabled) {
                snapshot = null;
                clearOutlines();
            }
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        enabled
                                ? "message.moveearth_addtional.territory_overlay.enabled"
                                : "message.moveearth_addtional.territory_overlay.disabled"), true);
            }
        }

        if (!enabled || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        if (requestCooldown-- <= 0) {
            PacketDistributor.sendToServer(new C2S_RequestTerritoryInfluenceOverlayPacket());
            requestCooldown = REQUEST_INTERVAL_TICKS;
        }
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        if (!enabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int panelWidth = 178;
        int x = graphics.guiWidth() - panelWidth - 8;
        int y = 8;
        graphics.fill(x, y, x + panelWidth, y + 48, 0xB0101218);
        graphics.drawString(minecraft.font,
                I18n.get("overlay.moveearth_addtional.territory.title"), x + 7, y + 6, 0xFFFFFFFF, true);

        if (snapshot == null || minecraft.player == null) {
            graphics.drawString(minecraft.font,
                    I18n.get("overlay.moveearth_addtional.territory.loading"), x + 7, y + 23, 0xFFAAAAAA, false);
            return;
        }

        int packed = cellAtPlayer(snapshot, minecraft.player.getBlockX(), minecraft.player.getBlockZ());
        int relation = TerritoryOverlayCell.relation(packed);
        String relationText = I18n.get(switch (relation) {
            case TerritoryOverlayCell.RELATION_FRIENDLY -> "overlay.moveearth_addtional.territory.friendly";
            case TerritoryOverlayCell.RELATION_HOSTILE -> "overlay.moveearth_addtional.territory.hostile";
            case TerritoryOverlayCell.RELATION_CONTESTED -> "overlay.moveearth_addtional.territory.contested";
            default -> "overlay.moveearth_addtional.territory.none";
        });
        int textColor = rgbFor(relation);
        graphics.drawString(minecraft.font, relationText, x + 7, y + 20, 0xFF000000 | textColor, true);
        graphics.drawString(minecraft.font,
                I18n.get("overlay.moveearth_addtional.territory.details",
                        TerritoryOverlayCell.influence(packed), TerritoryOverlayCell.protectionTier(packed)),
                x + 7, y + 34, 0xFFD0D0D0, false);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        enabled = false;
        snapshot = null;
        clearOutlines();
    }

    private static int cellAtPlayer(S2C_TerritoryInfluenceOverlayPacket packet, int playerX, int playerZ) {
        int xIndex = Math.floorDiv(playerX - packet.originX(), packet.step());
        int zIndex = Math.floorDiv(playerZ - packet.originZ(), packet.step());
        if (xIndex < 0 || xIndex >= packet.width() || zIndex < 0 || zIndex >= packet.width()) {
            return TerritoryOverlayCell.pack(TerritoryOverlayCell.RELATION_NONE, 0, 0.0D);
        }
        return packet.cells()[zIndex * packet.width() + xIndex];
    }

    private static Color colorFor(int relation, double influence) {
        int rgb = rgbFor(relation);
        double intensity = Math.min(1.0D, Math.max(0.0D, influence / 128.0D));
        int alpha = 45 + (int) Math.round(intensity * 90.0D);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }

    private static int rgbFor(int relation) {
        return switch (relation) {
            case TerritoryOverlayCell.RELATION_FRIENDLY -> 0x55FF88;
            case TerritoryOverlayCell.RELATION_HOSTILE -> 0xFF5555;
            case TerritoryOverlayCell.RELATION_CONTESTED -> 0xFFBB33;
            default -> 0xAAAAAA;
        };
    }

    private static void clearOutlines() {
        Outliner outliner = Outliner.getInstance();
        for (int index = 0; index < lastCellCount; index++) {
            outliner.remove(new OverlaySlot(index));
        }
        lastCellCount = 0;
    }

    private record OverlaySlot(int index) {
    }

    @EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_KEY);
        }
    }
}
