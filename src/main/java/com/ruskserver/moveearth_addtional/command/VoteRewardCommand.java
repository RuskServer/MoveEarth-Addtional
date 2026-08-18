package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.CommandDispatcher;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
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
    private static final ResourceLocation GOLD_COIN_ID =
            ResourceLocation.fromNamespaceAndPath("lightmanscurrency", "coin_gold");
    private static final int REWARD_COUNT = 6;

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
        if (reward == null) {
            source.sendFailure(Component.literal(
                    "投票報酬を付与できませんでした。Lightman's Currencyの金硬貨が読み込まれているか確認してください。"));
            return 0;
        }

        ItemStack stack = reward.stack();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }

        player.sendSystemMessage(Component.literal("投票報酬を受け取りました: " + reward.description()));
        Component broadcast = Component.literal("【投票】")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(player.getGameProfile().getName())
                        .withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" がサーバーに投票し、「" + reward.description() + "」を獲得しました！")
                        .withStyle(ChatFormatting.YELLOW));
        source.getServer().getPlayerList().broadcastSystemMessage(broadcast, false);
        source.sendSuccess(() -> Component.literal(
                player.getGameProfile().getName() + " に投票報酬「" + reward.description() + "」を付与しました。"), false);
        Moveearth_addtional.LOGGER.info("Granted vote reward '{}' to {} ({}).",
                reward.description(), player.getGameProfile().getName(), player.getUUID());
        return 1;
    }

    private static Reward createRandomReward(ServerPlayer player) {
        return switch (player.getRandom().nextInt(REWARD_COUNT)) {
            case 0 -> coinReward(2);
            case 1 -> coinReward(3);
            case 2 -> new Reward(new ItemStack(Items.END_STONE, 8), "エンドストーン x8");
            case 3 -> new Reward(new ItemStack(Items.GUNPOWDER, 8), "火薬 x8");
            case 4 -> enchantedPickaxe(player, true);
            case 5 -> enchantedPickaxe(player, false);
            default -> throw new IllegalStateException("Unexpected vote reward roll");
        };
    }

    private static Reward coinReward(int amount) {
        Item coin = BuiltInRegistries.ITEM.getOptional(GOLD_COIN_ID).orElse(null);
        return coin == null || coin == Items.AIR
                ? null
                : new Reward(new ItemStack(coin, amount), "金硬貨 x" + amount);
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

    private record Reward(ItemStack stack, String description) {
    }
}
