package com.ruskserver.moveearth_addtional.client.scope;

/**
 * Where a riflescope's shadow falls. Pure, so it is tested without a renderer.
 *
 * <p>A real scope shows a full picture only while the eye sits in its eye box, a small spot behind the
 * eyepiece. Off that spot the image is cut by a dark crescent on the far side, and the higher the
 * magnification the smaller the spot, so the same small misalignment costs more of the view. Here the
 * eye is the camera at the screen centre: when the gun sways, bobs, recoils or is still coming up, its
 * eyepiece is off centre, and turning the view quickly adds a short lag on top. The clear part of the
 * lens is a disc shifted away from that offset; everything of the eyepiece outside it is shadow.
 *
 * <p>All positions are framebuffer pixels with the origin at the bottom left, as {@code gl_FragCoord}.
 */
public final class ScopeShadowMath {
    /** How quickly the turning lag fades, in seconds. */
    static final double LAG_SECONDS = 0.12;
    /** Largest lag, in lens radii, however fast the view turns. */
    static final double MAX_LAG = 1.2;

    private ScopeShadowMath() { }

    /**
     * @param clearX       centre of the clear disc
     * @param clearRadius  radius of the clear disc
     * @param softness     width of the blurred shadow edge
     * @param darkness     how black the shadow gets, 0 to 1
     */
    public record Shadow(float clearX, float clearY, float clearRadius, float softness, float darkness) { }

    /**
     * @param lensX         eyepiece centre on screen
     * @param lensRadius    eyepiece radius on screen
     * @param screenX       screen centre, where the eye is
     * @param lagX          turning lag in lens radii ({@link #lag})
     * @param magnification the scope's zoom; 1 for none
     * @param aimProgress   0 while lowered, 1 when fully aimed
     * @param strength      configured strength; 0 turns the shadow off
     */
    public static Shadow shadow(double lensX, double lensY, double lensRadius, double screenX, double screenY,
                                double lagX, double lagY, double magnification, double aimProgress,
                                double strength) {
        double radius = Math.max(1.0, lensRadius);
        double s = Math.max(0.0, strength);
        // A 4x scope is the reference; a 12x one is about 1.7 times as touchy.
        double sensitivity = s * Math.sqrt(Math.max(1.0, magnification) / 4.0);
        double offsetX = lensX - screenX + lagX * radius;
        double offsetY = lensY - screenY + lagY * radius;
        double clearX = lensX - offsetX * (1.0 + sensitivity);
        double clearY = lensY - offsetY * (1.0 + sensitivity);
        // Until the scope is fully up the eye is not yet in the eye box: the clear disc grows in as it rises.
        double settle = 0.55 + 0.45 * smooth(clamp(aimProgress, 0.0, 1.0));
        double clearRadius = radius * (1.0 - 0.05 * Math.min(s, 2.0)) * settle;
        double darkness = clamp(0.9 * Math.min(s, 1.0), 0.0, 0.95);
        return new Shadow((float) clearX, (float) clearY, (float) clearRadius,
                (float) (radius * 0.14), (float) darkness);
    }

    /**
     * The turning lag after one frame, in lens radii: the view turning right leaves the eye behind, so
     * the image lags left and the shadow eats the right of the lens, then the lag fades.
     *
     * @param degreesRight  how far the view turned right this frame
     * @param degreesUp     how far it turned up this frame
     * @param seconds       frame time
     */
    public static double[] lag(double lagX, double lagY, double degreesRight, double degreesUp,
                               double seconds, double magnification) {
        double gain = 0.02 * Math.max(1.0, magnification);
        double decay = Math.exp(-Math.max(0.0, seconds) / LAG_SECONDS);
        double x = lagX * decay - degreesRight * gain;
        double y = lagY * decay - degreesUp * gain;
        double length = Math.hypot(x, y);
        if (length > MAX_LAG) {
            x *= MAX_LAG / length;
            y *= MAX_LAG / length;
        }
        return new double[] {x, y};
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
