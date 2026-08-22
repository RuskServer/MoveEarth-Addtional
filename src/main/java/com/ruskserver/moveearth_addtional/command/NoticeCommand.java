package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruskserver.moveearth_addtional.ModSounds;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_AnnouncementPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NoticeCommand {
    private static final int ADMIN_PERMISSION_LEVEL = 2;
    private static final int MAX_MESSAGE_LENGTH = 256;

    private NoticeCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("notice")
                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> broadcast(
                                context.getSource(), StringArgumentType.getString(context, "message")))));
    }

    private static int broadcast(CommandSourceStack source, String rawMessage) {
        String message = rawMessage.strip();
        if (message.isEmpty()) {
            source.sendFailure(Component.literal("通知文を入力してください。"));
            return 0;
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            source.sendFailure(Component.literal("通知文は256文字以内にしてください。"));
            return 0;
        }

        PacketDistributor.sendToAllPlayers(new S2C_AnnouncementPacket(message));
        var players = source.getServer().getPlayerList().getPlayers();
        players.forEach(player -> player.playNotifySound(
                ModSounds.SERVER_NOTICE.get(), SoundSource.MASTER, 1.0F, 1.0F));
        source.sendSuccess(() -> Component.literal(players.size() + "人へ通知を送信しました。"), true);
        return players.size();
    }
}
