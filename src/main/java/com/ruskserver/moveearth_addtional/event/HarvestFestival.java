package com.ruskserver.moveearth_addtional.event;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.advancement.ModCriteria;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.jobs.JobProgressSavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class HarvestFestival {
    private static final TagKey<Block> CROPS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "jobs/farmer/age_crops"));
    private static final ResourceLocation FARMER = ResourceLocation.fromNamespaceAndPath(
            Moveearth_addtional.MODID, "farmer");
    public static final long DURATION_TICKS = 30L * 60L * 20L;
    public static final int MINIMUM_POINTS = 100;

    private HarvestFestival() { }

    public static void harvest(ServerPlayer player, BlockState state) {
        if (!state.is(CROPS)) return;
        MinecraftServer server = player.getServer();
        if (!OpenTimeService.isOpen(server)) return;
        if (EconomyLedgerSavedData.get(server).scoreHarvest(player.getUUID(), player.getGameProfile().getName(),
                JobProgressSavedData.get(server).isActive(player.getUUID(), FARMER), OpenTimeService.now(server)))
            ModCriteria.trigger(player, ModCriteria.HARVEST_EVENT_PARTICIPATED);
    }

    public static List<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>> ranking(
            EconomyLedgerSavedData ledger) {
        return ledger.harvestScores().entrySet().stream()
                .sorted(Comparator.<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>>
                        comparingInt(entry -> entry.getValue().points()).reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .toList();
    }

    public static boolean settle(MinecraftServer server, boolean force) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(server);
        if (ledger.harvestSettled() || ledger.harvestId() == null
                || !force && OpenTimeService.now(server) < ledger.harvestEndTick()) return false;
        UUID eventId = ledger.harvestId();
        boolean resource = "RESOURCE".equals(ledger.eventKind());
        List<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>> eligible = ranking(ledger).stream()
                .filter(entry -> entry.getValue().points() >= MINIMUM_POINTS).toList();
        for (int index = 0; index < eligible.size(); index++) {
            UUID playerId = eligible.get(index).getKey();
            if (ledger.eventReward(eventId, playerId) != null) continue;
            int currency = HarvestFestivalRules.currency(index, 0);
            List<ItemStack> items = new ArrayList<>();
            items.add(resource ? resourceReward(ledger.targetMaterial()) : new ItemStack(Items.IRON_INGOT, 8));
            items.add(new ItemStack(Items.QUARTZ, 4));
            if (index < 3) items.add(new ItemStack(Items.DIAMOND, HarvestFestivalRules.diamonds(index)));
            if (!ledger.awardEvent(eventId, playerId, currency, items, System.currentTimeMillis())) return false;
        }
        ledger.finishHarvest();
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.info(eventName(ledger)
                + ": 終了しました。/event claim で報酬を受け取れます。"), false);
        return true;
    }

    private static ItemStack resourceReward(String material) {
        return switch (material) {
            case "coal" -> new ItemStack(Items.COAL, 16);
            case "copper" -> new ItemStack(Items.COPPER_INGOT, 12);
            default -> new ItemStack(Items.IRON_INGOT, 8);
        };
    }

    public static String eventName(EconomyLedgerSavedData ledger) {
        return "RESOURCE".equals(ledger.eventKind()) ? "資源発見" : "収穫祭";
    }

    public static boolean startResource(MinecraftServer server) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(server);
        ResourceEvent.Target target = ResourceEvent.chooseTarget(ledger.eventSequence());
        if (target == null || !ledger.startResource(target.region(), target.material(),
                OpenTimeService.now(server), ResourceEvent.DURATION_TICKS)) return false;
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.info("資源発見: 地方" + target.region()
                + "の天然" + target.material() + "鉱石を採掘！45分（開放時間）。"), false);
        return true;
    }

    private static void startAuto(MinecraftServer server) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(server);
        if (!EventScheduleRules.due(OpenTimeService.now(server), ledger.nextAutoEventTick(),
                ledger.harvestSettled(), OpenTimeService.isOpen(server))) return;
        if (ledger.harvestId() != null && "HARVEST".equals(ledger.eventKind())
                && startResource(server)) return;
        if (ledger.startHarvest(OpenTimeService.now(server), DURATION_TICKS))
            server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.info(
                    "収穫祭: 開催！成熟作物を収穫して得点を集めよう。30分（開放時間）。"), false);
    }

    public static int claim(ServerPlayer player) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.getServer());
        int received = 0;
        for (EconomyLedgerSavedData.EventReward reward : ledger.pendingEventRewards(player.getUUID())) {
            List<ItemStack> remaining = new ArrayList<>();
            for (ItemStack item : reward.items()) {
                ItemStack stack = item.copy();
                int before = stack.getCount();
                player.getInventory().add(stack);
                received += before - stack.getCount();
                if (!stack.isEmpty()) remaining.add(stack);
            }
            ledger.updateEventItems(reward.eventId(), player.getUUID(), remaining);
        }
        return received;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20L == 0L) {
            settle(server, false);
            startAuto(server);
        }
    }
}
