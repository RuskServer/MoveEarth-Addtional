package com.ruskserver.moveearth_addtional.client.upscale;

/** Pure sizing and FSR 1.0 constant setup, kept free of Minecraft classes for unit tests. */
final class UpscalePolicy {
    /** Largest display the passes allocate for (8K); beyond that they stay off. */
    static final long MAX_PIXELS = 7680L * 4320L;

    private UpscalePolicy() { }

    static int renderSize(int display, float factor) {
        return Math.max(1, Math.round(display / factor));
    }

    /** Relative depth jump for depth-based SMAA edges: 1% of the nearer distance. */
    static final float DEPTH_EDGE_THRESHOLD = 0.01F;

    /** Mirror of upscale_smaa_depth_edges.fsh: see the shader for the reasoning. */
    static boolean depthEdge(float c, float n, float beyond, float opposite, float threshold) {
        float jump = Math.abs(c - n);
        return jump > threshold * Math.min(c, n)
                && jump > 2F * Math.min(Math.abs(n - beyond), Math.abs(opposite - c));
    }

    /** View distance from a [0,1] depth-buffer value, given the projection's m22 and m32. */
    static float viewDistance(float depth, float m22, float m32) {
        return m32 / (depth * 2F - 1F + m22);
    }

    /** 0% skips RCAS entirely; EASU then writes the display directly. */
    static boolean rcasEnabled(int percent) { return percent > 0; }

    /** 100% = 0 stops (strongest RCAS), towards 0% = 2 stops (a quarter of that). */
    static float sharpnessStops(int percent) {
        return (100 - Math.max(0, Math.min(100, percent))) / 50F;
    }

    /** FsrRcasCon(): con.x = exp2(-stops). */
    static float[] rcasConstants(float stops) {
        return new float[]{(float) Math.pow(2.0, -stops), 0F, 0F, 0F};
    }

    /** SMAA_RT_METRICS: (1/width, 1/height, width, height). */
    static float[] rtMetrics(int width, int height) {
        return new float[]{1F / width, 1F / height, width, height};
    }

    /**
     * FsrEasuCon() from ffx_fsr1.h as float values (con0..con3, 16 floats); the shader reinterprets
     * their bits exactly as the reference does with its uint constants. Whole input texture is used.
     */
    static float[] easuConstants(float inputWidth, float inputHeight, float outputWidth, float outputHeight) {
        float[] con = new float[16];
        con[0] = inputWidth * (1F / outputWidth);
        con[1] = inputHeight * (1F / outputHeight);
        con[2] = 0.5F * inputWidth * (1F / outputWidth) - 0.5F;
        con[3] = 0.5F * inputHeight * (1F / outputHeight) - 0.5F;
        con[4] = 1F / inputWidth;
        con[5] = 1F / inputHeight;
        con[6] = 1F * (1F / inputWidth);
        con[7] = -1F * (1F / inputHeight);
        con[8] = -1F * (1F / inputWidth);
        con[9] = 2F * (1F / inputHeight);
        con[10] = 1F * (1F / inputWidth);
        con[11] = 2F * (1F / inputHeight);
        con[12] = 0F * (1F / inputWidth);
        con[13] = 4F * (1F / inputHeight);
        con[14] = 0F;
        con[15] = 0F;
        return con;
    }
}
