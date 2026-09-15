package com.ruskserver.moveearth_addtional.s2.technology;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Stable server-side bridge for optional mods whose machine events cannot be inferred from vanilla events.
 * Callers must report only completed, non-cancelled work and must attribute it to a real player.
 */
public final class TechnologyProgressApi {
    private TechnologyProgressApi() { }

    public static void record(ServerPlayer player, TechnologyDefinition.ObjectiveType type,
                              ResourceLocation target, long amount, BlockPos position) {
        if (player == null || type == null || amount <= 0L || player.isCreative() || player.isSpectator()) return;
        NationTechnologySavedData.get(player.server).recordObjective(player, type, target, amount, position);
    }
}
