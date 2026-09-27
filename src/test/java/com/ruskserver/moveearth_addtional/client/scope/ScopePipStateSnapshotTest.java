package com.ruskserver.moveearth_addtional.client.scope;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScopePipStateSnapshotTest {
    private static final class State {
        private int frame = 7;
        private Matrix4fc projection = new Matrix4f();
        private Vector3d fog = new Vector3d(1, 2, 3);
        private static int resolution = 1024;
        private final int constant = 42;
    }

    @Test
    void restoresInstanceStateAndCopiesMutableCameraValues() throws Exception {
        State state = new State();
        try (ScopePipStateSnapshot snapshot = new ScopePipStateSnapshot()) {
            snapshot.capture(state, State.class, false);
            state.frame = 99;
            ((Matrix4f) state.projection).scale(4);
            state.fog.set(9, 9, 9);
            State.resolution = 2048;
        }
        assertEquals(7, state.frame);
        assertEquals(1, state.projection.m00());
        assertEquals(new Vector3d(1, 2, 3), state.fog);
        assertEquals(2048, State.resolution);
        assertEquals(42, state.constant);
        State.resolution = 1024;
    }

    @Test
    void restoresStaticStateEvenWhenRenderingThrows() throws Exception {
        assertThrows(IllegalStateException.class, () -> {
            try (ScopePipStateSnapshot snapshot = new ScopePipStateSnapshot()) {
                snapshot.capture(null, State.class, true);
                State.resolution = 4096;
                throw new IllegalStateException("render failed");
            }
        });
        assertEquals(1024, State.resolution);
    }

    @Test
    void closeIsIdempotent() throws Exception {
        State state = new State();
        ScopePipStateSnapshot snapshot = new ScopePipStateSnapshot();
        snapshot.capture(state, State.class, false);
        state.frame = 99;
        snapshot.close();
        state.frame = 12;
        snapshot.close();
        assertEquals(12, state.frame);
    }
}
