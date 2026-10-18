package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.combat.RealPlayers;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.UUID;

/**
 * Breaking an unreinforced block on another nation's land opens a Siege on the core that holds it, as hitting
 * its reinforcement does. A Siege used to need reinforcement or the core, so a skirmish that wrecked an
 * outpost's open buildings, or won a battle over its land, was not a war at all. The break goes through the
 * same gates as an attack on reinforcement and is refused when one says no: a peace truce, a capital
 * rebuilding, a failed-Siege lockout, a former nation, or war consent not yet given to a nation the breaker is
 * not hostile to. Being only a block, it opens the Siege's first phase; the rolling phase still takes damage
 * to reinforcement or the core. Grass, crops and the like are left out ({@link TerritoryBreakPolicy}).
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TerritoryBreakSiege {
    /** Breaks the reinforcement handler already dealt with this tick, so they are not a second attack. */
    private static final java.util.Set<Handled> REINFORCED_BREAKS = new java.util.HashSet<>();

    private record Handled(net.minecraft.resources.ResourceLocation dimension, long pos) { }

    private TerritoryBreakSiege() {
    }

    /** Called by the reinforcement handler for a break on a reinforced block, before it may drop the record. */
    public static void reinforcedBreak(ServerLevel level, BlockPos pos) {
        REINFORCED_BREAKS.add(new Handled(level.dimension().location(), pos.asLong()));
    }

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        REINFORCED_BREAKS.clear();
    }

    /** After the protections that refuse a break outright, before the intrusion notice counts it. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator() || RealPlayers.real(player) == null) return;
        BlockPos pos = event.getPos();
        // Reinforced blocks are already a Siege attack; ReinforcementEvents handles them, and may have dropped
        // the record of a lapsed one by now.
        if (REINFORCED_BREAKS.contains(new Handled(level.dimension().location(), pos.asLong()))
                || ReinforcementSavedData.get(level).get(pos).isPresent()) return;
        BlockState state = event.getState();
        if (!TerritoryBreakPolicy.counts(state.getDestroySpeed(level, pos), state.canBeReplaced())) return;
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                .controllingCore(level.getServer(), level.dimension().location(), pos).orElse(null);
        if (core == null) return;
        NationSavedData nations = NationSavedData.get(level.getServer());
        UUID breakerNation = nations.nationIdFor(player.getUUID()).orElse(null);
        if (breakerNation != null && (breakerNation.equals(core.nationId())
                || nations.isAllied(breakerNation, core.nationId()))) return;

        // The refusals that need no war first, so nobody is asked to confirm a war for a break refused anyway.
        if (OfflineDefenseService.settlementProtected(level, pos)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.moveearth_addtional.siege.settlement_truce"), true);
            return;
        }
        if (breakerNation != null && underOwnRecoveryProtection(level, breakerNation)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.moveearth_addtional.siege.break_recovery_protected"), true);
            return;
        }
        Component blocked = SiegeService.breakBlockReason(player, level, pos);
        if (blocked != null) {
            event.setCanceled(true);
            player.displayClientMessage(blocked, true);
            return;
        }
        SiegeSavedData.AttemptResult result = SiegeService.recordAttack(player, level, pos, false);
        if (result.status() == SiegeSavedData.AttemptStatus.RECOVERY_PROTECTED) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.moveearth_addtional.siege.break_recovery_protected"), true);
        }
    }

    private static boolean underOwnRecoveryProtection(ServerLevel level, UUID nationId) {
        var recoveries = com.ruskserver.moveearth_addtional.s2.recovery.NationRecoverySavedData.get(level.getServer());
        return recoveries.eligibleForNation(nationId,
                        com.ruskserver.moveearth_addtional.s2.time.OpenTimeService.now(level.getServer()))
                .filter(episode -> !recoveries.protectionWaived(episode.id())).isPresent();
    }
}
