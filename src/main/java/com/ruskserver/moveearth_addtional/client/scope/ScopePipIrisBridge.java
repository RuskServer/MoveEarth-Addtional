package com.ruskserver.moveearth_addtional.client.scope;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import java.lang.reflect.Field;
import java.util.Objects;
import net.neoforged.fml.ModList;

public final class ScopePipIrisBridge {
    private static boolean pipelineHook;
    private static boolean handHook;
    private static Object lensPipeline;
    private static Object ownerPipeline;
    private static Object ownerManager;
    private static Object ownerDimension;
    private static Object selectedPipeline;

    private ScopePipIrisBridge() { }

    private static Class<?> irisClass(String name) throws ClassNotFoundException {
        return Class.forName("net.irisshaders.iris." + name);
    }

    public static Object selectPipeline() {
        pipelineHook = true;
        return ScopePipRenderer.isRenderingLens() ? selectedPipeline : null;
    }

    public static boolean skipHand() {
        handHook = true;
        return ScopePipRenderer.isRenderingLens();
    }

    public static String unavailableReason() {
        if (!ModList.get().isLoaded("iris")) return null;
        try {
            if (!(boolean) irisClass("Iris").getMethod("isPackInUseQuick").invoke(null)) return null;
            if (!ScopePipConfig.IRIS_EXPERIMENTAL.get()) return "iris_experimental_disabled";
            if (ModList.get().isLoaded("distanthorizons")) return "iris_distant_horizons_unsupported";
            if (!pipelineHook || !handHook) return "iris_hooks_not_ready";
            Object manager = irisClass("Iris").getMethod("getPipelineManager").invoke(null);
            Object pipeline = manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            return pipeline == null ? "iris_pipeline_not_ready" : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            return "iris_api_unsupported";
        }
    }

    public static void renderLens(Runnable renderWorld) {
        if (!ModList.get().isLoaded("iris")) {
            renderWorld.run();
            return;
        }
        try {
            Class<?> iris = irisClass("Iris");
            if (!(boolean) iris.getMethod("isPackInUseQuick").invoke(null)) {
                release();
                renderWorld.run();
                return;
            }
            Object manager = iris.getMethod("getPipelineManager").invoke(null);
            Field pipelineField = manager.getClass().getDeclaredField("pipeline");
            pipelineField.setAccessible(true);
            Object original = pipelineField.get(manager);
            Object dimension = iris.getMethod("getCurrentDimension").invoke(null);
            try (ScopePipStateSnapshot snapshot = new ScopePipStateSnapshot()) {
                captureSingleton(snapshot, "uniforms.CapturedRenderingState");
                captureSingleton(snapshot, "shaderpack.materialmap.WorldRenderingSettings");
                snapshot.capture(null, irisClass("shadows.ShadowRenderer"), true);
                try {
                    if (lensPipeline == null || original != ownerPipeline || manager != ownerManager
                            || !Objects.equals(dimension, ownerDimension)) {
                        release();
                        Object pack = ((java.util.Optional<?>) iris.getMethod("getCurrentPack").invoke(null)).orElseThrow();
                        Object programs = pack.getClass().getMethod("getProgramSet", dimension.getClass()).invoke(pack, dimension);
                        lensPipeline = irisClass("pipeline.IrisRenderingPipeline").getConstructor(programs.getClass()).newInstance(programs);
                        ownerPipeline = original;
                        ownerManager = manager;
                        ownerDimension = dimension;
                    }
                    selectedPipeline = lensPipeline;
                    pipelineField.set(manager, lensPipeline);
                    renderWorld.run();
                } finally {
                    selectedPipeline = null;
                    pipelineField.set(manager, original);
                }
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Iris scope rendering bridge failed", exception);
        }
    }

    private static void captureSingleton(ScopePipStateSnapshot snapshot, String name) throws ReflectiveOperationException {
        Class<?> type = irisClass(name);
        snapshot.capture(type.getField("INSTANCE").get(null), type, false);
    }

    public static void release() {
        Object previous = lensPipeline;
        lensPipeline = null;
        ownerPipeline = null;
        ownerManager = null;
        ownerDimension = null;
        if (previous == null) return;
        try {
            previous.getClass().getMethod("destroy").invoke(previous);
        } catch (ReflectiveOperationException | LinkageError exception) {
            Moveearth_addtional.LOGGER.warn("Could not release Iris scope pipeline", exception);
        }
    }
}
