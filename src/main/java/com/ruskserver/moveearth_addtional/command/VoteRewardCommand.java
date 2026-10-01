package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class VoteRewardCommand {
    private static final int ADMIN_PERMISSION_LEVEL = 2;
    private VoteRewardCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("moveearthvotereward")
                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> grant(
                                context.getSource(), EntityArgument.getPlayer(context, "player")))));
    }

    private static int grant(CommandSourceStack source, ServerPlayer player) {
        Reward reward = createRandomReward(player);
        boolean physical = !reward.stack().isEmpty();
        boolean deferred = physical
                && com.ruskserver.moveearth_addtional.pvp.PvpMatchManager.INSTANCE.isActive(player);
        long currency = VoteRewardPool.BASE_TC + reward.currency();
        String description = physical ? currency + " TC + " + reward.description() : currency + " TC";
        var ledger = EconomyLedgerSavedData.get(source.getServer());
        if (deferred && !ledger.awardEvent(java.util.UUID.randomUUID(), player.getUUID(), 0,
                java.util.List.of(reward.stack()), System.currentTimeMillis())) {
            source.sendFailure(MoveEarthMessage.error("投票の現物報酬を保留できませんでした。"));
            return 0;
        }
        var result = ledger.transfer(java.util.UUID.randomUUID(), null,
                EconomyLedgerSavedData.Account.player(player.getUUID()), currency, "vote_reward");
        if (result != EconomyLedgerSavedData.Result.APPLIED) {
            if (deferred) {
                // The physical prize is safely pending. Do not report an overall failure that
                // might cause the vote service to reissue the same random prize.
                player.sendSystemMessage(MoveEarthMessage.warning(
                        "投票の現物報酬は /event claim で受け取れますが、TCの付与に失敗しました。運営に連絡してください。"));
                source.sendSuccess(() -> MoveEarthMessage.warning(
                        "投票の現物報酬は保留しましたが、TCの付与に失敗しました。運営に連絡してください。"), false);
                Moveearth_addtional.LOGGER.error("Vote currency failed after deferring physical prize for {}",
                        player.getUUID());
                return 1;
            }
            source.sendFailure(MoveEarthMessage.error("投票報酬の付与に失敗しました。"));
            return 0;
        }
        if (physical) {
            ItemStack stack = reward.stack();
            if (deferred) {
                player.sendSystemMessage(MoveEarthMessage.info(
                        "投票の現物報酬を保留しました。試合終了後に /event claim で受け取れます。"));
            } else if (!player.getInventory().add(stack)) player.drop(stack, false);
        }

        player.sendSystemMessage(MoveEarthMessage.success(
                (deferred ? "投票報酬を受け取りました（現物は受取待ち）: " : "投票報酬を受け取りました: ") + description));
        Component broadcast = MoveEarthMessage.info(Component.literal("【投票】")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(player.getGameProfile().getName())
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" がサーバーに投票し、「" + description + "」を獲得しました！")
                        .withStyle(ChatFormatting.YELLOW)));
        source.getServer().getPlayerList().broadcastSystemMessage(broadcast, false);
        source.sendSuccess(() -> MoveEarthMessage.success(
                player.getGameProfile().getName() + " に投票報酬「" + description + "」を付与しました。"), false);
        Moveearth_addtional.LOGGER.info("Granted vote reward '{}' to {} ({}).",
                description, player.getGameProfile().getName(), player.getUUID());
        return 1;
    }

    private static Reward createRandomReward(ServerPlayer player) {
        return switch (VoteRewardPool.bonusAt(player.getRandom().nextInt(VoteRewardPool.slotCount()))) {
            case END_STONE -> new Reward(new ItemStack(Items.END_STONE, 8), "エンドストーン x8");
            case EFFICIENCY_PICKAXE -> enchantedPickaxe(player, true);
            case MENDING_PICKAXE -> enchantedPickaxe(player, false);
            case EXTRA_TC_2 -> coinReward(2);
            case EXTRA_TC_3 -> coinReward(3);
            case ANDESITE_ALLOY -> createReward("andesite_alloy", 6, "安山岩合金 x6");
            case BRASS_INGOT -> createReward("brass_ingot", 4, "真鍮インゴット x4");
            case ELECTRON_TUBE -> createReward("electron_tube", 3, "電子管 x3");
            case COPPER_SHEET -> createReward("copper_sheet", 6, "銅板 x6");
            case GUNPOWDER -> new Reward(new ItemStack(Items.GUNPOWDER, 8), "火薬 x8");
        };
    }

    private static Reward createReward(String itemPath, int amount, String description) {
        ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath("create", itemPath);
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null || item == Items.AIR) {
            Moveearth_addtional.LOGGER.warn(
                    "Create vote reward item '{}' is unavailable; falling back to currency.", itemId);
            return coinReward(2);
        }
        return new Reward(new ItemStack(item, amount), description);
    }

    private static Reward coinReward(int amount) {
        return new Reward(ItemStack.EMPTY, amount + " TC", amount);
    }

    private static Reward enchantedPickaxe(ServerPlayer player, boolean efficiency) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (efficiency) {
            stack.enchant(enchantments.getOrThrow(Enchantments.EFFICIENCY), 5);
            return new Reward(stack, "ダイヤモンドのツルハシ（効率強化V）");
        }

        stack.enchant(enchantments.getOrThrow(Enchantments.MENDING), 1);
        return new Reward(stack, "ダイヤモンドのツルハシ（修繕）");
    }

    private record Reward(ItemStack stack, String description, long currency) {
        private Reward(ItemStack stack, String description) { this(stack, description, 0L); }
    }
}
