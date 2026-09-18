package com.ruskserver.moveearth_addtional.client.menu;

final class StartupFlowPolicy {
    static final long WARNING_MINIMUM_MILLIS = 1_500L;
    static final long LOGO_DURATION_MILLIS = 1_800L;
    static final long LOGO_SKIP_DELAY_MILLIS = 250L;
    static final long MENU_ENTRANCE_MILLIS = 650L;

    private StartupFlowPolicy() { }

    static boolean warningCanContinue(long elapsedMillis) {
        return elapsedMillis >= WARNING_MINIMUM_MILLIS;
    }

    static boolean logoCanSkip(long elapsedMillis) {
        return elapsedMillis >= LOGO_SKIP_DELAY_MILLIS;
    }

    static boolean logoFinished(long elapsedMillis) {
        return elapsedMillis >= LOGO_DURATION_MILLIS;
    }

    static float easedProgress(long elapsedMillis, long durationMillis) {
        if (durationMillis <= 0L) return 1.0F;
        float linear = Math.max(0.0F, Math.min(1.0F, elapsedMillis / (float) durationMillis));
        float inverse = 1.0F - linear;
        return 1.0F - inverse * inverse * inverse;
    }
}
