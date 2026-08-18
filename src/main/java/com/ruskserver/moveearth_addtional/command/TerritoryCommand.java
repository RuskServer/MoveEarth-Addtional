package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.territory.create.CreateStressScanner;
import com.ruskserver.moveearth_addtional.territory.data.TerritoryCoreSavedData;
import com.ruskserver.moveearth_addtional.territory.data.TerritoryPowerSavedData;
import com.ruskserver.moveearth_addtional.territory.domain.InfluenceResult;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryCore;
import com.ruskserver.moveearth_addtional.territory.domain.TerritoryOwnerId;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryIndustrialPowerService;
import com.ruskserver.moveearth_addtional.territory.service.TerritoryInfluenceService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TerritoryCommand {
    private TerritoryCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("territory")
                .then(Commands.literal("inspect")
                        .requires(source -> source.hasPermission(0))
                        .executes(context -> inspect(context.getSource())))
                .then(Commands.literal("cores")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> listCores(context.getSource())))
                .then(Commands.literal("power")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("get")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> getPower(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 1_000_000.0D))
                                                .executes(context -> setPower(
                                                        context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        DoubleArgumentType.getDouble(context, "value"))))))
                        .then(Commands.literal("refresh")
                                .executes(context -> refreshPower(context.getSource())))));
    }

    private static int inspect(CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        InfluenceResult result = TerritoryInfluenceService.evaluate(player.serverLevel(), player.blockPosition());
        if (result.leadingOwner().isEmpty()) {
            source.sendSuccess(() -> Component.literal("この地点には領土影響力がありません。"), false);
            return 0;
        }

        String leader = displayOwner(source, result.leadingOwner().orElseThrow());
        String controller = result.controllingOwner()
                .map(ownerId -> displayOwner(source, ownerId))
                .orElse("競合中");
        String protections = result.protectedActions().isEmpty()
                ? "なし"
                : result.protectedActions().stream()
                .map(action -> action.name().toLowerCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "影響力: 首位=%s 支配=%s 強度=%.2f 2位=%.2f 保護=%s",
                leader, controller, result.leadingInfluence(), result.runnerUpInfluence(), protections)), false);
        return result.contested() ? 0 : 1;
    }

    private static int getPower(CommandSourceStack source, ServerPlayer player) {
        TerritoryOwnerId ownerId = TerritoryOwnerId.of(player.getUUID());
        TerritoryIndustrialPowerService.Breakdown power =
                TerritoryIndustrialPowerService.get(source.getServer(), ownerId);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%s の工業力: 合計=%.2f, Create=%.2f, 手動補正=%.2f, "
                        + "使用応力=%.1f SU, 発電容量=%.1f SU, 発電源=%d, ネットワーク=%d",
                player.getScoreboardName(), power.totalScore(), power.create().industrialScore(),
                power.manualAdjustment(), power.create().usedStress(), power.create().generatedCapacity(),
                power.create().sourceCount(), power.create().networkCount())), false);
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(power.totalScore()));
    }

    private static int setPower(CommandSourceStack source, ServerPlayer player, double score) {
        TerritoryPowerSavedData.get(source.getServer())
                .setIndustrialScore(TerritoryOwnerId.of(player.getUUID()), score);
        source.sendSuccess(() -> Component.literal(
                player.getScoreboardName() + " の工業力手動補正を " + score + " に設定しました。"), true);
        return 1;
    }

    private static int refreshPower(CommandSourceStack source) {
        if (!ModList.get().isLoaded("create")) {
            source.sendFailure(Component.literal("Createが導入されていないため応力を取得できません。"));
            return 0;
        }
        CreateStressScanner.refresh(source.getServer());
        source.sendSuccess(() -> Component.literal("Create応力スナップショットを更新しました。"), false);
        return 1;
    }

    private static int listCores(CommandSourceStack source) {
        Collection<TerritoryCore> cores = TerritoryCoreSavedData.get(source.getServer()).allCores();
        source.sendSuccess(() -> Component.literal("登録済み領土コア: " + cores.size()), false);
        cores.stream().limit(20).forEach(core -> source.sendSuccess(() -> Component.literal(
                core.id() + " owner=" + displayOwner(source, core.ownerId())
                        + " dimension=" + core.position().dimensionId()
                        + " pos=(" + (int) Math.floor(core.position().x())
                        + ", " + (int) Math.floor(core.position().y())
                        + ", " + (int) Math.floor(core.position().z()) + ")"), false));
        if (cores.size() > 20) {
            source.sendSuccess(() -> Component.literal("ほか " + (cores.size() - 20) + " 件"), false);
        }
        return cores.size();
    }

    private static String displayOwner(CommandSourceStack source, TerritoryOwnerId ownerId) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayer(ownerId.value());
        return online == null ? ownerId.value().toString() : online.getScoreboardName();
    }
}
