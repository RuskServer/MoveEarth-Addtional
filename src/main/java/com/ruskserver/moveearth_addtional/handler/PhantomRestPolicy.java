package com.ruskserver.moveearth_addtional.handler;

final class PhantomRestPolicy {
    // Vanilla's phantom spawner cannot select a player until the rest timer is
    // greater than 72,000 ticks (three in-game days).
    static final int MAX_PROTECTED_REST_TICKS = 72_000;

    private PhantomRestPolicy() {
    }

    static boolean isProtectedFromPhantoms(int timeSinceRestTicks) {
        return timeSinceRestTicks <= MAX_PROTECTED_REST_TICKS;
    }
}
