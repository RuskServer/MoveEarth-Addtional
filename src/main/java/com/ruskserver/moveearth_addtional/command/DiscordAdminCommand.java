package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.notification.DiscordLinkGateway;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Token-free server-side Discord lifecycle and outbox diagnostics. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class DiscordAdminCommand {
    private DiscordAdminCommand() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("moveearth")
                .then(Commands.literal("discord")
                        .requires(source -> source.hasPermission(3))
                        .then(Commands.literal("status").executes(context -> status(context.getSource())))
                        .then(Commands.literal("reconnect").executes(context -> reconnect(context.getSource())))));
    }

    private static int status(CommandSourceStack source) {
        NationNotificationSavedData data = NationNotificationSavedData.get(source.getServer());
        var stats = data.deliveryStats();
        String state = DiscordLinkGateway.access().state().name().toLowerCase(java.util.Locale.ROOT);
        source.sendSuccess(() -> MoveEarthMessage.info("Discord: " + state
                + " | nations=" + data.linkedCount() + " | pending=" + stats.pending()
                + " | failed=" + stats.failedAttempts() + " | dropped=" + stats.dropped()
                + " | expired=" + stats.expired()), false);
        return 1;
    }

    private static int reconnect(CommandSourceStack source) {
        boolean requested = DiscordLinkGateway.access().reconnect();
        source.sendSuccess(() -> MoveEarthMessage.info(requested
                ? "Discord reconnect requested; use /moveearth discord status to verify."
                : "Discord reconnect unavailable (disabled, player build, or server not ready)."), false);
        return requested ? 1 : 0;
    }
}
