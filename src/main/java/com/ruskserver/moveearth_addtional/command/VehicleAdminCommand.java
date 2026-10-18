package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleCargoBackup;
import com.ruskserver.moveearth_addtional.s2.vehicle.VehicleCargoPolicy;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff tools for a vehicle lost to a Sable fault: what its cargo record holds, and that cargo handed out in
 * shulker boxes to give back ({@link VehicleCargoBackup}). Operators only.
 *
 * <pre>
 * /vehicleadmin cargo list [nation]       vehicles with a record, newest first
 * /vehicleadmin cargo show &lt;id&gt; [gen]     what one snapshot held (gen 1 = newest)
 * /vehicleadmin cargo give &lt;id&gt; [gen]     that snapshot's items, in shulker boxes, to you
 * /vehicleadmin info                      the Sable body you look at: owner, vehicle, governing core
 * /vehicleadmin heal                      examine that body again and repair what is found
 * /vehicleadmin relink &lt;id&gt;               bind that body to a vehicle (and move its record to a core on it)
 * /vehicleadmin remove &lt;id&gt;               drop a vehicle record whose craft is gone
 * </pre>
 * Automatic repair runs on its own as bodies load ({@code VehicleIntegrityService}); these are for what it
 * cannot tell, such as a fragment that never carried any owner.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class VehicleAdminCommand {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("Asia/Tokyo"));

    private VehicleAdminCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vehicleadmin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("cargo")
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource(), ""))
                                .then(Commands.argument("nation", StringArgumentType.greedyString())
                                        .executes(ctx -> list(ctx.getSource(), StringArgumentType.getString(ctx, "nation")))))
                        .then(Commands.literal("show")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> show(ctx.getSource(), StringArgumentType.getString(ctx, "id"), 1))
                                        .then(Commands.argument("gen", IntegerArgumentType.integer(1, VehicleCargoPolicy.KEPT_SNAPSHOTS))
                                                .executes(ctx -> show(ctx.getSource(), StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "gen"))))))
                        .then(Commands.literal("give")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> give(ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "id"), 1))
                                        .then(Commands.argument("gen", IntegerArgumentType.integer(1, VehicleCargoPolicy.KEPT_SNAPSHOTS))
                                                .executes(ctx -> give(ctx.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(ctx, "id"),
                                                        IntegerArgumentType.getInteger(ctx, "gen")))))))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("heal").executes(ctx -> heal(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("relink")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> relink(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))));
    }

    /** The Sable body under the player's crosshair, within 16 blocks, or null. */
    private static dev.ryanhcode.sable.sublevel.ServerSubLevel lookedAt(ServerPlayer player) {
        var hit = player.pick(16.0D, 1.0F, false);
        if (!(hit instanceof net.minecraft.world.phys.BlockHitResult block)
                || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
        return dev.ryanhcode.sable.Sable.HELPER.getContaining(player.serverLevel(), block.getBlockPos())
                instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body && !body.isRemoved() ? body : null;
    }

    private static dev.ryanhcode.sable.sublevel.ServerSubLevel requireBody(ServerPlayer player) {
        var body = lookedAt(player);
        if (body == null) player.sendSystemMessage(Component.literal("§c構造物（Sableの物理ボディ）を見て実行してください。"));
        return body;
    }

    private static String shortId(UUID id) {
        return id == null ? "なし" : id.toString().substring(0, 8);
    }

    private static String nationName(net.minecraft.server.MinecraftServer server, UUID nation) {
        if (nation == null) return "なし";
        return com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(server).nation(nation)
                .map(found -> found.name()).orElse("消滅した国 " + shortId(nation));
    }

    private static int info(ServerPlayer player) {
        var body = requireBody(player);
        if (body == null) return 0;
        var level = player.serverLevel();
        var vehicles = com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData.get(player.server);
        UUID bound = com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology.boundVehicleId(body);
        var record = bound == null ? null : vehicles.vehicle(bound).orElse(null);
        var governing = com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology
                .at(level, body.getPlot().getCenterBlock()).orElse(null);
        player.sendSystemMessage(Component.literal("§6=== 構造物 " + shortId(body.getUniqueId()) + " ==="));
        player.sendSystemMessage(Component.literal("§7所有国: §f" + nationName(player.server,
                com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology.owningNation(level, body))
                + " §7(刻印: " + nationName(player.server,
                com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology.taggedNation(body)) + ")"));
        player.sendSystemMessage(Component.literal("§7紐付いた車両: §f" + shortId(bound)
                + (bound == null ? "" : record == null ? " §c(記録なし)" : " §a(記録あり)")));
        if (record != null) {
            player.sendSystemMessage(Component.literal("§7  コア位置 " + record.corePos().toShortString()
                    + " / コアの構造物 " + shortId(record.subLevelId()) + " / HP " + record.health() + "/"
                    + record.maximumHealth()));
        }
        player.sendSystemMessage(Component.literal("§7この構造物を守っている車両: §f" + (governing == null
                ? "なし（コアのある構造物とつながっていない）"
                : shortId(governing.vehicle().id()) + " (" + nationName(player.server, governing.vehicle().nationId())
                + "、つながった構造物 " + governing.connectedBodies().size() + "個)")));
        return 1;
    }

    private static int heal(ServerPlayer player) {
        var body = requireBody(player);
        if (body == null) return 0;
        var report = com.ruskserver.moveearth_addtional.compat.vehicle.VehicleIntegrityService
                .heal(player.serverLevel(), body);
        player.sendSystemMessage(Component.literal(report.changed()
                ? "§a修復しました: 記録の付け直し " + report.relinked() + "件 / 幽霊記録の削除 " + report.ghosts()
                + "件 / コア記録の復元 " + report.restored() + "件" + (report.stamped() ? " / 所有国を刻印" : "")
                : "§7直すものは見つかりませんでした。"));
        return 1;
    }

    private static UUID vehicleByPrefix(CommandSourceStack source, String prefix) {
        var vehicles = com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData.get(source.getServer());
        List<UUID> matches = vehicles.all().stream().map(record -> record.id())
                .filter(id -> id.toString().startsWith(prefix.toLowerCase())).toList();
        if (prefix.length() < 4 || matches.size() != 1) {
            source.sendFailure(Component.literal(matches.isEmpty() ? "§c該当する車両の記録がありません。"
                    : "§c4文字以上で、1台に絞れるIDを指定してください（/vehicleadmin info で確認）。"));
            return null;
        }
        return matches.getFirst();
    }

    private static int relink(ServerPlayer player, String prefix) {
        var body = requireBody(player);
        UUID vehicleId = body == null ? null : vehicleByPrefix(player.createCommandSourceStack(), prefix);
        if (vehicleId == null) return 0;
        var level = player.serverLevel();
        com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology.bind(level, body, vehicleId);
        // A core of this vehicle standing on the body takes the record with it.
        var report = com.ruskserver.moveearth_addtional.compat.vehicle.VehicleIntegrityService.heal(level, body);
        Moveearth_addtional.LOGGER.info("{} relinked body {} to vehicle {}", player.getGameProfile().getName(),
                body.getUniqueId(), vehicleId);
        player.sendSystemMessage(Component.literal("§a構造物 " + shortId(body.getUniqueId()) + " を車両 "
                + shortId(vehicleId) + " に紐付けました" + (report.relinked() > 0 ? "（コア記録も移しました）" : "") + "。"));
        return 1;
    }

    private static int remove(CommandSourceStack source, String prefix) {
        UUID vehicleId = vehicleByPrefix(source, prefix);
        if (vehicleId == null) return 0;
        var vehicles = com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData.get(source.getServer());
        var record = vehicles.vehicle(vehicleId).orElse(null);
        vehicles.remove(vehicleId);
        com.ruskserver.moveearth_addtional.s2.vehicle.VehicleLootSavedData.get(source.getServer()).revoke(vehicleId);
        Moveearth_addtional.LOGGER.info("{} removed the record of vehicle {} ({})", source.getTextName(), vehicleId, record);
        source.sendSuccess(() -> Component.literal("§a車両 " + shortId(vehicleId) + " の記録を削除しました。"), true);
        return 1;
    }

    private static int list(CommandSourceStack source, String nationFilter) {
        record Row(UUID id, CompoundTag latest, int count) { }
        List<Row> rows = new ArrayList<>();
        for (UUID id : VehicleCargoBackup.recorded(source.getServer())) {
            ListTag snapshots = VehicleCargoBackup.snapshots(source.getServer(), id);
            if (snapshots.isEmpty()) continue;
            CompoundTag latest = snapshots.getCompound(0);
            if (!nationFilter.isBlank() && !latest.getString("NationName").toLowerCase().contains(nationFilter.toLowerCase())) continue;
            rows.add(new Row(id, latest, snapshots.size()));
        }
        rows.sort(Comparator.comparingLong((Row row) -> row.latest().getLong("Time")).reversed());
        source.sendSuccess(() -> Component.literal("§6=== 車両の積み荷記録 (" + rows.size() + "台) ==="), false);
        for (Row row : rows) {
            String line = "§b" + row.id().toString().substring(0, 8) + " §f" + row.latest().getString("NationName")
                    + " §7最新 " + TIME.format(Instant.ofEpochMilli(row.latest().getLong("Time")))
                    + " / 収納" + row.latest().getList("Containers", Tag.TAG_COMPOUND).size() + "個"
                    + " / アイテム" + itemCount(row.latest()) + "個 / 記録" + row.count() + "件";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return rows.size();
    }

    private static int show(CommandSourceStack source, String idPrefix, int generation) {
        CompoundTag snapshot = snapshot(source, idPrefix, generation);
        if (snapshot == null) return 0;
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (ItemStack stack : stacks(source, snapshot)) totals.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        source.sendSuccess(() -> Component.literal("§6=== " + snapshot.getString("NationName") + " の車両 "
                + TIME.format(Instant.ofEpochMilli(snapshot.getLong("Time"))) + " (" + reason(snapshot) + ") ==="), false);
        totals.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(40)
                .forEach(entry -> source.sendSuccess(() -> Component.literal("  §f" + entry.getKey() + " §7×" + entry.getValue()), false));
        if (totals.size() > 40) source.sendSuccess(() -> Component.literal("  §7…ほか" + (totals.size() - 40) + "種類"), false);
        return totals.size();
    }

    private static int give(ServerPlayer player, String idPrefix, int generation) {
        CommandSourceStack source = player.createCommandSourceStack();
        CompoundTag snapshot = snapshot(source, idPrefix, generation);
        if (snapshot == null) return 0;
        List<ItemStack> boxable = new ArrayList<>();
        List<ItemStack> loose = new ArrayList<>();
        for (ItemStack stack : stacks(source, snapshot)) {
            // A shulker box cannot hold another; those are handed over as they are.
            boolean shulker = net.minecraft.world.level.block.Block.byItem(stack.getItem())
                    instanceof net.minecraft.world.level.block.ShulkerBoxBlock;
            (shulker ? loose : boxable).add(stack);
        }
        int boxes = VehicleCargoPolicy.boxes(boxable.size());
        String label = snapshot.getString("NationName") + " " + TIME.format(Instant.ofEpochMilli(snapshot.getLong("Time")));
        for (int box = 0; box < boxes; box++) {
            List<ItemStack> contents = boxable.subList(box * VehicleCargoPolicy.BOX_SLOTS,
                    Math.min(boxable.size(), (box + 1) * VehicleCargoPolicy.BOX_SLOTS));
            ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
            shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
            shulker.set(DataComponents.CUSTOM_NAME, Component.literal("補填 " + label + " (" + (box + 1) + "/" + boxes + ")"));
            hand(player, shulker);
        }
        loose.forEach(stack -> hand(player, stack));
        Moveearth_addtional.LOGGER.info("{} took the cargo of vehicle {} ({} at {}) to hand back: {} box(es), {} loose",
                player.getGameProfile().getName(), idPrefix, snapshot.getString("NationName"),
                TIME.format(Instant.ofEpochMilli(snapshot.getLong("Time"))), boxes, loose.size());
        player.sendSystemMessage(Component.literal("§a積み荷をシュルカーボックス" + boxes + "個"
                + (loose.isEmpty() ? "" : "と" + loose.size() + "個") + "で受け取りました。"));
        return boxes;
    }

    private static void hand(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static CompoundTag snapshot(CommandSourceStack source, String idPrefix, int generation) {
        List<UUID> matches = VehicleCargoBackup.recorded(source.getServer()).stream()
                .filter(id -> id.toString().startsWith(idPrefix.toLowerCase())).toList();
        if (idPrefix.length() < 4 || matches.size() != 1) {
            source.sendFailure(Component.literal(matches.isEmpty() ? "§c該当する車両の記録がありません。"
                    : "§c4文字以上で、1台に絞れるIDを指定してください（/vehicleadmin cargo list で確認）。"));
            return null;
        }
        ListTag snapshots = VehicleCargoBackup.snapshots(source.getServer(), matches.getFirst());
        int index = VehicleCargoPolicy.index(generation, snapshots.size());
        if (index < 0) {
            source.sendFailure(Component.literal("§cその世代の記録はありません（" + snapshots.size() + "件まで）。"));
            return null;
        }
        return snapshots.getCompound(index);
    }

    private static List<ItemStack> stacks(CommandSourceStack source, CompoundTag snapshot) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Tag entry : snapshot.getList("Containers", Tag.TAG_COMPOUND)) {
            for (Tag item : ((CompoundTag) entry).getList("Items", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.parseOptional(source.registryAccess(), (CompoundTag) item);
                if (!stack.isEmpty()) stacks.add(stack);
            }
        }
        return stacks;
    }

    private static int itemCount(CompoundTag snapshot) {
        int count = 0;
        for (Tag entry : snapshot.getList("Containers", Tag.TAG_COMPOUND)) {
            for (Tag item : ((CompoundTag) entry).getList("Items", Tag.TAG_COMPOUND)) count += ((CompoundTag) item).getInt("count");
        }
        return count;
    }

    private static String reason(CompoundTag snapshot) {
        return switch (snapshot.getString("Reason")) {
            case "split" -> "分離の直前";
            case "stop" -> "サーバー停止時";
            default -> "定期記録";
        };
    }
}
