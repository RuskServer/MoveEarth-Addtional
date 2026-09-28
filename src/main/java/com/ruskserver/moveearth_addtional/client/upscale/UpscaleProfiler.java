package com.ruskserver.moveearth_addtional.client.upscale;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * GPU timestamps around the world pass and each anti-aliasing pass, read a few frames later so the
 * CPU never waits for the GPU, plus a count of render-target reallocations per second. Only runs
 * while the status HUD is shown.
 */
final class UpscaleProfiler {
    static final int WORLD_START = 0, WORLD_END = 1, SMAA_END = 2, EASU_END = 3, FRAME_END = 4;
    private static final int MARKS = 5, SLOTS = 4;
    private static final double SMOOTHING = 0.1;
    private static int[][] queries;
    private static final boolean[][] written = new boolean[SLOTS][MARKS];
    private static int slot;
    private static boolean unsupported;
    /** Smoothed milliseconds: world (incl. AFTER_LEVEL), SMAA, EASU, RCAS. */
    private static final double[] millis = new double[4];
    private static int reallocations, reallocationsPerSecond;
    private static long windowStart;

    private UpscaleProfiler() { }

    static boolean enabled() {
        if (unsupported || !UpscaleClientConfig.showStatus()) return false;
        if (queries == null) {
            var caps = GL.getCapabilities();
            if (!caps.OpenGL33 && !caps.GL_ARB_timer_query) {
                unsupported = true;
                return false;
            }
            queries = new int[SLOTS][MARKS];
            for (int[] frame : queries) GL15.glGenQueries(frame);
        }
        return true;
    }

    static void mark(int index) {
        if (!enabled()) return;
        GL33.glQueryCounter(queries[slot][index], GL33.GL_TIMESTAMP);
        written[slot][index] = true;
    }

    /** Called once per frame after the last mark; reads the oldest slot if the GPU has finished it. */
    static void endFrame() {
        if (!enabled()) return;
        slot = (slot + 1) % SLOTS;
        boolean[] done = written[slot];
        if (done[WORLD_START] && done[FRAME_END]
                && GL15.glGetQueryObjecti(queries[slot][FRAME_END], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            long[] time = new long[MARKS];
            for (int i = 0; i < MARKS; i++) {
                time[i] = done[i] ? GL33.glGetQueryObjecti64(queries[slot][i], GL15.GL_QUERY_RESULT) : -1L;
            }
            smooth(0, time[WORLD_START], time[WORLD_END]);
            smooth(1, time[WORLD_END], time[SMAA_END]);
            smooth(2, time[SMAA_END], time[EASU_END]);
            if (done[EASU_END]) smooth(3, time[EASU_END], time[FRAME_END]);
        }
        java.util.Arrays.fill(done, false);
    }

    private static void smooth(int index, long start, long end) {
        if (start < 0L || end < start) return;
        double value = (end - start) / 1_000_000.0;
        millis[index] = millis[index] == 0.0 ? value : millis[index] + (value - millis[index]) * SMOOTHING;
    }

    static void reallocated() {
        reallocations++;
    }

    static int reallocationsPerSecond() {
        long now = System.nanoTime();
        if (now - windowStart >= 1_000_000_000L) {
            reallocationsPerSecond = reallocations;
            reallocations = 0;
            windowStart = now;
        }
        return reallocationsPerSecond;
    }

    static double millis(int index) { return millis[index]; }

    static void release() {
        if (queries != null) for (int[] frame : queries) GL15.glDeleteQueries(frame);
        queries = null;
        for (boolean[] frame : written) java.util.Arrays.fill(frame, false);
        java.util.Arrays.fill(millis, 0.0);
    }
}
