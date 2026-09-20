package com.ruskserver.moveearth_addtional.compat.aeronautics;

/** Pure identification and capacity policy for Create: Simulated portable engines. */
public final class PortableEngineBalancePolicy {
    private static final String SIMULATED_NAMESPACE = "simulated";
    private static final String PORTABLE_ENGINE_SUFFIX = "_portable_engine";

    private PortableEngineBalancePolicy() {
    }

    public static boolean isPortableEngine(String namespace, String path) {
        return SIMULATED_NAMESPACE.equals(namespace)
                && path != null
                && path.endsWith(PORTABLE_ENGINE_SUFFIX);
    }

    public static float cappedCapacity(boolean enabled, String namespace, String path,
                                       float registeredCapacity, double maximumCapacity) {
        if (!enabled || !isPortableEngine(namespace, path)) {
            return registeredCapacity;
        }
        return (float) Math.min(registeredCapacity, Math.max(0.0D, maximumCapacity));
    }
}
