package com.ruskserver.moveearth_addtional.warehouse;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only inspection and recovery commands. Never required for players. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WarehouseCommands {
    private WarehouseCommands() { }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("warehouse")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("locate").executes(context -> locate(context.getSource())))
                .then(Commands.literal("preview")
                        .then(Commands.argument("min", BlockPosArgument.blockPos())
                                .executes(context -> inspect(context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "min"), false))))
                .then(Commands.literal("register")
                        .then(Commands.argument("min", BlockPosArgument.blockPos())
                                .executes(context -> inspect(context.getSource(),
                                        BlockPosArgument.getBlockPos(context, "min"), true))))
                .then(Commands.literal("testencounter")
                        .then(Commands.argument("region", IntegerArgumentType.integer(1))
                                .executes(context -> testEncounter(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "region"), false))))
                .then(Commands.literal("resetencounter")
                        .then(Commands.argument("region", IntegerArgumentType.integer(1))
                                .executes(context -> testEncounter(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "region"), true)))));
    }

    private static int status(CommandSourceStack source) {
        var data = WarehouseSites.get(source.getServer());
        var sites = data.all();
        source.sendSuccess(() -> MoveEarthMessage.info("登録済み倉庫: " + sites.size() + "件"), false);
        var fixed = WarehouseRegionAnchors.all();
        source.sendSuccess(() -> MoveEarthMessage.info("固定候補: " + fixed.size() + "件"), false);
        for (var anchor : fixed) {
            source.sendSuccess(() -> Component.literal("  地方" + anchor.region() + " X="
                    + anchor.minX() + " Z=" + anchor.minZ() + " / "
                    + (data.hasRegion(anchor.region()) ? "生成・登録済み"
                    : WarehouseRegionAnchors.generationStatus(anchor.region()))), false);
        }
        for (var site : sites) {
            var encounter = WarehouseEncounterState.get(source.getServer()).get(site.regionId());
            source.sendSuccess(() -> Component.literal("  地方" + site.regionId() + " "
                    + site.dimension() + " " + site.min().toShortString()
                    + " / " + encounter.phase() + " #" + encounter.cycle()), false);
        }
        for (var pending : data.pendingPlacements()) {
            source.sendSuccess(() -> Component.literal("  要確認: 地方" + pending.regionId() + " "
                    + pending.dimension() + " " + pending.min().toShortString()
                    + " / 自動配置が未完了。保護中・自動再試行なし"), false);
        }
        return sites.size();
    }

    private static int locate(CommandSourceStack source) {
        if (!source.getLevel().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(MoveEarthMessage.error("倉庫の最寄り検索はオーバーワールドで実行してください"));
            return 0;
        }
        var nearest = WarehouseRegionAnchors.all().stream().min(java.util.Comparator.comparingDouble(anchor -> {
            double dx = source.getPosition().x - (anchor.minX() + WarehouseSitePolicy.WIDTH / 2);
            double dz = source.getPosition().z - (anchor.minZ() + WarehouseSitePolicy.LENGTH / 2);
            return dx * dx + dz * dz;
        }));
        if (nearest.isEmpty()) {
            source.sendFailure(MoveEarthMessage.error("固定倉庫候補がありません。運用タイルの一致をサーバーログで確認してください"));
            return 0;
        }
        var anchor = nearest.get();
        boolean ready = WarehouseSites.get(source.getServer()).hasRegion(anchor.region());
        source.sendSuccess(() -> MoveEarthMessage.info("最寄りの固定倉庫: オーバーワールド 地方" + anchor.region()
                + " 中心 X=" + (anchor.minX() + WarehouseSitePolicy.WIDTH / 2)
                + " Z=" + (anchor.minZ() + WarehouseSitePolicy.LENGTH / 2)
                + " (" + (ready ? "生成・登録済み"
                : WarehouseRegionAnchors.generationStatus(anchor.region())) + ")"), false);
        return 1;
    }

    private static int inspect(CommandSourceStack source, BlockPos min, boolean register) {
        if (!(source.getLevel() instanceof ServerLevel level)) return 0;
        var result = register ? WarehouseSiteService.register(level, min)
                : WarehouseSiteService.inspect(level, min, false);
        if (!result.success()) {
            source.sendFailure(MoveEarthMessage.error("倉庫候補を登録できません: " + result.status()));
            return 0;
        }
        String action = register ? "登録しました" : "候補として利用できます";
        source.sendSuccess(() -> MoveEarthMessage.success("地方" + result.regionId() + " 倉庫を"
                + action + "。最小座標=" + min.toShortString()
                + (register ? "" : "。自動配置待ち、または手動配置後に /warehouse register を実行")), true);
        return 1;
    }

    private static int testEncounter(CommandSourceStack source, int region, boolean reset) {
        boolean success = reset
                ? WarehouseEncounterService.resetForTesting(source.getServer(), region)
                : WarehouseEncounterService.startForTesting(source.getServer(), region);
        if (!success) {
            source.sendFailure(MoveEarthMessage.error("倉庫戦闘の試験操作に失敗しました。登録・状態・開放時間・スポーン空間を確認してください"));
            return 0;
        }
        source.sendSuccess(() -> MoveEarthMessage.success(
                "地方" + region + "の倉庫戦闘を" + (reset ? "試験リセット" : "試験開始") + "しました"), true);
        return 1;
    }
}
