package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.siege.S2C_WarConsentPromptPacket;
import com.ruskserver.moveearth_addtional.s2.siege.WarConsentPolicy;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Shows the war-consent prompt as a chat line with a link that opens it. It never opens by itself: the hit
 * that triggers it often lands mid-fight (a grenade splashing a neutral wall), and a screen taking the
 * controls then would cost the player the fight. A prompt screen already open is refreshed instead.
 * The link runs a client-only command, so it works without any server command.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class WarConsentClient {
    private static final String COMMAND = "moveearthwarconsent";
    private static S2C_WarConsentPromptPacket pending;
    private static long receivedAt;

    private WarConsentClient() { }

    public static void prompt(S2C_WarConsentPromptPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        pending = packet;
        receivedAt = Util.getMillis();
        if (minecraft.screen instanceof WarConsentScreen) {
            minecraft.setScreen(new WarConsentScreen(packet));
            return;
        }
        Component link = Component.translatable("message.moveearth_addtional.war_consent.open")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + COMMAND))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(
                                "message.moveearth_addtional.war_consent.open_hover"))));
        minecraft.player.displayClientMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.war_consent.deferred", packet.targetNationName())
                .append(" ").append(link)), false);
    }

    private static int openPending() {
        Minecraft minecraft = Minecraft.getInstance();
        S2C_WarConsentPromptPacket packet = pending;
        if (minecraft.player == null) return 0;
        if (packet == null || Util.getMillis() - receivedAt > WarConsentPolicy.PROMPT_VALID_MILLIS) {
            pending = null;
            minecraft.player.displayClientMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.war_consent.expired")), false);
            return 0;
        }
        // After the chat screen that ran this command has finished handling its click or input.
        minecraft.tell(() -> minecraft.setScreen(new WarConsentScreen(packet)));
        return 1;
    }

    static void resolved() {
        pending = null;
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(COMMAND).executes(context -> openPending()));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = null;
    }
}
