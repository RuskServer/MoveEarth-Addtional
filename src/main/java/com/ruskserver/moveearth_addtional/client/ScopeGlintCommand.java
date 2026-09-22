package com.ruskserver.moveearth_addtional.client;

import com.mojang.brigadier.CommandDispatcher;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Says why a scope is or is not glinting.
 *
 * <p>The glint has several ways to be absent and they all look the same from
 * inside the game: no gun, no scope, an optic the zoom threshold does not
 * count, no sun, or simply that nobody can see their own. Reading that off one
 * at a time beats changing a number and going back out to look.
 *
 * <p>A client command, because the answer is only knowable on a client. The
 * optic's magnification lives in the gun pack's client index, and the glint is
 * drawn by each viewer for themselves; nothing about it exists on the server
 * to ask.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.GAME)
public final class ScopeGlintCommand {

    /** Far enough to cover a firing line, short enough to stay readable. */
    private static final double RANGE = 256.0D;

    private ScopeGlintCommand() { }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("moveearthglint")
                .executes(context -> report(context.getSource())));
    }

    private static int report(CommandSourceStack source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("--- scope glint ---")
                .withStyle(ChatFormatting.AQUA), false);
        int seen = 0;
        for (Player other : minecraft.level.players()) {
            if (other != minecraft.player
                    && other.distanceToSqr(minecraft.player) > RANGE * RANGE) {
                continue;
            }
            seen++;
            String line = ScopeGlintRenderer.explain(other, 1.0F);
            source.sendSuccess(() -> Component.literal("  " + line), false);
        }
        if (seen <= 1) {
            // The commonest reason for "it does not work" is testing alone,
            // and nothing about a solo test could ever show a glint.
            source.sendSuccess(() -> Component.literal(
                    "  nobody else within " + (int) RANGE + " blocks; a glint is only ever "
                            + "drawn for other players")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return seen;
    }
}
