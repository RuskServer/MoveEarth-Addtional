package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.UUID;

/** Operator-only ledger diagnostics for pre-opening economy tests. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EconomyAdminCommand {
    private EconomyAdminCommand() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("moveeartheconomy")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("balance")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    long balance = EconomyLedgerSavedData.get(context.getSource().getServer())
                                            .balance(EconomyLedgerSavedData.Account.player(target.getUUID()));
                                    context.getSource().sendSuccess(() -> MoveEarthMessage.info(
                                            target.getScoreboardName() + ": " + balance), false);
                                    return 1;
                                })))
                .then(Commands.literal("grant")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", LongArgumentType.longArg(1L, 1_000_000L))
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            long amount = LongArgumentType.getLong(context, "amount");
                                            UUID txId = UUID.randomUUID();
                                            var result = EconomyLedgerSavedData.get(context.getSource().getServer())
                                                    .transfer(txId, null,
                                                            EconomyLedgerSavedData.Account.player(target.getUUID()),
                                                            amount, "admin_grant");
                                            if (result != EconomyLedgerSavedData.Result.APPLIED) return 0;
                                            Moveearth_addtional.LOGGER.info("Economy admin grant: actor={} target={} amount={} tx={}",
                                                    context.getSource().getTextName(), target.getUUID(), amount, txId);
                                            context.getSource().sendSuccess(() -> MoveEarthMessage.success(
                                                    target.getScoreboardName() + " に " + amount + " を付与しました"), true);
                                            return 1;
                                        })))));
    }
}
