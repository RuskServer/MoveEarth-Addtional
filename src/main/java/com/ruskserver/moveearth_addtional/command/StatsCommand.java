package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_OpenStatsScreenPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class StatsCommand {
    private StatsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stats")
                .requires(source -> source.hasPermission(0))
                .executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            var stats = player.getStats();
            int distanceCm = stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM))
                    + stats.getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM))
                    + stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                    + stats.getValue(Stats.CUSTOM.get(Stats.SWIM_ONE_CM))
                    + stats.getValue(Stats.CUSTOM.get(Stats.FLY_ONE_CM))
                    + stats.getValue(Stats.CUSTOM.get(Stats.AVIATE_ONE_CM))
                    + stats.getValue(Stats.CUSTOM.get(Stats.BOAT_ONE_CM));

            PacketDistributor.sendToPlayer(player, new S2C_OpenStatsScreenPacket(
                    player.getGameProfile().getName(),
                    stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)),
                    stats.getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS)),
                    stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)),
                    stats.getValue(Stats.CUSTOM.get(Stats.DEATHS)),
                    stats.getValue(Stats.CUSTOM.get(Stats.DAMAGE_DEALT)),
                    stats.getValue(Stats.CUSTOM.get(Stats.DAMAGE_TAKEN)),
                    distanceCm,
                    stats.getValue(Stats.CUSTOM.get(Stats.JUMP))
            ));
            return 1;
                }));
    }
}
