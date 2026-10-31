package com.ruskserver.moveearth_addtional.s2.dispatch;

import com.ruskserver.moveearth_addtional.config.RecoveryDispatchConfig;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;

/**
 * Tells whoever a dispatch contract is waiting on that it is their turn, and the hired players where and when
 * they fight. A contract passes through three sides and up to five clicks; without a word to each, it sat in
 * the screen until it expired.
 */
final class DispatchNudges {
    private static final String KEY = "message.moveearth_addtional.dispatch.";

    private DispatchNudges() {
    }

    static void created(MinecraftServer server, DispatchContractSavedData.Contract contract) {
        NationSavedData nations = NationSavedData.get(server);
        tellNation(server, contract.providerNation(), S2Permission.MANAGE_DISPATCH, Component.translatable(
                KEY + "request", name(nations, contract.employerNation()), side(contract), target(server, contract)));
        tellPlayers(server, contract.participants(), Component.translatable(KEY + "chosen",
                name(nations, contract.employerNation()), side(contract)));
    }

    /** After an approval or consent: whoever the next step now waits on. */
    static void progressed(MinecraftServer server, DispatchContractSavedData.Contract before,
                           DispatchContractSavedData.Contract after) {
        if (!before.providerApproved() && after.providerApproved()) {
            Set<UUID> waiting = new java.util.LinkedHashSet<>(after.participants());
            waiting.removeAll(after.consents());
            tellPlayers(server, waiting, Component.translatable(KEY + "consent_needed",
                    name(NationSavedData.get(server), after.employerNation())));
        }
        boolean readyBefore = DispatchContractPolicy.mayFund(before.employerApproved(), before.providerApproved(),
                before.participants().size(), before.consents().size());
        boolean readyAfter = DispatchContractPolicy.mayFund(after.employerApproved(), after.providerApproved(),
                after.participants().size(), after.consents().size());
        if (!readyBefore && readyAfter) {
            tellNation(server, after.employerNation(), S2Permission.MANAGE_TREASURY,
                    Component.translatable(KEY + "fund_needed", name(NationSavedData.get(server), after.providerNation())));
        }
    }

    static void funded(MinecraftServer server, DispatchContractSavedData.Contract contract) {
        NationSavedData nations = NationSavedData.get(server);
        // Hitting first counts for the mercenary's own nation and starts its own war, which the contract
        // will not join: they wait for the employer (or, defending, for the attacker).
        Component wait = contract.side() == DispatchContractSavedData.Side.OFFENSE
                ? Component.translatable(KEY + "wait_offense", name(nations, contract.employerNation()), target(server, contract))
                : Component.translatable(KEY + "wait_defense", name(nations, contract.opponentNation()), target(server, contract));
        tellPlayers(server, contract.participants(), wait);
        tellNation(server, contract.providerNation(), S2Permission.MANAGE_DISPATCH, wait);
    }

    static void activated(MinecraftServer server, DispatchContractSavedData.Contract contract,
                          SiegeSavedData.SiegeRecord siege) {
        tellPlayers(server, contract.participants(), Component.translatable(KEY + "activated",
                name(NationSavedData.get(server), contract.employerNation()), side(contract),
                siege.corePos().getX(), siege.corePos().getY(), siege.corePos().getZ(),
                RecoveryDispatchConfig.dispatchBattlefieldRadius()));
    }

    /** "Nation's capital (x, z)", for the parties of a contract; the screen shows the same. */
    static Component target(MinecraftServer server, DispatchContractSavedData.Contract contract) {
        var core = TerritorySavedData.get(server).coreById(contract.targetCoreId()).orElse(null);
        if (core == null) return Component.literal("?");
        return Component.translatable(KEY + "target", name(NationSavedData.get(server), core.nationId()),
                Component.translatable(KEY + "core." + core.type().name().toLowerCase(java.util.Locale.ROOT)),
                core.pos().getX(), core.pos().getZ());
    }

    private static Component side(DispatchContractSavedData.Contract contract) {
        return Component.translatable("screen.moveearth_addtional.dispatch.side."
                + contract.side().name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String name(NationSavedData nations, UUID nation) {
        return nation == null ? "?" : nations.nation(nation).map(NationSavedData.Nation::name).orElse("?");
    }

    private static void tellPlayers(MinecraftServer server, Set<UUID> players, Component message) {
        for (UUID id : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(MoveEarthMessage.info(message));
        }
    }

    private static void tellNation(MinecraftServer server, UUID nationId, S2Permission permission, Component message) {
        NationSavedData nations = NationSavedData.get(server);
        var nation = nations.nation(nationId).orElse(null);
        if (nation == null) return;
        for (UUID member : nation.members().keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null && nations.can(member, permission)) {
                player.sendSystemMessage(MoveEarthMessage.info(message));
            }
        }
    }
}
