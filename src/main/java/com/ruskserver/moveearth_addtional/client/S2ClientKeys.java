package com.ruskserver.moveearth_addtional.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ruskserver.moveearth_addtional.network.C2S_RequestS2HubPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestReinforcementScanPacket;
import com.ruskserver.moveearth_addtional.network.C2S_RequestPrisonerScreenPacket;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.ruskserver.moveearth_addtional.item.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
    public static final KeyMapping TOGGLE_MAP_TERRITORIES = new KeyMapping(
            "key.moveearth_addtional.map_territories", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, "key.categories.moveearth_addtional");
    public static final KeyMapping OPEN_PRISONERS = new KeyMapping(
            "key.moveearth_addtional.prisoners", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, "key.categories.moveearth_addtional");
    private static int reinforcementScanTicks;
    private static boolean wasHoldingWelder;
    private static net.minecraft.core.BlockPos lastReinforcementScanPos;
    private static net.minecraft.resources.ResourceLocation lastReinforcementScanDimension;

    private S2ClientKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_HUB);
        event.register(CLEAR_TERRITORY_PREVIEW);
        event.register(TOGGLE_REINFORCEMENT_OVERLAY);
        event.register(TOGGLE_MAP_TERRITORIES);
        event.register(OPEN_PRISONERS);
    }

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            TerritoryPreviewClientState.clear();
            VaultClientState.clear();
            ReinforcementClientState.clear();
            TerritoryMapClientState.clear();
            WeldingBrushClientState.clear();
            wasHoldingWelder = false;
            lastReinforcementScanPos = null;
            lastReinforcementScanDimension = null;
            return;
        }
        while (OPEN_HUB.consumeClick()) {
            if (minecraft.player != null && minecraft.getConnection() != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new C2S_RequestS2HubPacket(S2HubTab.OVERVIEW));
            }
        }
        while (OPEN_PRISONERS.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new C2S_RequestPrisonerScreenPacket(null));
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
            requestReinforcementScan(minecraft);
        }
        wasHoldingWelder = holdingWelder;
        while (TOGGLE_REINFORCEMENT_OVERLAY.consumeClick()) {
            if (minecraft.screen == null && holdingWelder) {
                ReinforcementClientState.toggleOverlay();
                requestReinforcementScan(minecraft);
            }
        }
        while (TOGGLE_MAP_TERRITORIES.consumeClick()) {
            boolean enabled = TerritoryMapClientState.toggle();
            if (minecraft.player != null) minecraft.player.displayClientMessage(MoveEarthMessage.info(
                    Component.translatable(enabled
                            ? "message.moveearth_addtional.map_territories.enabled"
                            : "message.moveearth_addtional.map_territories.disabled")), true);
        }
        if (holdingWelder && minecraft.player != null) {
            reinforcementScanTicks++;
            var currentPos = minecraft.player.blockPosition();
            var currentDimension = minecraft.player.level().dimension().location();
            boolean moved = lastReinforcementScanPos == null
                    || lastReinforcementScanPos.distSqr(currentPos) >= 64.0D;
            boolean changedDimension = !currentDimension.equals(lastReinforcementScanDimension);
            if (moved || changedDimension || reinforcementScanTicks >= 200) {
                requestReinforcementScan(minecraft);
            }
        } else if (!holdingWelder) {
            reinforcementScanTicks = 0;
            lastReinforcementScanPos = null;
            lastReinforcementScanDimension = null;
        }
    }

    private static void requestReinforcementScan(Minecraft minecraft) {
        if (minecraft.player == null) return;
        reinforcementScanTicks = 0;
        lastReinforcementScanPos = minecraft.player.blockPosition().immutable();
        lastReinforcementScanDimension = minecraft.player.level().dimension().location();
        PacketDistributor.sendToServer(new C2S_RequestReinforcementScanPacket(64));
    }
}
