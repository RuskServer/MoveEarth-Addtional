package com.ruskserver.moveearth_addtional.client;

/** Frame-rate-independent fade and intensity rules for the gas-mask shader. */
public final class GasMaskVisualPolicy {
    private GasMaskVisualPolicy() { }

    public static float approach(float current, float target, float elapsedSeconds) {
        float elapsed = Math.max(0.0F, Math.min(0.25F, elapsedSeconds));
        float factor = 1.0F - (float) Math.exp(-8.0F * elapsed);
        return current + (target - current) * factor;
    }

    public static float fogStrength(float filterPercent) {
        return clamp((0.25F - filterPercent) / 0.25F);
    }

    public static float panicStrength(float oxygenPercent) {
        return clamp((0.40F - oxygenPercent) / 0.40F);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
