package com.ruskserver.moveearth_addtional.jobs;

import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
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
        if (!com.ruskserver.moveearth_addtional.economy.EarningEligibility.mayEarnWithNotice(player)) {
            return;
        }
        double xp = rateLimiter.apply(player.getUUID(), definition.id(), baseXp, player.serverLevel().getGameTime());
        JobProgressSavedData.AwardResult result = data.award(player.getUUID(), definition, xp,
                player.getServer().overworld().getGameTime());
        if (result.awardedXp() <= 0) {
            return;
        }

        com.ruskserver.moveearth_addtional.analytics.collector.AnalyticsCollectorManager.INSTANCE.recordJobsXp(player, result.awardedXp());

        // The nation's tax comes out of the award itself; see NationTaxPolicy.
        java.util.UUID nationId = com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(player.getServer())
                .nationIdFor(player.getUUID()).orElse(null);
        int taxPercent = com.ruskserver.moveearth_addtional.s2.nation.NationTaxSavedData.get(player.getServer())
                .percent(nationId);
        EconomyLedgerSavedData.JobIncomeSnapshot income = EconomyLedgerSavedData.get(player.getServer())
                .awardJobIncome(player.getUUID(), result.awardedXp(), System.currentTimeMillis(), nationId, taxPercent);

        JobProgressSavedData.ProgressSnapshot progress = data.snapshot(player.getUUID())
                .progress(definition.id());
        JobProgressBossBar.show(player, definition, progress, result.awardedXp(), income.justEarned(),
                income.justTaxed());

        if (result.leveledUp()) {
            player.sendSystemMessage(MoveEarthMessage.success("JOBS  •  " + definition.displayName()
                    + " がレベル " + result.newLevel() + " になりました"));
        }
    }

    public void clearTransientState() {
        rateLimiter.clear();
    }
}
