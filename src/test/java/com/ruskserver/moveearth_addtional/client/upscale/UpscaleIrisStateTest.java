package com.ruskserver.moveearth_addtional.client.upscale;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpscaleIrisStateTest {
    @Test void absentIrisDoesNotResolveOptionalClasses() {
        assertEquals(UpscaleIrisState.State.ABSENT, UpscaleIrisState.state(false, () -> {
            fail("must not query absent Iris"); return true;
        }));
    }

    @Test void packTogglesAreObservedInsteadOfCachingAState() throws Exception {
        var query = UpscaleIrisState.bind(FakeApi.class);
        FakeApi.active = false;
        assertEquals(UpscaleIrisState.State.INACTIVE, UpscaleIrisState.state(true, query));
        FakeApi.active = true;
        assertEquals(UpscaleIrisState.State.ACTIVE, UpscaleIrisState.state(true, query));
        FakeApi.active = false;
        assertEquals(UpscaleIrisState.State.INACTIVE, UpscaleIrisState.state(true, query));
    }

    @Test void unknownOrBrokenApiFailsClosed() throws Exception {
        assertEquals(UpscaleIrisState.State.UNKNOWN, UpscaleIrisState.state(true, () -> null));
        assertEquals(UpscaleIrisState.State.UNKNOWN, UpscaleIrisState.state(true, () -> { throw new LinkageError(); }));
        assertEquals(UpscaleIrisState.State.UNKNOWN, UpscaleIrisState.state(true,
                UpscaleIrisState.bind(BrokenApi.class)));
        assertThrows(NoSuchMethodException.class, () -> UpscaleIrisState.bind(Object.class));
    }

    public static final class FakeApi {
        private static boolean active;
        public static FakeApi getInstance() { return new FakeApi(); }
        public boolean isShaderPackInUse() { return active; }
    }

    public static final class BrokenApi {
        public static BrokenApi getInstance() { return new BrokenApi(); }
        public boolean isShaderPackInUse() { throw new IllegalStateException("not ready"); }
    }
}
