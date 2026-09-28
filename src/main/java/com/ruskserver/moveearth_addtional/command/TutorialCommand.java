package com.ruskserver.moveearth_addtional.command;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.tutorial.TutorialService;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /tutorial skip} hides the first-session goals; {@code /tutorial restart} shows them again. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TutorialCommand {
    private TutorialCommand() { }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tutorial")
                .requires(source -> source.hasPermission(0))
                .then(Commands.literal("skip").executes(context -> {
                    TutorialService.skip(context.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("restart").executes(context -> {
                    TutorialService.restart(context.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
