package com.ruskserver.moveearth_addtional.s2.siege;

/**
 * Crosshair guidance for a player looking at a territory core. Decided on the server so the client never
 * learns ownership, eligibility or charge state beyond what the prompt itself shows.
 */
public record CoreSabotagePrompt(Kind kind, int progressTicks, int progressTotal, int seconds, int tnt) {
    public static final int REQUIRED_TNT = 4;
    public static final CoreSabotagePrompt NONE = new CoreSabotagePrompt(Kind.NONE, 0, 0, 0, 0);

    public enum Kind {
        NONE,
        PLANT, PLANT_NEEDS_TNT, UNAVAILABLE, TOO_FAR,
        PLANTING, ALLY_PLANTING, ARMED,
        ENEMY_PLANTING, DEFUSE, DEFUSE_NEEDS_TOOL, DEFUSING, ALLY_DEFUSING;

        public boolean hasProgress() { return this == PLANTING || this == DEFUSING; }
    }

    /** Server-side view of the charge on the targeted core. */
    public record Charge(boolean armed, int ticks, boolean viewerIsAttacker,
                         boolean defended, boolean viewerIsDefender, int defuseTicks) { }

    public record Viewer(boolean friendly, boolean capable, boolean holdingTool, boolean near, int tnt) { }

    public CoreSabotagePrompt {
        if (kind == null) kind = Kind.NONE;
        progressTotal = Math.max(0, progressTotal);
        progressTicks = Math.max(0, Math.min(progressTotal, progressTicks));
        seconds = Math.max(0, seconds);
        tnt = Math.max(0, Math.min(64, tnt));
    }

    public float progress() {
        return progressTotal <= 0 ? 0F : progressTicks / (float) progressTotal;
    }

    /**
     * @param sabotageAllowed whether this core may be sabotaged by the viewer right now (exposed, open hours,
     *                        no truce or settlement protection); only consulted for hostile viewers
     * @param charge          the charge on this core, or {@code null} when there is none
     */
    public static CoreSabotagePrompt decide(Viewer viewer, boolean sabotageAllowed, Charge charge) {
        if (!viewer.capable()) return NONE;
        if (viewer.friendly()) {
            if (charge == null) return NONE;
            if (!charge.armed()) return status(Kind.ENEMY_PLANTING, installRemaining(charge));
            int fuse = fuseRemaining(charge);
            if (charge.viewerIsDefender()) return new CoreSabotagePrompt(Kind.DEFUSING, charge.defuseTicks(),
                    CoreSabotageDisplayPolicy.DEFUSE_TICKS, fuse, 0);
            if (charge.defended()) return status(Kind.ALLY_DEFUSING, fuse);
            if (!viewer.holdingTool()) return status(Kind.DEFUSE_NEEDS_TOOL, fuse);
            if (!viewer.near()) return status(Kind.TOO_FAR, fuse);
            return status(Kind.DEFUSE, fuse);
        }
        // Hostile viewers only get guidance while holding the welder, so combat aim at a core stays clean.
        if (!viewer.holdingTool()) return NONE;
        if (charge != null) {
            if (charge.armed()) return status(Kind.ARMED, fuseRemaining(charge));
            return charge.viewerIsAttacker()
                    ? new CoreSabotagePrompt(Kind.PLANTING, charge.ticks(), CoreSabotageDisplayPolicy.INSTALL_TICKS,
                    installRemaining(charge), 0)
                    : status(Kind.ALLY_PLANTING, installRemaining(charge));
        }
        if (!sabotageAllowed) return status(Kind.UNAVAILABLE, 0);
        if (!viewer.near()) return status(Kind.TOO_FAR, 0);
        if (viewer.tnt() < REQUIRED_TNT) return new CoreSabotagePrompt(Kind.PLANT_NEEDS_TNT, 0, 0, 0, viewer.tnt());
        return status(Kind.PLANT, 0);
    }

    private static CoreSabotagePrompt status(Kind kind, int seconds) {
        return new CoreSabotagePrompt(kind, 0, 0, seconds, 0);
    }

    private static int installRemaining(Charge charge) {
        return CoreSabotageDisplayPolicy.remainingSeconds(charge.ticks(), CoreSabotageDisplayPolicy.INSTALL_TICKS);
    }

    private static int fuseRemaining(Charge charge) {
        return CoreSabotageDisplayPolicy.remainingSeconds(charge.ticks(), CoreSabotageDisplayPolicy.FUSE_TICKS);
    }
}
