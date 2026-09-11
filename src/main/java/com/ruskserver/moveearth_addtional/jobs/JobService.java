package com.ruskserver.moveearth_addtional.jobs;

import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.server.level.ServerPlayer;

/** Applies anti-abuse limits, persists awards and gives concise player feedback. */
public final class JobService {
    public static final JobService INSTANCE = new JobService();
    private final JobRateLimiter rateLimiter = new JobRateLimiter();

    private JobService() {
    }

    public void awardBlockBreak(ServerPlayer player, JobDefinition definition, double baseXp) {
        awardAction(player, definition, baseXp);
    }

    public void awardAction(ServerPlayer player, JobDefinition definition, double baseXp) {
        JobProgressSavedData data = JobProgressSavedData.get(player.getServer());
        if (!data.isActive(player.getUUID(), definition.id())) {
            return;
        }
        double xp = rateLimiter.apply(player.getUUID(), definition.id(), baseXp, player.serverLevel().getGameTime());
        JobProgressSavedData.AwardResult result = data.award(player.getUUID(), definition, xp,
                player.getServer().overworld().getGameTime());
        if (result.awardedXp() <= 0) {
            return;
        }

        com.ruskserver.moveearth_addtional.analytics.collector.AnalyticsCollectorManager.INSTANCE.recordJobsXp(player, result.awardedXp());

        JobProgressSavedData.ProgressSnapshot progress = data.snapshot(player.getUUID())
                .progress(definition.id());
        JobProgressBossBar.show(player, definition, progress, result.awardedXp());

        if (result.leveledUp()) {
            player.sendSystemMessage(MoveEarthMessage.success("JOBS  •  " + definition.displayName()
                    + " がレベル " + result.newLevel() + " になりました（+"
                    + result.pointsEarned() + "ポイント）"));
        } else if (result.recurringPointsEarned() > 0) {
            JobProgressSavedData.RecurringPointSnapshot recurring = data.recurringSnapshot(player.getUUID(),
                    player.getServer().overworld().getGameTime());
            player.sendSystemMessage(MoveEarthMessage.success("JOBS  •  継続報酬 +"
                    + result.recurringPointsEarned() + " PT（今時間 " + recurring.pointsInWindow()
                    + "/" + JobPointIncome.MAX_POINTS_PER_WINDOW + "）"));
        }
    }

    public void clearTransientState() {
        rateLimiter.clear();
    }
}
