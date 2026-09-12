package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestReinforcementScanPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.ruskserver.moveearth_addtional.item.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class S2ClientKeys {
    public static final KeyMapping OPEN_HUB = new KeyMapping(
            "key.moveearth_addtional.s2_hub", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N, "key.categories.moveearth_addtional");
    public static final KeyMapping CLEAR_TERRITORY_PREVIEW = new KeyMapping(
            "key.moveearth_addtional.territory_preview_clear", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "key.categories.moveearth_addtional");
    public static final KeyMapping TOGGLE_REINFORCEMENT_OVERLAY = new KeyMapping(
            "key.moveearth_addtional.reinforcement_overlay", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.moveearth_addtional");
    private static int reinforcementScanTicks;
    private static boolean wasHoldingWelder;

    private S2ClientKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_HUB);
        event.register(CLEAR_TERRITORY_PREVIEW);
        event.register(TOGGLE_REINFORCEMENT_OVERLAY);
    }

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            TerritoryPreviewClientState.clear();
            VaultClientState.clear();
            ReinforcementClientState.clear();
            WeldingBrushClientState.clear();
            wasHoldingWelder = false;
            return;
        }
        while (OPEN_HUB.consumeClick()) {
            if (minecraft.player != null && minecraft.getConnection() != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW));
            }
        }
        while (CLEAR_TERRITORY_PREVIEW.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null
                    && TerritoryPreviewClientState.active()) {
                TerritoryPreviewClientState.clear();
                minecraft.player.displayClientMessage(MoveEarthMessage.info(
                        net.minecraft.network.chat.Component.translatable(
                                "message.moveearth_addtional.territory_preview.closed")), true);
            }
        }
        boolean holdingWelder = minecraft.player != null
                && minecraft.player.getMainHandItem().is(ModItems.WELDING_TOOL.get());
        if (holdingWelder && !wasHoldingWelder) {
            PacketDistributor.sendToServer(new com.ruskserver.moveearth_addtional.network.C2S_SetWeldingBrushPacket(
                    WeldingBrushClientState.radius()));
        }
        wasHoldingWelder = holdingWelder;
        while (TOGGLE_REINFORCEMENT_OVERLAY.consumeClick()) {
            if (minecraft.screen == null && holdingWelder) {
                ReinforcementClientState.toggleOverlay();
                PacketDistributor.sendToServer(new C2S_RequestReinforcementScanPacket(64));
            }
        }
        if (holdingWelder && ++reinforcementScanTicks >= 20) {
            reinforcementScanTicks = 0;
            PacketDistributor.sendToServer(new C2S_RequestReinforcementScanPacket(64));
        } else if (!holdingWelder) {
            reinforcementScanTicks = 0;
        }
    }
}
