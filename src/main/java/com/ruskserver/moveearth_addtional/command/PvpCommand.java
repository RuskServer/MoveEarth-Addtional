package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_OpenPvpScreenPacket;
import com.ruskserver.moveearth_addtional.pvp.PvpArenaSavedData;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PvpCommand {
    /** Available to every player, matching the permission behavior of /stats. */
    private static final int PLAYER_PERMISSION_LEVEL = 0;
    /** Reserved for server operators who host matches or edit the arena. */
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("pvp")
                .requires(source -> source.hasPermission(PLAYER_PERMISSION_LEVEL))
                .executes(ctx -> open(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("tasks").executes(ctx -> tasks(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("crate").executes(ctx -> crate(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("admin").requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                        .then(Commands.literal("arena").executes(ctx -> arena(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("open").executes(ctx -> hosting(ctx.getSource(), true)))
                        .then(Commands.literal("close").executes(ctx -> hosting(ctx.getSource(), false)))
                        .then(Commands.literal("start").executes(ctx -> start(ctx.getSource())))
                        .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                        .then(Commands.literal("setredspawn").executes(ctx -> set(ctx.getSource().getPlayerOrException(), "red")))
                        .then(Commands.literal("setbluespawn").executes(ctx -> set(ctx.getSource().getPlayerOrException(), "blue")))
                        .then(Commands.literal("sethill1").executes(ctx -> set(ctx.getSource().getPlayerOrException(), "hill1")))
                        .then(Commands.literal("sethill2").executes(ctx -> set(ctx.getSource().getPlayerOrException(), "hill2")))));
    }

    private static int open(ServerPlayer player) {
        var rewards = com.ruskserver.moveearth_addtional.pvp.PvpRewardData.get(player.server);
        PacketDistributor.sendToPlayer(player, new S2C_OpenPvpScreenPacket(PvpMatchManager.INSTANCE.isParticipant(player),
                PvpMatchManager.INSTANCE.isActive(player),
                com.ruskserver.moveearth_addtional.pvp.PvpArenaSavedData.get(player.server).hosting(),
                rewards.points(player.getUUID()), rewards.summary(player.getUUID())));
        return 1;
    }
    private static int leave(ServerPlayer player) { PvpMatchManager.INSTANCE.leave(player); return 1; }
    private static int status(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(com.ruskserver.moveearth_addtional.pvp.PvpRewardData.get(player.server).summary(player.getUUID())));
        return 1;
    }
    private static int tasks(ServerPlayer player) {
        com.ruskserver.moveearth_addtional.pvp.PvpRewardData.get(player.server).openTaskScreen(player);
        return 1;
    }
    private static int crate(ServerPlayer player) {
        if (PvpMatchManager.INSTANCE.isActive(player)) {
            player.sendSystemMessage(Component.literal("試合中は武器箱を交換できません。"));
            return 0;
        }
        var rewards = com.ruskserver.moveearth_addtional.pvp.PvpRewardData.get(player.server);
        if (!rewards.exchangeCrate(player)) {
            player.sendSystemMessage(Component.literal("武器箱の交換には100ポイントと空きスロットが必要です。現在: " + rewards.points(player.getUUID()) + "pt"));
            return 0;
        }
        player.sendSystemMessage(Component.literal("武器箱を交換しました。右クリックで開封できます。"));
        return 1;
    }
    private static int start(CommandSourceStack source) { return PvpMatchManager.INSTANCE.start(source.getServer()) ? 1 : 0; }
    private static int stop(CommandSourceStack source) { PvpMatchManager.INSTANCE.stop(source.getServer()); return 1; }
    private static int hosting(CommandSourceStack source, boolean open) {
        com.ruskserver.moveearth_addtional.pvp.PvpArenaSavedData arenaData =
                com.ruskserver.moveearth_addtional.pvp.PvpArenaSavedData.get(source.getServer());
        if (open && !arenaData.hosting()) {
            com.ruskserver.moveearth_addtional.pvp.PvpRewardData.get(source.getServer()).beginEvent();
        }
        arenaData.setHosting(open);
        if (!open) PvpMatchManager.INSTANCE.stop(source.getServer());
        source.sendSuccess(() -> Component.literal(open ? "PvPイベントの受付を開始しました。" : "PvPイベントを終了しました。"), true);
        return 1;
    }
    private static int arena(ServerPlayer player) {
        ServerLevel arena = player.server.getLevel(PvpMatchManager.ARENA);
        if (arena == null) return 0;
        var pos = PvpArenaSavedData.get(player.server).redSpawn();
        player.teleportTo(arena, pos.getX() + .5, pos.getY(), pos.getZ() + .5, player.getYRot(), player.getXRot());
        return 1;
    }
    private static int set(ServerPlayer player, String target) {
        if (!player.level().dimension().equals(PvpMatchManager.ARENA)) {
            player.sendSystemMessage(Component.literal("PvPディメンション内で実行してください。"));
            return 0;
        }
        PvpArenaSavedData data = PvpArenaSavedData.get(player.server);
        switch (target) {
            case "red" -> data.setRedSpawn(player.blockPosition());
            case "blue" -> data.setBlueSpawn(player.blockPosition());
            case "hill1" -> data.setHillMin(player.blockPosition());
            case "hill2" -> data.setHillMax(player.blockPosition());
        }
        player.sendSystemMessage(Component.literal("PvP地点を保存しました: " + target));
        return 1;
    }

}
