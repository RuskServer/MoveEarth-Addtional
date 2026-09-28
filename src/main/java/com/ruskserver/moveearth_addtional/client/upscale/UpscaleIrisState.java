package com.ruskserver.moveearth_addtional.client.upscale;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import net.neoforged.fml.ModList;

/** Optional public API query. Cache reflection metadata, never cache the changing pack state. */
final class UpscaleIrisState {
    enum State { ABSENT, INACTIVE, ACTIVE, UNKNOWN }
    private static Supplier<Boolean> query;

    private UpscaleIrisState() { }

    static State current() {
        boolean present = ModList.get().isLoaded("iris");
        return state(present, () -> {
            if (query == null) {
                try {
                    query = bind(Class.forName("net.irisshaders.iris.api.v0.IrisApi"));
                } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                    query = () -> null;
                }
            }
            return query.get();
        });
    }

    static State state(boolean present, Supplier<Boolean> query) {
        if (!present) return State.ABSENT;
        try {
            Boolean active = query.get();
            return active == null ? State.UNKNOWN : active ? State.ACTIVE : State.INACTIVE;
        } catch (RuntimeException | LinkageError exception) {
            return State.UNKNOWN;
        }
    }

    static Supplier<Boolean> bind(Class<?> api) throws ReflectiveOperationException {
        Method instance = api.getMethod("getInstance");
        Method active = api.getMethod("isShaderPackInUse");
        return () -> {
            try {
                Object value = active.invoke(instance.invoke(null));
                return value instanceof Boolean result ? result : null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                return null;
            }
        };
    }
}
