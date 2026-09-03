package com.ruskserver.moveearth_addtional.jobs;

import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Centralized attribution for TaCZ/Create gunsmith actions. */
public final class GunSmithJobService {
    private GunSmithJobService() {}

    public static void awardCraft(ServerPlayer player, ItemStack result) {
        if (!eligible(player) || result.isEmpty()) return;
        for (JobDefinition definition : JobDefinitions.INSTANCE.all()) {
            double xp = Math.max(definition.gunCraftXp(result), definition.attachmentCraftXp(result));
            if (xp > 0) JobService.INSTANCE.awardAction(player, definition, xp);
        }
    }

    public static void awardDisassembly(ServerPlayer player, ItemStack input) {
        if (!eligible(player) || input.isEmpty() || com.tacz.guns.api.item.IGun.getIGunOrNull(input) == null) return;
        for (JobDefinition definition : JobDefinitions.INSTANCE.all()) {
            if (definition.gunDisassemblyXp() > 0) {
                JobService.INSTANCE.awardAction(player, definition, definition.gunDisassemblyXp());
            }
        }
    }

    private static boolean eligible(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator()
                && !player.level().dimension().equals(PvpMatchManager.ARENA);
    }
}
