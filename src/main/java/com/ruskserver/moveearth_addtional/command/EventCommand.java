package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.event.HarvestFestival;
import com.ruskserver.moveearth_addtional.event.EventScreenSync;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EventCommand {
    private EventCommand() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("event")
                .executes(context -> open(context.getSource().getPlayerOrException()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("top").executes(context -> top(context.getSource())))
                .then(Commands.literal("claim").executes(context -> claim(context.getSource().getPlayerOrException())))
                .then(Commands.literal("harvest").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start").executes(context -> start(context.getSource())))
                        .then(Commands.literal("stop").executes(context -> stop(context.getSource()))))
                .then(Commands.literal("resource").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start").executes(context -> startResource(context.getSource())))));
    }

    private static int open(ServerPlayer player) {
        EventScreenSync.open(player);
        return 1;
    }

    private static int start(CommandSourceStack source) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(source.getServer());
        if (!ledger.startHarvest(OpenTimeService.now(source.getServer()), HarvestFestival.DURATION_TICKS)) {
            source.sendFailure(MoveEarthMessage.warning("収穫祭: 開催中のイベントがあります。"));
            return 0;
        }
        source.getServer().getPlayerList().broadcastSystemMessage(MoveEarthMessage.info(
                "収穫祭: 開催！成熟作物を収穫して得点を集めよう。残り30分（開放時間）。"), false);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        if (!HarvestFestival.settle(source.getServer(), true)) {
            source.sendFailure(MoveEarthMessage.warning("収穫祭: 開催中のイベントがありません。"));
            return 0;
        }
        return 1;
    }

    private static int startResource(CommandSourceStack source) {
        if (!HarvestFestival.startResource(source.getServer())) {
            source.sendFailure(MoveEarthMessage.warning("資源発見: 開催中、または対象地方が未設定です。"));
            return 0;
        }
        return 1;
    }

    private static int status(CommandSourceStack source) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(source.getServer());
        if (ledger.harvestId() == null) {
            source.sendSuccess(() -> MoveEarthMessage.info("イベント: 開催履歴がありません。"), false);
            return 1;
        }
        long now = OpenTimeService.now(source.getServer());
        long minutes = Math.max(0L, (ledger.harvestEndTick() - now + 1199L) / 1200L);
        String state = ledger.harvestSettled() ? "終了" : "開催中・残り約" + minutes + "分";
        String target = "RESOURCE".equals(ledger.eventKind())
                ? " / 地方" + ledger.targetRegion() + "・" + ledger.targetMaterial() + "鉱石" : "";
        long nextMinutes = Math.max(0L, (ledger.nextAutoEventTick() - now + 1199L) / 1200L);
        source.sendSuccess(() -> MoveEarthMessage.info(HarvestFestival.eventName(ledger) + ": " + state + target
                + " / 参加ライン " + HarvestFestival.MINIMUM_POINTS + "点 / 次回自動開催まで約"
                + nextMinutes + "分"), false);
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            EconomyLedgerSavedData.HarvestScore score = ledger.harvestScores().get(player.getUUID());
            source.sendSuccess(() -> Component.literal("あなたの得点: " + (score == null ? 0 : score.points())
                    + " / 受取待ち: " + ledger.pendingEventRewards(player.getUUID()).size() + "件"), false);
        }
        return 1;
    }

    private static int top(CommandSourceStack source) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(source.getServer());
        source.sendSuccess(() -> MoveEarthMessage.info(HarvestFestival.eventName(ledger) + ": 上位5名"), false);
        var ranking = HarvestFestival.ranking(ledger);
        for (int index = 0; index < Math.min(5, ranking.size()); index++) {
            int rank = index + 1;
            var score = ranking.get(index).getValue();
            source.sendSuccess(() -> Component.literal(rank + ". " + score.name() + " " + score.points() + "点"), false);
        }
        return 1;
    }

    private static int claim(ServerPlayer player) {
        int received = HarvestFestival.claim(player);
        player.sendSystemMessage(MoveEarthMessage.success("イベント: 現物報酬を" + received + "個受け取りました。"));
        return received > 0 ? 1 : 0;
    }
}
