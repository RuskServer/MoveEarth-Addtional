package com.ruskserver.moveearth_addtional.command;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.BalanceScreenSync;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Player-facing wallet; operator diagnostics remain under /moveeartheconomy. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class BalanceCommand {
    private BalanceCommand() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("balance").requires(source -> source.isPlayer())
                .executes(context -> {
                    BalanceScreenSync.open(context.getSource().getPlayerOrException());
                    return 1;
                }));
    }
}
