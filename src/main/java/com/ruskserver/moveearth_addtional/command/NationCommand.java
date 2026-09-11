package com.ruskserver.moveearth_addtional.command;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2HubTab;
import com.ruskserver.moveearth_addtional.s2.S2NationViewService;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class NationCommand {
    private NationCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nation")
                .requires(source -> source.hasPermission(0))
                .executes(context -> open(context.getSource().getPlayerOrException())));
    }

    private static int open(ServerPlayer player) {
        S2NationViewService.INSTANCE.sendHub(player, S2HubTab.OVERVIEW);
        return 1;
    }
}
