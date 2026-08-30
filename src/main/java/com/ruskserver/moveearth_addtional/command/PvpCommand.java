package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.S2C_OpenPvpScreenPacket;
import com.ruskserver.moveearth_addtional.pvp.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class PvpCommand {
    private static final int PLAYER_PERMISSION_LEVEL = 0;
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
                        .then(Commands.literal("loadout")
                                .executes(ctx -> openLoadoutEditor(ctx.getSource().getPlayerOrException()))
                                .then(Commands.literal("editor").executes(ctx -> openLoadoutEditor(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("reset").executes(ctx -> resetLoadouts(ctx.getSource())))
                                .then(Commands.literal("list").executes(ctx -> listLoadouts(ctx.getSource()))))
                        .then(Commands.literal("map")
                                .then(Commands.literal("list").executes(ctx -> listMaps(ctx.getSource())))
                                .then(Commands.literal("info")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> infoMap(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id")))))
                                .then(Commands.literal("create")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> createMap(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "name"))))))
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> deleteMap(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id")))))
                                .then(Commands.literal("setredspawn")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> setMapLocation(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id"), "red"))))
                                .then(Commands.literal("setbluespawn")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> setMapLocation(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id"), "blue"))))
                                .then(Commands.literal("addredspawn")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> addMapSpawn(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id"), true))))
                                .then(Commands.literal("addbluespawn")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> addMapSpawn(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id"), false))))
                                .then(Commands.literal("clearspawns")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> clearMapSpawns(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id"), "all"))
                                                .then(Commands.argument("team", StringArgumentType.word())
                                                        .executes(ctx -> clearMapSpawns(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "team"))))))
                                .then(Commands.literal("sethill1")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> setMapLocation(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id"), "hill1"))))
                                .then(Commands.literal("sethill2")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> setMapLocation(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id"), "hill2"))))
                                .then(Commands.literal("setdesc")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                                        .executes(ctx -> setMapDescription(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "description"))))))
                                .then(Commands.literal("tp")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> tpMap(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id")))))
                                .then(Commands.literal("vote")
                                        .executes(ctx -> forceVote(ctx.getSource(), 15))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 60))
                                                .executes(ctx -> forceVote(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "seconds"))))))));
    }

    private static int open(ServerPlayer player) {
        var rewards = PvpRewardData.get(player.server);
        PacketDistributor.sendToPlayer(player, new S2C_OpenPvpScreenPacket(PvpMatchManager.INSTANCE.isParticipant(player),
                PvpMatchManager.INSTANCE.isActive(player),
                PvpArenaSavedData.get(player.server).hosting(),
                PvpMatchManager.INSTANCE.phase() == PvpPhase.RUNNING,
                PvpMatchManager.INSTANCE.participantCount(),
                rewards.points(player.getUUID()), rewards.summary(player.getUUID()),
                PvpMatchManager.INSTANCE.selectedLoadout(player).id()));
        return 1;
    }

    private static int leave(ServerPlayer player) { PvpMatchManager.INSTANCE.leave(player); return 1; }
    private static int status(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(PvpRewardData.get(player.server).summary(player.getUUID())));
        return 1;
    }
    private static int tasks(ServerPlayer player) {
        PvpRewardData.get(player.server).openTaskScreen(player);
        return 1;
    }
    private static int crate(ServerPlayer player) {
        if (PvpMatchManager.INSTANCE.isActive(player)) {
            player.sendSystemMessage(Component.literal("§c試合中は武器箱を交換できません。"));
            return 0;
        }
        var rewards = PvpRewardData.get(player.server);
        if (!rewards.exchangeCrate(player)) {
            player.sendSystemMessage(Component.literal("§c武器箱の交換には100ポイントと空きスロットが必要です。現在: " + rewards.points(player.getUUID()) + "pt"));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§a武器箱を交換しました。右クリックで開封できます。"));
        return 1;
    }

    private static int start(CommandSourceStack source) { return PvpMatchManager.INSTANCE.start(source.getServer()) ? 1 : 0; }
    private static int stop(CommandSourceStack source) { PvpMatchManager.INSTANCE.stop(source.getServer()); return 1; }

    private static int hosting(CommandSourceStack source, boolean open) {
        PvpArenaSavedData arenaData = PvpArenaSavedData.get(source.getServer());
        if (open && !arenaData.hosting()) {
            PvpRewardData.get(source.getServer()).beginEvent();
        }
        arenaData.setHosting(open);
        if (!open) PvpMatchManager.INSTANCE.stop(source.getServer());
        else PvpMatchManager.INSTANCE.syncEntryState(source.getServer());
        source.sendSuccess(() -> Component.literal(open ? "§aPvPイベントの受付を開始しました。" : "§6PvPイベントを終了しました。"), true);
        return 1;
    }

    private static int arena(ServerPlayer player) {
        ServerLevel arena = player.server.getLevel(PvpMatchManager.ARENA);
        if (arena == null) return 0;
        PvpMapDefinition defMap = PvpMapSavedData.get(player.server).getOrDefault("default");
        var pos = defMap.redSpawn();
        player.teleportTo(arena, pos.getX() + .5, pos.getY(), pos.getZ() + .5, player.getYRot(), player.getXRot());
        return 1;
    }

    private static int openLoadoutEditor(ServerPlayer player) {
        var data = PvpLoadoutSavedData.get(player.server);
        PacketDistributor.sendToPlayer(player, new com.ruskserver.moveearth_addtional.network.S2C_OpenLoadoutEditorPacket(data.getAll()));
        return 1;
    }

    private static int resetLoadouts(CommandSourceStack source) {
        var data = PvpLoadoutSavedData.get(source.getServer());
        data.resetToDefaults();
        PacketDistributor.sendToAllPlayers(new com.ruskserver.moveearth_addtional.network.S2C_SyncLoadoutsPacket(data.getAll()));
        source.sendSuccess(() -> Component.literal("§aPvPロードアウトを初期プリセットにリセットしました。"), true);
        return 1;
    }

    private static int listLoadouts(CommandSourceStack source) {
        var data = PvpLoadoutSavedData.get(source.getServer());
        source.sendSuccess(() -> Component.literal("§6=== PvP 登録ロードアウト一覧 (" + data.getAll().size() + "件) ==="), false);
        int i = 1;
        for (var loadout : data.getAll()) {
            final int index = i++;
            source.sendSuccess(() -> Component.literal("§e" + index + ". §b" + loadout.id() + " §f(" + loadout.displayName() + ") §7- " + loadout.weaponSummary()), false);
        }
        return 1;
    }

    /* マップ管理コマンド群 */
    private static int listMaps(CommandSourceStack source) {
        var data = PvpMapSavedData.get(source.getServer());
        List<PvpMapDefinition> maps = data.getAll();
        source.sendSuccess(() -> Component.literal("§6=== PvP 登録マップ一覧 (" + maps.size() + "件) ==="), false);
        int i = 1;
        for (PvpMapDefinition map : maps) {
            final int index = i++;
            String status = map.isConfigured() ? "§a[設定完了]" : "§c[設定未完了]";
            int totalRed = map.allRedSpawns().size();
            int totalBlue = map.allBlueSpawns().size();
            source.sendSuccess(() -> Component.literal("§e" + index + ". §b" + map.id() + " §f(" + map.displayName() + ") " + status
                    + " §7[RED: " + totalRed + "箇所, BLUE: " + totalBlue + "箇所] - " + (map.description().isEmpty() ? "説明なし" : map.description())), false);
        }
        return 1;
    }

    private static int infoMap(CommandSourceStack source, String id) {
        var data = PvpMapSavedData.get(source.getServer());
        PvpMapDefinition map = data.getById(id).orElse(null);
        if (map == null) {
            source.sendFailure(Component.literal("§cマップID '" + id + "' が見つかりません。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§6=== マップ詳細: §b" + map.displayName() + " §7(" + map.id() + ") ==="), false);
        source.sendSuccess(() -> Component.literal("§e説明: §f" + (map.description().isEmpty() ? "なし" : map.description())), false);
        source.sendSuccess(() -> Component.literal("§cREDメインスポーン: §f" + map.redSpawn().toShortString()), false);
        if (!map.extraRedSpawns().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§cRED追加スポーン (" + map.extraRedSpawns().size() + "箇所):"), false);
            for (int i = 0; i < map.extraRedSpawns().size(); i++) {
                final int idx = i + 1;
                final var p = map.extraRedSpawns().get(i);
                source.sendSuccess(() -> Component.literal("  §7#" + idx + ": " + p.toShortString()), false);
            }
        }
        source.sendSuccess(() -> Component.literal("§9BLUEメインスポーン: §f" + map.blueSpawn().toShortString()), false);
        if (!map.extraBlueSpawns().isEmpty()) {
            source.sendSuccess(() -> Component.literal("§9BLUE追加スポーン (" + map.extraBlueSpawns().size() + "箇所):"), false);
            for (int i = 0; i < map.extraBlueSpawns().size(); i++) {
                final int idx = i + 1;
                final var p = map.extraBlueSpawns().get(i);
                source.sendSuccess(() -> Component.literal("  §7#" + idx + ": " + p.toShortString()), false);
            }
        }
        source.sendSuccess(() -> Component.literal("§a拠点エリア: §f" + map.hillMin().toShortString() + " ~ " + map.hillMax().toShortString()), false);
        return 1;
    }

    private static int createMap(CommandSourceStack source, String id, String name) {
        var data = PvpMapSavedData.get(source.getServer());
        if (data.getById(id).isPresent()) {
            source.sendFailure(Component.literal("§cマップID '" + id + "' は既に存在します。"));
            return 0;
        }
        PvpMapDefinition map = new PvpMapDefinition(id, name, "", null, null, null, null, null, null, PvpMapDefinition.DEFAULT_COLOR, true);
        data.addOrUpdate(map);
        source.sendSuccess(() -> Component.literal("§aマップ '" + id + "' (" + name + ") を作成しました。setredspawn, setbluespawn, sethill1, sethill2 で各座標を設定してください。"), true);
        return 1;
    }

    private static int deleteMap(CommandSourceStack source, String id) {
        var data = PvpMapSavedData.get(source.getServer());
        if (!data.delete(id)) {
            source.sendFailure(Component.literal("§cマップ '" + id + "' を削除できませんでした（存在しないか、最後の1つのため削除不可）。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§aマップ '" + id + "' を削除しました。"), true);
        return 1;
    }

    private static int setMapLocation(ServerPlayer player, String id, String target) {
        if (!player.level().dimension().equals(PvpMatchManager.ARENA)) {
            player.sendSystemMessage(Component.literal("§cPvPアリーナディメンション内で実行してください。"));
            return 0;
        }
        var data = PvpMapSavedData.get(player.server);
        PvpMapDefinition map = data.getById(id).orElse(null);
        if (map == null) {
            player.sendSystemMessage(Component.literal("§cマップID '" + id + "' が見つかりません。"));
            return 0;
        }
        switch (target) {
            case "red" -> map = map.withRedSpawn(player.blockPosition());
            case "blue" -> map = map.withBlueSpawn(player.blockPosition());
            case "hill1" -> map = map.withHillMin(player.blockPosition());
            case "hill2" -> map = map.withHillMax(player.blockPosition());
        }
        data.addOrUpdate(map);
        player.sendSystemMessage(Component.literal("§aマップ [" + map.displayName() + "] の " + target + " 地点を保存しました: " + player.blockPosition().toShortString()));
        return 1;
    }

    private static int addMapSpawn(ServerPlayer player, String id, boolean isRed) {
        if (!player.level().dimension().equals(PvpMatchManager.ARENA)) {
            player.sendSystemMessage(Component.literal("§cPvPアリーナディメンション内で実行してください。"));
            return 0;
        }
        var data = PvpMapSavedData.get(player.server);
        PvpMapDefinition map = data.getById(id).orElse(null);
        if (map == null) {
            player.sendSystemMessage(Component.literal("§cマップID '" + id + "' が見つかりません。"));
            return 0;
        }
        map = isRed ? map.withAddedRedSpawn(player.blockPosition()) : map.withAddedBlueSpawn(player.blockPosition());
        data.addOrUpdate(map);
        int total = isRed ? map.allRedSpawns().size() : map.allBlueSpawns().size();
        String teamName = isRed ? "§cRED" : "§9BLUE";
        player.sendSystemMessage(Component.literal("§aマップ [" + map.displayName() + "] に " + teamName + " 追加スポーン地点を登録しました (計 " + total + "箇所): " + player.blockPosition().toShortString()));
        return 1;
    }

    private static int clearMapSpawns(CommandSourceStack source, String id, String team) {
        var data = PvpMapSavedData.get(source.getServer());
        PvpMapDefinition map = data.getById(id).orElse(null);
        if (map == null) {
            source.sendFailure(Component.literal("§cマップID '" + id + "' が見つかりません。"));
            return 0;
        }
        boolean clearRed = "all".equalsIgnoreCase(team) || "red".equalsIgnoreCase(team);
        boolean clearBlue = "all".equalsIgnoreCase(team) || "blue".equalsIgnoreCase(team);
        PvpMapDefinition updated = map.withClearedExtraSpawns(clearRed, clearBlue);
        data.addOrUpdate(updated);
        source.sendSuccess(() -> Component.literal("§aマップ [" + updated.displayName() + "] の追加スポーン地点をクリアしました (" + team + ")。"), true);
        return 1;
    }

    private static int setMapDescription(CommandSourceStack source, String id, String desc) {
        var data = PvpMapSavedData.get(source.getServer());
        PvpMapDefinition map = data.getById(id).orElse(null);
        if (map == null) {
            source.sendFailure(Component.literal("§cマップID '" + id + "' が見つかりません。"));
            return 0;
        }
        PvpMapDefinition updated = map.withDescription(desc);
        data.addOrUpdate(updated);
        source.sendSuccess(() -> Component.literal("§aマップ [" + updated.displayName() + "] の説明文を更新しました: " + desc), true);
        return 1;
    }

    private static int tpMap(ServerPlayer player, String id) {
        ServerLevel arena = player.server.getLevel(PvpMatchManager.ARENA);
        if (arena == null) return 0;
        var data = PvpMapSavedData.get(player.server);
        PvpMapDefinition map = data.getById(id).orElse(null);
        if (map == null) {
            player.sendSystemMessage(Component.literal("§cマップID '" + id + "' が見つかりません。"));
            return 0;
        }
        var pos = map.redSpawn();
        player.teleportTo(arena, pos.getX() + .5, pos.getY(), pos.getZ() + .5, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("§aマップ [" + map.displayName() + "] のREDスポーン地点へテレポートしました。"));
        return 1;
    }

    private static int forceVote(CommandSourceStack source, int seconds) {
        var maps = PvpMapSavedData.get(source.getServer()).getEnabledAndConfigured();
        if (maps.isEmpty()) {
            source.sendFailure(Component.literal("§c有効なマップが存在しません。"));
            return 0;
        }
        PvpMapVoteManager.INSTANCE.startVote(source.getServer(),
                PvpMatchManager.INSTANCE.isParticipant(source.getPlayer()) ? Set.of(source.getPlayer().getUUID()) : Set.of(),
                maps, seconds);
        source.sendSuccess(() -> Component.literal("§aマップ投票を開始しました（" + seconds + "秒）。"), true);
        return 1;
    }
}
