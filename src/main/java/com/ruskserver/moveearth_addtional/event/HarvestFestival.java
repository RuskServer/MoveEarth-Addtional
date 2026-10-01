package com.ruskserver.moveearth_addtional.event;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.advancement.ModCriteria;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.economy.HandOffSpace;
import com.ruskserver.moveearth_addtional.economy.PlayerHandOff;
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
    public static final int MINIMUM_POINTS = HarvestFestivalRules.MINIMUM_POINTS;

    private HarvestFestival() { }

    public static void harvest(ServerPlayer player, BlockState state) {
        if (!state.is(CROPS)) return;
        MinecraftServer server = player.getServer();
        if (!OpenTimeService.isOpen(server)
                || !com.ruskserver.moveearth_addtional.economy.EarningEligibility.mayEarn(player)) return;
        if (EconomyLedgerSavedData.get(server).scoreHarvest(player.getUUID(), player.getGameProfile().getName(),
                JobProgressSavedData.get(server).isActive(player.getUUID(), FARMER), OpenTimeService.now(server)))
            ModCriteria.trigger(player, ModCriteria.HARVEST_EVENT_PARTICIPATED);
    }

    public static List<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>> ranking(
            EconomyLedgerSavedData ledger) {
        return ledger.harvestScores().entrySet().stream()
                .sorted((a, b) -> HarvestFestivalRules.compare(
                        a.getValue().points(), a.getValue().reachedTick(), a.getKey().toString(),
                        b.getValue().points(), b.getValue().reachedTick(), b.getKey().toString()))
                .toList();
    }

    /**
     * The ranking settlement pays out on: nation members with at least the minimum points. The HUD
     * and the event screen use this too, so a player who left their nation after scoring neither
     * shows as a leader nor pushes members down a place they would not lose at settlement.
     */
    public static List<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>> eligibleRanking(
            MinecraftServer server, EconomyLedgerSavedData ledger) {
        var nations = com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(server);
        return ranking(ledger).stream()
                .filter(entry -> HarvestFestivalRules.rewardEligible(
                        nations.nationIdFor(entry.getKey()).isPresent(), entry.getValue().points())).toList();
    }

    public static boolean settle(MinecraftServer server, boolean force) {
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(server);
        if (ledger.harvestSettled() || ledger.harvestId() == null
                || !force && OpenTimeService.now(server) < ledger.harvestEndTick()) return false;
        UUID eventId = ledger.harvestId();
        boolean resource = "RESOURCE".equals(ledger.eventKind());
        // Rewards go to players who belong to a nation at settlement; others do not take a rank.
        List<Map.Entry<UUID, EconomyLedgerSavedData.HarvestScore>> eligible = eligibleRanking(server, ledger);
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
        ledger.pruneEventRewards(System.currentTimeMillis());
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.info(eventName(ledger)
                + ": 終了しました。/event claim で報酬を受け取れます（国家所属者のみ）。"), false);
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
        server.getPlayerList().broadcastSystemMessage(MoveEarthMessage.info(Component.translatable(
                "message.moveearth_addtional.event.resource_started", target.region(),
                Component.translatable("material.moveearth_addtional." + target.material()))), false);
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
                    Component.translatable("message.moveearth_addtional.event.harvest_started")), false);
    }

    public static int claim(ServerPlayer player) {
        if (com.ruskserver.moveearth_addtional.pvp.PvpMatchManager.INSTANCE.isActive(player)) {
            player.sendSystemMessage(MoveEarthMessage.warning("PvP試合中は現物報酬を受け取れません。試合終了後に受け取ってください。"));
            return 0;
        }
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.getServer());
        // Book what fits and take it off the goods store first, save that, then hand items over:
        // a crash between the store save and the player save can lose a claim but not duplicate it.
        HandOffSpace<ItemStack> space = PlayerHandOff.space(player);
        List<ItemStack> handOff = new ArrayList<>();
        try (PlayerHandOff.HandOff open = PlayerHandOff.begin(ledger)) {
            for (EconomyLedgerSavedData.EventReward reward : ledger.pendingEventRewards(player.getUUID())) {
                List<ItemStack> remaining = new ArrayList<>();
                boolean changed = false;
                for (ItemStack item : reward.items()) {
                    int fits = PlayerHandOff.book(space, item);
                    if (fits > 0) {
                        handOff.add(item.copyWithCount(fits));
                        changed = true;
                    }
                    if (fits < item.getCount()) remaining.add(item.copyWithCount(item.getCount() - fits));
                }
                if (changed) ledger.updateEventItems(reward.eventId(), player.getUUID(), remaining);
            }
            if (handOff.isEmpty() || !open.commit(player)) return 0;
        }
        int received = 0;
        for (ItemStack stack : handOff) {
            received += stack.getCount();
            PlayerHandOff.give(player, stack);
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
        // Hourly sweep so rewards lapse even when no event settles for a while.
        if (server.getTickCount() % 72_000 == 0)
            EconomyLedgerSavedData.get(server).pruneEventRewards(System.currentTimeMillis());
    }
}
