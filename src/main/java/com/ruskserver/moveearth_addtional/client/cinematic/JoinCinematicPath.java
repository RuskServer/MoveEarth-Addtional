package com.ruskserver.moveearth_addtional.client.cinematic;

/**
 * Where the join cinematic's camera is at each moment, as a pure function of time, so it is unit tested.
 *
 * <p>The camera looks straight down from high above the player and drops in three cuts, each holding while it
 * sinks a little and turns, then eases from the last height into the player's own eyes and view direction.
 * Heights are given relative to the player's eyes; the caller turns them into world positions.
 */
public final class JoinCinematicPath {
    /** Seconds each high shot holds before the cut to the next. */
    static final double[] SHOT_SECONDS = {1.3, 1.0, 0.8};
    /** Height of each shot as a share of the opening height, at its start and its end. */
    static final double[][] SHOT_HEIGHT = {{1.0, 0.9}, {0.42, 0.37}, {0.15, 0.12}};
    /** Seconds of the final ease from the last shot into the player's eyes. */
    static final double LANDING_SECONDS = 1.4;
    /** The last shot never comes closer than this many blocks above the eyes before the landing. */
    static final double MIN_LAST_HEIGHT = 18.0;
    /** Degrees the view turns across the whole descent. */
    static final double TURN_DEGREES = 50.0;

    private JoinCinematicPath() { }

    public static double totalSeconds() {
        double total = LANDING_SECONDS;
        for (double seconds : SHOT_SECONDS) total += seconds;
        return total;
    }

    /**
     * @param shot      the cut this moment belongs to (0-2), or 3 while landing
     * @param height    blocks above the player's eyes, before landing blends it away
     * @param landing   0 until the landing starts, then eased to 1 at the player's eyes
     * @param yawOffset degrees added to the player's yaw while looking down
     * @param shotStart whether this is the first moment of a cut, for its sound
     */
    public record Frame(int shot, double height, double landing, double yawOffset, boolean shotStart) { }

    /** The frame at {@code seconds} into a descent that opens {@code openingHeight} blocks above the eyes. */
    public static Frame at(double seconds, double openingHeight, double previousSeconds) {
        double opening = Math.max(MIN_LAST_HEIGHT / SHOT_HEIGHT[2][1], openingHeight);
        double t = Math.max(0.0, seconds);
        double start = 0.0;
        double turn = TURN_DEGREES * Math.min(1.0, t / totalSeconds());
        for (int shot = 0; shot < SHOT_SECONDS.length; shot++) {
            double end = start + SHOT_SECONDS[shot];
            if (t < end) {
                double progress = (t - start) / SHOT_SECONDS[shot];
                double share = lerp(SHOT_HEIGHT[shot][0], SHOT_HEIGHT[shot][1], easeOut(progress));
                boolean shotStart = previousSeconds < start;
                return new Frame(shot, opening * share, 0.0, turn, shotStart);
            }
            start = end;
        }
        double landing = Math.min(1.0, (t - start) / LANDING_SECONDS);
        double lastHeight = opening * SHOT_HEIGHT[SHOT_HEIGHT.length - 1][1];
        return new Frame(SHOT_SECONDS.length, lastHeight, easeInOut(landing), turn, previousSeconds < start);
    }

    public static boolean finished(double seconds) {
        return seconds >= totalSeconds();
    }

    static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    static double easeOut(double progress) {
        double p = Math.max(0.0, Math.min(1.0, progress));
        return 1.0 - (1.0 - p) * (1.0 - p);
    }

    static double easeInOut(double progress) {
        double p = Math.max(0.0, Math.min(1.0, progress));
        return p < 0.5 ? 4.0 * p * p * p : 1.0 - Math.pow(-2.0 * p + 2.0, 3.0) / 2.0;
    }

    /**
     * The opening height: as high as the ground below stays inside what is drawn. With Distant Horizons the
     * distant terrain is there to see, so the camera can climb much higher.
     *
     * @param vanillaRenderBlocks the client's render distance in blocks
     * @param lodBlocks           Distant Horizons' LOD distance in blocks, or 0 without it
     */
    public static double openingHeight(int vanillaRenderBlocks, int lodBlocks) {
        double vanilla = Math.max(60.0, Math.min(200.0, vanillaRenderBlocks * 0.7));
        if (lodBlocks <= 0) return vanilla;
        return Math.max(vanilla, Math.min(600.0, lodBlocks * 0.3));
    }
}
