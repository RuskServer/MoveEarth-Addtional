package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_OpenPvpScreenPacket;
import com.ruskserver.moveearth_addtional.pvp.*;
import com.ruskserver.moveearth_addtional.pvp.deploy.*;
import com.ruskserver.moveearth_addtional.pvp.loadout.*;
import com.ruskserver.moveearth_addtional.pvp.map.*;
import com.ruskserver.moveearth_addtional.pvp.reward.*;
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
                .then(Commands.literal("burner").executes(ctx -> exchange(ctx.getSource().getPlayerOrException(),
                        PvpExchange.BLAZE_BURNER)))
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
                                .then(Commands.literal("setbounds1")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> setBounds(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id"), true))))
                                .then(Commands.literal("setbounds2")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> setBounds(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id"), false))))
                                .then(Commands.literal("clearbounds")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> clearBounds(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id")))))
                                .then(Commands.literal("wand")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> giveWand(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id")))))
                                .then(Commands.literal("namepoint")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                        .executes(ctx -> namePoint(ctx.getSource().getPlayerOrException(),
                                                                StringArgumentType.getString(ctx, "id"),
                                                                StringArgumentType.getString(ctx, "name"))))))
                                .then(Commands.literal("scan")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ctx -> scanMap(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "id")))))
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
        player.sendSystemMessage(MoveEarthMessage.info(
                PvpRewardData.get(player.server).summary(player.getUUID())));
        return 1;
    }
    private static int tasks(ServerPlayer player) {
        PvpRewardData.get(player.server).openTaskScreen(player);
        return 1;
    }
    private static int exchange(ServerPlayer player, PvpExchange exchange) {
        if (PvpMatchManager.INSTANCE.isActive(player)) {
            player.sendSystemMessage(MoveEarthMessage.error("試合中はポイント交換できません。"));
            return 0;
        }
        var rewards = PvpRewardData.get(player.server);
        if (!rewards.exchange(player, exchange)) {
            player.sendSystemMessage(MoveEarthMessage.error(exchange.displayName() + "の交換には" + exchange.cost()
                    + "ポイントが必要です。現在: " + rewards.points(player.getUUID()) + "pt"));
            return 0;
        }
        player.sendSystemMessage(MoveEarthMessage.success(exchange.displayName() + "と交換しました（"
                + exchange.cost() + "pt）。"));
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
        PacketDistributor.sendToPlayer(player, new com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_OpenLoadoutEditorPacket(data.getAll()));
        return 1;
    }

    private static int resetLoadouts(CommandSourceStack source) {
        var data = PvpLoadoutSavedData.get(source.getServer());
        data.resetToDefaults();
        PacketDistributor.sendToAllPlayers(new com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_SyncLoadoutsPacket(data.getAll()));
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
            java.util.List<String> issues = map.setupIssues();
            String status = issues.isEmpty() ? "§a[設定完了]" : "§c[設定未完了: " + issues.getFirst()
                    + (issues.size() > 1 ? " ほか" + (issues.size() - 1) + "件" : "") + "]";
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
        PvpMapGeometry.Box box = map.bounds();
        source.sendSuccess(() -> Component.literal("§b戦闘エリア" + (map.hasOwnBounds() ? "" : " §e(自動・要確認)") + ": §f"
                + box.minX() + " " + box.minY() + " " + box.minZ() + " ~ " + box.maxX() + " " + box.maxY() + " " + box.maxZ()
                + " §7(" + box.width() + "×" + box.depth() + ")"), false);
        for (PvpMapDefinition.DeployPoint point : map.deployPoints()) {
            source.sendSuccess(() -> Component.literal("  " + (point.team() == PvpTeam.RED ? "§c" : "§9") + point.name()
                    + " §7" + point.pos().toShortString()), false);
        }
        var tactical = PvpTacticalSavedData.get(source.getServer()).get(map.id());
        source.sendSuccess(() -> Component.literal("§b戦術マップ: §f" + (PvpTacticalScanner.INSTANCE.scanning(map.id()) ? "作成中"
                : tactical.map(t -> "作成済み (" + t.width() + "×" + t.depth() + "マス、1マス" + t.cellSize() + "ブロック)")
                .orElse("§c未作成 §7(/pvp admin map scan " + map.id() + ")"))), false);
        for (String issue : map.setupIssues()) {
            source.sendSuccess(() -> Component.literal("§c未完了: " + issue), false);
        }
        return 1;
    }

    private static int setBounds(ServerPlayer player, String id, boolean first) {
        return PvpMapEditing.setCorner(player, id, first, player.blockPosition()) ? 1 : 0;
    }

    private static int clearBounds(CommandSourceStack source, String id) {
        var data = PvpMapSavedData.get(source.getServer());
        PvpMapDefinition map = data.getById(id).orElse(null);
        if (map == null) {
            source.sendFailure(Component.literal("§cマップID '" + id + "' が見つかりません。"));
            return 0;
        }
        PvpMapDefinition updated = map.withBoundsCorner(true, null).withBoundsCorner(false, null);
        data.addOrUpdate(updated);
        PvpTacticalScanner.INSTANCE.request(updated, source.getPlayer() == null ? null : source.getPlayer().getUUID());
        source.sendSuccess(() -> Component.literal("§aマップ [" + map.displayName() + "] の戦闘エリアを消し、スポーンと丘から自動で決めるようにしました。"), true);
        return 1;
    }

    private static int giveWand(ServerPlayer player, String id) {
        if (PvpMapSavedData.get(player.server).getById(id).isEmpty()) {
            player.sendSystemMessage(MoveEarthMessage.error("マップID '" + id + "' が見つかりません。"));
            return 0;
        }
        var wand = com.ruskserver.moveearth_addtional.item.PvpMapWandItem.forMap(id);
        if (!player.getInventory().add(wand)) player.drop(wand, false);
        player.sendSystemMessage(MoveEarthMessage.success("マップ [" + id + "] の範囲指定ワンドを渡しました。左クリックで角1、右クリックで角2です。"));
        return 1;
    }

    /** Names the deploy point nearest the player, within a few blocks. */
    private static int namePoint(ServerPlayer player, String id, String name) {
        var data = PvpMapSavedData.get(player.server);
        PvpMapDefinition map = data.getById(id).orElse(null);
        if (map == null) {
            player.sendSystemMessage(MoveEarthMessage.error("マップID '" + id + "' が見つかりません。"));
            return 0;
        }
        if (name.strip().length() > PvpMapDefinition.MAX_POINT_NAME) {
            player.sendSystemMessage(MoveEarthMessage.error("名前は" + PvpMapDefinition.MAX_POINT_NAME + "文字までです。"));
            return 0;
        }
        PvpMapDefinition.DeployPoint nearest = map.deployPoints().stream()
                .min(java.util.Comparator.comparingDouble(point -> point.pos().distSqr(player.blockPosition())))
                .filter(point -> point.pos().distSqr(player.blockPosition()) <= 36.0D)
                .orElse(null);
        if (nearest == null) {
            player.sendSystemMessage(MoveEarthMessage.error("6ブロック以内に出撃地点がありません。名前を付けたい地点の上に立ってください。"));
            return 0;
        }
        data.addOrUpdate(map.withPointName(nearest.pos(), name));
        player.sendSystemMessage(MoveEarthMessage.success("出撃地点 " + nearest.pos().toShortString() + " を「" + name.strip() + "」と名付けました。"));
        return 1;
    }

    private static int scanMap(CommandSourceStack source, String id) {
        PvpMapDefinition map = PvpMapSavedData.get(source.getServer()).getById(id).orElse(null);
        if (map == null) {
            source.sendFailure(Component.literal("§cマップID '" + id + "' が見つかりません。"));
            return 0;
        }
        if (map.bounds().tooLarge()) {
            source.sendFailure(Component.literal("§c戦闘エリアが広すぎるため戦術マップを作れません。"));
            return 0;
        }
        PvpTacticalScanner.INSTANCE.request(map, source.getPlayer() == null ? null : source.getPlayer().getUUID());
        source.sendSuccess(() -> Component.literal("§aマップ [" + map.displayName() + "] の戦術マップを作成しています…"), true);
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
        source.sendSuccess(() -> Component.literal("§aマップ '" + id + "' (" + name + ") を作成しました。setredspawn, setbluespawn, sethill1, sethill2 で各座標を、wand（または setbounds1/2）で戦闘エリアを設定してください。"), true);
        return 1;
    }

    private static int deleteMap(CommandSourceStack source, String id) {
        var data = PvpMapSavedData.get(source.getServer());
        if (!data.delete(id)) {
            source.sendFailure(Component.literal("§cマップ '" + id + "' を削除できませんでした（存在しないか、最後の1つのため削除不可）。"));
            return 0;
        }
        PvpTacticalSavedData.get(source.getServer()).remove(id.toLowerCase(java.util.Locale.ROOT));
        source.sendSuccess(() -> Component.literal("§aマップ '" + id + "' を削除しました。"), true);
        return 1;
    }

    private static int setMapLocation(ServerPlayer player, String id, String target) {
        if (!player.level().dimension().equals(PvpMatchManager.ARENA)) {
            player.sendSystemMessage(MoveEarthMessage.error("PvPアリーナディメンション内で実行してください。"));
            return 0;
        }
        var data = PvpMapSavedData.get(player.server);
        PvpMapDefinition map = data.getById(id).orElse(null);
        if (map == null) {
            player.sendSystemMessage(MoveEarthMessage.error("マップID '" + id + "' が見つかりません。"));
            return 0;
        }
        switch (target) {
            case "red" -> map = map.withRedSpawn(player.blockPosition());
            case "blue" -> map = map.withBlueSpawn(player.blockPosition());
            case "hill1" -> map = map.withHillMin(player.blockPosition());
            case "hill2" -> map = map.withHillMax(player.blockPosition());
        }
        if (!PvpMapEditing.save(player, data, map)) return 0;
        player.sendSystemMessage(MoveEarthMessage.success("マップ [" + map.displayName() + "] の " + target + " 地点を保存しました: " + player.blockPosition().toShortString()));
        return 1;
    }

    private static int addMapSpawn(ServerPlayer player, String id, boolean isRed) {
        if (!player.level().dimension().equals(PvpMatchManager.ARENA)) {
            player.sendSystemMessage(MoveEarthMessage.error("PvPアリーナディメンション内で実行してください。"));
            return 0;
        }
        var data = PvpMapSavedData.get(player.server);
        PvpMapDefinition map = data.getById(id).orElse(null);
        if (map == null) {
            player.sendSystemMessage(MoveEarthMessage.error("マップID '" + id + "' が見つかりません。"));
            return 0;
        }
        if ((isRed ? map.allRedSpawns() : map.allBlueSpawns()).size()
                >= com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpDeployPacket.MAX_POINTS) {
            player.sendSystemMessage(MoveEarthMessage.error("出撃地点は1チーム"
                    + com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpDeployPacket.MAX_POINTS + "か所までです。"));
            return 0;
        }
        map = isRed ? map.withAddedRedSpawn(player.blockPosition()) : map.withAddedBlueSpawn(player.blockPosition());
        if (!PvpMapEditing.save(player, data, map)) return 0;
        int total = isRed ? map.allRedSpawns().size() : map.allBlueSpawns().size();
        String teamName = isRed ? "§cRED" : "§9BLUE";
        player.sendSystemMessage(MoveEarthMessage.success("マップ [" + map.displayName() + "] に " + teamName + " 追加スポーン地点を登録しました (計 " + total + "箇所): " + player.blockPosition().toShortString()));
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
            player.sendSystemMessage(MoveEarthMessage.error("マップID '" + id + "' が見つかりません。"));
            return 0;
        }
        var pos = map.redSpawn();
        player.teleportTo(arena, pos.getX() + .5, pos.getY(), pos.getZ() + .5, player.getYRot(), player.getXRot());
        player.sendSystemMessage(MoveEarthMessage.success("マップ [" + map.displayName() + "] のREDスポーン地点へテレポートしました。"));
        return 1;
    }

    private static int forceVote(CommandSourceStack source, int seconds) {
        var maps = PvpMapSavedData.get(source.getServer()).getEnabledAndConfigured();
        if (maps.isEmpty()) {
            source.sendFailure(Component.literal("§c有効なマップが存在しません。"));
            return 0;
        }
        // Run from the console or a command block there is no player to count as a voter.
        ServerPlayer caller = source.getPlayer();
        PvpMapVoteManager.INSTANCE.startVote(source.getServer(),
                caller != null && PvpMatchManager.INSTANCE.isParticipant(caller) ? Set.of(caller.getUUID()) : Set.of(),
                maps, seconds);
        source.sendSuccess(() -> Component.literal("§aマップ投票を開始しました（" + seconds + "秒）。"), true);
        return 1;
    }
}
