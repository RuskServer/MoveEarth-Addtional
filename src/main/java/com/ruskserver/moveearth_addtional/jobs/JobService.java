package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Applies anti-abuse limits, persists awards and gives concise player feedback. */
public final class JobService {
    public static final JobService INSTANCE = new JobService();
    private final JobRateLimiter rateLimiter = new JobRateLimiter();

    private JobService() {
    }

    public void awardBlockBreak(ServerPlayer player, JobDefinition definition, int baseXp) {
        int xp = rateLimiter.apply(player.getUUID(), definition.id(), baseXp, player.serverLevel().getGameTime());
        JobProgressSavedData.AwardResult result = JobProgressSavedData.get(player.getServer())
                .award(player.getUUID(), definition, xp);
        if (result.awardedXp() <= 0) {
            return;
        }

        if (result.leveledUp()) {
            player.sendSystemMessage(Component.literal("[Jobs] " + definition.displayName()
                    + " がレベル " + result.newLevel() + " になりました（+"
                    + result.pointsEarned() + "ポイント）"));
        } else {
            player.displayClientMessage(Component.literal("[Jobs] " + definition.displayName()
                    + " +" + result.awardedXp() + " XP"), true);
        }
    }

    public void clearTransientState() {
        rateLimiter.clear();
    }
}
