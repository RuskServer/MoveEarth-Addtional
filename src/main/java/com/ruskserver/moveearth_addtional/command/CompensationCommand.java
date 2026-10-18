package com.ruskserver.moveearth_addtional.command;

import com.mojang.authlib.GameProfile;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Temporary: the fixed relief pack for players who lost their base, booked as an event reward so it is picked up
 * with {@code /event claim} (kept for a week, like any event reward). Operators only.
 *
 * <pre>
 * /compensate &lt;players&gt;
 * </pre>
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class CompensationCommand {
    private record Entry(String id, int count) { }

    private static final List<Entry> PACK = List.of(
            new Entry("minecraft:redstone_block", 64),
            new Entry("minecraft:gold_block", 64),
            new Entry("minecraft:copper_block", 64),
            new Entry("minecraft:wheat_seeds", 16),
            new Entry("minecraft:beetroot_seeds", 16),
            new Entry("minecraft:melon_seeds", 16),
            new Entry("minecraft:pumpkin_seeds", 16),
            new Entry("minecraft:carrot", 16),
            new Entry("minecraft:potato", 16),
            new Entry("farmersdelight:cabbage_seeds", 16),
            new Entry("farmersdelight:tomato_seeds", 16),
            new Entry("farmersdelight:onion", 16),
            new Entry("farmersdelight:rice", 16),
            new Entry("mekanism:ingot_osmium", 128),
            new Entry("create:electron_tube", 32),
            new Entry("minecraft:donkey_spawn_egg", 3),
            new Entry("minecraft:villager_spawn_egg", 2));

    private CompensationCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("compensate")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("players", GameProfileArgument.gameProfile())
                        .executes(ctx -> award(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "players")))));
    }

    private static int award(CommandSourceStack source, Collection<GameProfile> profiles) {
        List<ItemStack> items = items();
        EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(source.getServer());
        int awarded = 0;
        for (GameProfile profile : profiles) {
            // A fresh id per grant: each is its own reward, so a second grant to the same player adds to the first.
            if (!ledger.awardEvent(UUID.randomUUID(), profile.getId(), 0, items, System.currentTimeMillis())) {
                source.sendFailure(Component.literal("§c" + profile.getName() + " への補填を登録できませんでした。"));
                continue;
            }
            awarded++;
            Moveearth_addtional.LOGGER.info("{} booked the relief pack for {} ({})",
                    source.getTextName(), profile.getName(), profile.getId());
            ServerPlayer online = source.getServer().getPlayerList().getPlayer(profile.getId());
            if (online != null) {
                online.sendSystemMessage(MoveEarthMessage.success("補填物資が届きました。/event claim で受け取れます（7日以内）。"));
            }
        }
        int count = awarded;
        source.sendSuccess(() -> Component.literal("§a" + count + "人に補填を登録しました（/event claim で受け取り）。"), true);
        return count;
    }

    /** The pack in stacks no larger than each item's own: a saved stack over 99 would not load again. */
    private static List<ItemStack> items() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Entry entry : PACK) {
            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(entry.id())).orElse(Items.AIR);
            if (item == Items.AIR) {
                Moveearth_addtional.LOGGER.warn("Relief pack item '{}' is not installed; left out.", entry.id());
                continue;
            }
            for (int left = entry.count(); left > 0; ) {
                int size = Math.min(left, new ItemStack(item).getMaxStackSize());
                stacks.add(new ItemStack(item, size));
                left -= size;
            }
        }
        return stacks;
    }
}
