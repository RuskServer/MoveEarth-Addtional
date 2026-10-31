package com.ruskserver.moveearth_addtional.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.dispatch.DispatchContractSavedData;
import com.ruskserver.moveearth_addtional.s2.dispatch.DispatchContractService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;
import java.util.UUID;

/**
 * Staff tools for dispatch contracts that stopped in review ("要確認"), where a payment could not be booked.
 * Nothing else leads out of that state, and while a contract sits in it neither nation can disband.
 *
 * <pre>
 * /dispatchadmin list            contracts not yet completed or cancelled
 * /dispatchadmin retry &lt;id&gt;      run its settlement again (payments already made are not repeated)
 * /dispatchadmin close &lt;id&gt;      end it without moving money, to settle by hand
 * </pre>
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class DispatchAdminCommand {
    private DispatchAdminCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dispatchadmin")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("retry").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> retry(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("close").then(Commands.argument("id", StringArgumentType.word())
                        .executes(ctx -> close(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))));
    }

    private static int list(CommandSourceStack source) {
        NationSavedData nations = NationSavedData.get(source.getServer());
        List<DispatchContractSavedData.Contract> open = DispatchContractSavedData.get(source.getServer()).all().stream()
                .filter(contract -> contract.state() != DispatchContractSavedData.State.COMPLETED
                        && contract.state() != DispatchContractSavedData.State.CANCELLED).toList();
        source.sendSuccess(() -> Component.literal("§6=== 派遣契約（未完了 " + open.size() + "件） ==="), false);
        for (DispatchContractSavedData.Contract contract : open) {
            String line = "§b" + contract.id().toString().substring(0, 8) + " §f" + name(nations, contract.employerNation())
                    + " → " + name(nations, contract.providerNation()) + " §7" + contract.state().name()
                    + (contract.endReason().isBlank() ? "" : " (" + contract.endReason() + ")")
                    + " / 預かり " + (contract.ownEscrow() + contract.subsidyEscrow()) + " TC";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return open.size();
    }

    private static int retry(CommandSourceStack source, String prefix) {
        UUID id = find(source, prefix);
        if (id == null) return 0;
        boolean done = DispatchContractService.retrySettlement(source.getServer(), id);
        Moveearth_addtional.LOGGER.info("{} retried the settlement of dispatch contract {}: {}", source.getTextName(), id, done);
        if (done) source.sendSuccess(() -> Component.literal("§a精算をやり直して完了しました。"), true);
        else source.sendFailure(Component.literal("§c精算できませんでした（要確認の契約だけが対象です。理由は /dispatchadmin list で確認）。"));
        return done ? 1 : 0;
    }

    private static int close(CommandSourceStack source, String prefix) {
        UUID id = find(source, prefix);
        if (id == null) return 0;
        boolean done = DispatchContractService.forceClose(source.getServer(), id);
        Moveearth_addtional.LOGGER.info("{} closed dispatch contract {} without settlement: {}", source.getTextName(), id, done);
        if (done) source.sendSuccess(() -> Component.literal("§a契約をお金を動かさずに終了しました。預かり分は手作業で精算してください。"), true);
        else source.sendFailure(Component.literal("§cこの契約は既に終わっています。"));
        return done ? 1 : 0;
    }

    private static UUID find(CommandSourceStack source, String prefix) {
        List<UUID> matches = DispatchContractSavedData.get(source.getServer()).all().stream()
                .map(DispatchContractSavedData.Contract::id)
                .filter(id -> id.toString().startsWith(prefix.toLowerCase())).toList();
        if (prefix.length() < 4 || matches.size() != 1) {
            source.sendFailure(Component.literal(matches.isEmpty() ? "§c該当する契約がありません。"
                    : "§c4文字以上で、1件に絞れるIDを指定してください（/dispatchadmin list で確認）。"));
            return null;
        }
        return matches.getFirst();
    }

    private static String name(NationSavedData nations, UUID nation) {
        return nations.nation(nation).map(NationSavedData.Nation::name).orElse("?");
    }
}
