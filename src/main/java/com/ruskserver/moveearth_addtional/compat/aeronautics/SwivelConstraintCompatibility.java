package com.ruskserver.moveearth_addtional.compat.aeronautics;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import net.neoforged.fml.ModList;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime guards for the optional swivel-bearing constraint replacement.
 */
public final class SwivelConstraintCompatibility {
    private static final AtomicBoolean ENABLED_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean VERSION_WARNING_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();
    private static volatile Boolean testedRuntime;

    private SwivelConstraintCompatibility() {
    }

    public static boolean enabledFor(double normal1X, double normal1Y, double normal1Z,
                                     double normal2X, double normal2Y, double normal2Z) {
        boolean enabled = AeronauticsSwivelConfig.hingeConstraintEnabled()
                && (AeronauticsSwivelConfig.allowUntestedVersions() || testedRuntime());
        boolean replace = SwivelConstraintPolicy.shouldReplace(
                enabled,
                AeronauticsSwivelConfig.horizontalOnly(),
                normal1X, normal1Y, normal1Z,
                normal2X, normal2Y, normal2Z
        );
        if (replace && ENABLED_LOGGED.compareAndSet(false, true)) {
            Moveearth_addtional.LOGGER.info(
                    "MoveEarth hinge-style swivel drive is active (horizontalOnly={}, servo fallback enabled)",
                    AeronauticsSwivelConfig.horizontalOnly()
            );
        }
        return replace;
    }

    public static void logFailure(Throwable failure) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            Moveearth_addtional.LOGGER.error(
                    "MoveEarth could not create a hinge-style swivel constraint; using the original rotary constraint",
                    failure
            );
        }
    }

    static boolean testedVersionStrings(String simulatedVersion, String sableVersion) {
        return isVersionFamily(simulatedVersion, 1, 3)
                && isVersionFamily(sableVersion, 2, 0);
    }

    private static boolean testedRuntime() {
        Boolean cached = testedRuntime;
        if (cached != null) {
            return cached;
        }
        String simulatedVersion = versionOf("simulated");
        String sableVersion = versionOf("sable");
        boolean tested = testedVersionStrings(simulatedVersion, sableVersion);
        testedRuntime = tested;
        if (!tested && VERSION_WARNING_LOGGED.compareAndSet(false, true)) {
            Moveearth_addtional.LOGGER.warn(
                    "MoveEarth swivel optimization disabled for untested runtime: simulated={}, sable={}",
                    simulatedVersion,
                    sableVersion
            );
        }
        return tested;
    }

    private static String versionOf(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("missing");
    }

    private static boolean isVersionFamily(String version, int expectedMajor, int expectedMinor) {
        String[] parts = version.split("[.-]", 3);
        if (parts.length < 2) {
            return false;
        }
        try {
            return Integer.parseInt(parts[0]) == expectedMajor
                    && Integer.parseInt(parts[1]) == expectedMinor;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
