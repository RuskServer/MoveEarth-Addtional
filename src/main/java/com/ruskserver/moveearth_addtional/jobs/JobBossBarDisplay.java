package com.ruskserver.moveearth_addtional.jobs;

/** Pure formatter for the temporary Jobs progress boss bar. */
public final class JobBossBarDisplay {
    private JobBossBarDisplay() {
    }

    public static Display create(String jobName, int level, double xpInLevel,
                                 double xpForNextLevel, double gainedXp) {
        boolean atMaxLevel = xpForNextLevel <= 0.0D;
        String progressText = atMaxLevel
                ? "MAX"
                : JobXpFormat.format(xpInLevel) + "/" + JobXpFormat.format(xpForNextLevel);
        String title = "Lvl " + level + " " + jobName + ": " + progressText
                + " XP (+" + JobXpFormat.format(gainedXp) + ")";
        float progress = atMaxLevel ? 1.0F
                : (float) Math.max(0.0D, Math.min(1.0D, xpInLevel / xpForNextLevel));
        return new Display(title, progress);
    }

    public record Display(String title, float progress) {
    }
}
