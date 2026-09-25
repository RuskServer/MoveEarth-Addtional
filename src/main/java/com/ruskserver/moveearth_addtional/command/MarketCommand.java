package com.ruskserver.moveearth_addtional.command;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.MarketScreenSync;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MarketCommand {
    private MarketCommand() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("market").requires(source -> source.isPlayer())
                .executes(context -> {
                    MarketScreenSync.open(context.getSource().getPlayerOrException(), MarketScreenSync.NONE);
                    return 1;
                }));
    }
}
