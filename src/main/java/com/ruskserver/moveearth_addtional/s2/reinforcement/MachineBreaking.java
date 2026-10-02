package com.ruskserver.moveearth_addtional.s2.reinforcement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Whether a machine may remove a reinforced block.
 *
 * <p>Every protection the reinforcement system has is written against {@code
 * BlockEvent.BreakEvent}, which fires only for a player. A drill, a saw or
 * anything else that removes a block without one goes straight past all of it:
 * no reinforcement HP spent, no siege timer started, no repair delay. Closure
 * is binary, so a single block taken that way ends a siege before it begins.
 *
 * <p>The answer is to refuse rather than to charge. A player who breaks a
 * reinforced block spends one unit of its durability per swing, which is what
 * makes a wall a wall; a drill ticks far faster than a player swings, so
 * letting it deal the same damage would make it the best siege weapon in the
 * game rather than a mining tool. Machines are not siege equipment, so they
 * simply do not bite.
 *
 * <p>The cases where a machine <em>may</em> break are exactly the cases where a
 * player's break is not intercepted either: reinforcement that is switched off,
 * and reinforcement whose protection has lapsed because upkeep went unpaid.
 * Keeping the two rules the same is the point of putting this in one place —
 * they were two rules once, and the second one was missing.
 */
public final class MachineBreaking {

    private MachineBreaking() { }

    /** True when nothing protects the block, so a machine may take it. */
    public static boolean mayBreak(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return true;
        }
        if (isCore(level.getBlockState(pos))) {
            return false;
        }
        if (!mayBreakHeldGoods(level, pos)) {
            return false;
        }
        ReinforcementEntry entry = ReinforcementSavedData.get(level).get(pos).orElse(null);
        if (entry == null || !entry.enabled()) {
            return true;
        }
        return !SiegeDamageService.penaltyAt(level, pos).reinforcementProtectionEnabled();
    }

    /**
     * Territory and vehicle cores take damage only through the siege and vehicle
     * damage rules; a drill would skip their HP, the siege timer and the owner's
     * warning, and a vehicle core's record would be left pointing at nothing.
     * A prison intake is freed only by a rescue; a drill would release everyone
     * held there without one.
     */
    private static boolean isCore(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(com.ruskserver.moveearth_addtional.block.ModBlocks.TERRITORY_CORE.get())
                || state.is(com.ruskserver.moveearth_addtional.block.ModBlocks.VEHICLE_CORE.get())
                || state.is(com.ruskserver.moveearth_addtional.block.ModBlocks.PRISON_INTAKE.get());
    }

    /**
     * Blocks whose removal by a machine would lose goods that belong to someone else.
     *
     * <p>A market station or storage wreckage holds other players' claims and items, and neither
     * survives being broken, so no machine may take one. Nation storage inside a nation's land or a
     * siege boundary is likewise off limits to drills and saws; a drill does not know whose side it
     * is on, and players can still break their own storage by hand. Storage in unclaimed land keeps
     * the old behaviour.
     */
    private static boolean mayBreakHeldGoods(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.is(com.ruskserver.moveearth_addtional.block.ModBlocks.MARKET_STATION.get())
                || state.is(com.ruskserver.moveearth_addtional.block.ModBlocks.STORAGE_WRECKAGE.get())) {
            return false;
        }
        if (!state.is(com.ruskserver.moveearth_addtional.s2.nation.NationStorageEvents.STORAGE_BLOCKS)) {
            return true;
        }
        return com.ruskserver.moveearth_addtional.s2.siege.SiegeLootService.formerOwnerAt(
                level.getServer(), level.dimension().location(), pos) == null;
    }
}
