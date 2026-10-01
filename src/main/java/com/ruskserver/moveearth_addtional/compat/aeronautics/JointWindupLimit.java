package com.ruskserver.moveearth_addtional.compat.aeronautics;

/**
 * Anti-windup for driven joints: Absolute Kinematics hinges and Create: Simulated
 * swivel bearings. Both advance their target angle by the shaft speed every tick
 * whether or not the moving part follows, and their servo pushes in proportion
 * to how far the target is ahead. Held back by a piston or a wall, the target ran
 * far ahead and the stored push was released all at once when the obstruction
 * cleared. Here the target stops advancing once it leads the actual angle by the
 * allowed margin. It is never pulled back, so a joint holding still under load
 * keeps its full strength.
 */
public final class JointWindupLimit {
    /** A fast joint may lead by this many ticks of its own motion. */
    static final double LEAD_TICKS = 2.0;

    private JointWindupLimit() { }

    /**
     * For a joint whose angles do not wrap (the hinge's stay within ±90 degrees).
     *
     * @param previous       target angle before this tick's advance, in degrees
     * @param proposed       target angle after the joint's own advance
     * @param current        the moving part's actual angle
     * @param minLeadDegrees smallest lead allowed regardless of speed
     * @return the target to keep; between {@code previous} and {@code proposed}
     */
    public static double limitAdvance(double previous, double proposed, double current, double minLeadDegrees) {
        double step = proposed - previous;
        if (step == 0.0 || !Double.isFinite(current)) return proposed;
        double lead = Math.max(minLeadDegrees, Math.abs(step) * LEAD_TICKS);
        if (step > 0.0) return Math.min(proposed, Math.max(previous, current + lead));
        return Math.max(proposed, Math.min(previous, current - lead));
    }

    /**
     * For a joint that turns freely, whose angles wrap every 360 degrees (the swivel
     * bearing keeps its target in (-360, 360)). Angles are compared the short way
     * round; an unchanged advance returns {@code proposed} exactly.
     */
    public static double limitWrappedAdvance(double previous, double proposed, double current, double minLeadDegrees) {
        if (!Double.isFinite(current)) return proposed;
        double step = wrap(proposed - previous);
        if (step == 0.0) return proposed;
        double currentNearPrevious = previous - wrap(previous - current);
        double limited = limitAdvance(previous, previous + step, currentNearPrevious, minLeadDegrees);
        return limited == previous + step ? proposed : limited % 360.0;
    }

    /** The same angle in [-180, 180). */
    public static double wrap(double degrees) {
        double wrapped = (degrees + 180.0) % 360.0;
        if (wrapped < 0.0) wrapped += 360.0;
        return wrapped - 180.0;
    }
}
