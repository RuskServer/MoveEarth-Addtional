package com.ruskserver.moveearth_addtional.client.compat;

public final class TaczArmRenderContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private TaczArmRenderContext() { }

    public static boolean isRenderingArm() {
        return DEPTH.get() > 0;
    }

    public static void render(Runnable renderer) {
        int previous = DEPTH.get();
        DEPTH.set(previous + 1);
        try {
            renderer.run();
        } finally {
            if (previous == 0) DEPTH.remove();
            else DEPTH.set(previous);
        }
    }
}
