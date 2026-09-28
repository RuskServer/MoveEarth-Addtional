package com.ruskserver.moveearth_addtional.client.upscale;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpscalePolicyTest {
    @Test void presetsMatchAmdRenderResolutions() {
        // 3840x2160 per AMD's FSR 1.0 table: 2954x1662, 2560x1440, 2259x1271, 1920x1080.
        assertEquals(2954, UpscalePolicy.renderSize(3840, 1.3F));
        assertEquals(1662, UpscalePolicy.renderSize(2160, 1.3F));
        assertEquals(2560, UpscalePolicy.renderSize(3840, 1.5F));
        assertEquals(2259, UpscalePolicy.renderSize(3840, 1.7F));
        assertEquals(1080, UpscalePolicy.renderSize(2160, 2.0F));
        assertEquals(1, UpscalePolicy.renderSize(1, 2.0F));
    }

    @Test void zeroSharpnessSkipsRcas() {
        assertFalse(UpscalePolicy.rcasEnabled(0));
        assertTrue(UpscalePolicy.rcasEnabled(1));
    }

    @Test void sharpnessPercentMapsToRcasStops() {
        assertEquals(0F, UpscalePolicy.sharpnessStops(100));
        assertEquals(2F, UpscalePolicy.sharpnessStops(0));
        assertEquals(0.4F, UpscalePolicy.sharpnessStops(80), 1e-6F);
        assertEquals(1F, UpscalePolicy.rcasConstants(0F)[0]);
        assertEquals(0.25F, UpscalePolicy.rcasConstants(2F)[0], 1e-6F);
    }

    @Test void easuConstantsFollowFsrEasuCon() {
        float[] con = UpscalePolicy.easuConstants(1477, 831, 1920, 1080);
        assertEquals(1477F / 1920F, con[0], 1e-6F);
        assertEquals(0.5F * 1477F / 1920F - 0.5F, con[2], 1e-6F);
        assertEquals(1F / 1477F, con[4], 1e-9F);
        assertEquals(-1F / 831F, con[7], 1e-9F);
        assertEquals(2F / 831F, con[9], 1e-9F);
        assertEquals(4F / 831F, con[13], 1e-9F);
        assertEquals(0F, con[15]);
    }

    @Test void rtMetricsAreReciprocalAndSize() {
        assertArrayEquals(new float[]{1F / 1920, 1F / 1080, 1920, 1080}, UpscalePolicy.rtMetrics(1920, 1080));
    }

    private static final float T = UpscalePolicy.DEPTH_EDGE_THRESHOLD;

    @Test void grazingGroundIsNotAnEdgeButStepsAndThinPostsAre() {
        // Ground seen from 1.62 m, one pixel = 0.0011 rad: distance grows smoothly towards the horizon.
        for (int row = 5; row <= 996; row += 7) { // stays below the horizon: 1000 - row - 3 >= 1
            float[] z = new float[4];
            for (int k = 0; k < 4; k++) z[k] = 1.62F / (0.0011F * (1000 - row - k));
            // c = z[1], neighbour = z[2], beyond = z[3], opposite = z[0]
            assertFalse(UpscalePolicy.depthEdge(z[1], z[2], z[3], z[0], T), "ground row " + row);
        }
        // A block step on a flat hill top 60 m away: 60 -> 61 m with flat surroundings.
        assertTrue(UpscalePolicy.depthEdge(60F, 61F, 61.01F, 59.99F, T));
        // A one-pixel fence post 5 m away in front of terrain 50 m away.
        assertTrue(UpscalePolicy.depthEdge(5F, 50F, 50F, 50F, T));
        // Same surface, tiny noise: no edge.
        assertFalse(UpscalePolicy.depthEdge(30F, 30.1F, 30.2F, 29.9F, T));
    }

    @Test void viewDistanceInvertsTheProjection() {
        var projection = new org.joml.Matrix4f().perspective(1.2F, 16F / 9F, 0.05F, 1000F);
        for (float distance : new float[]{0.5F, 10F, 200F, 900F}) {
            var clip = projection.transform(new org.joml.Vector4f(0F, 0F, -distance, 1F));
            float depth = (clip.z / clip.w + 1F) * 0.5F;
            // Float depth near 1 loses precision far away (0.1% at 900 m, like a 24-bit depth buffer),
            // well below the 1% edge threshold.
            assertEquals(distance, UpscalePolicy.viewDistance(depth, projection.m22(), projection.m32()),
                    distance * 2e-3F);
        }
    }
}
