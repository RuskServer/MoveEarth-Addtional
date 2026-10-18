package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.EntityCapability;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Mekanism's radiation API reached by reflection, since Mekanism is not a
 * compile dependency. Everything is a no-op when Mekanism is absent or its API
 * has changed shape.
 */
final class MekanismRadiationBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Handles handles;
    private static boolean resolved;

    private MekanismRadiationBridge() {
    }

    static boolean available() {
        Handles h = handles();
        if (h == null) return false;
        try {
            return (boolean) h.isRadiationEnabled.invoke(h.manager);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Adds {@code dose} Sv to the entity the way Mekanism exposes it: reduced by
     * hazmat and MekaSuit shielding, and skipped outside survival and adventure.
     */
    static void radiate(LivingEntity entity, double dose) {
        Handles h = handles();
        if (h == null || !(dose > 0.0D)) return;
        try {
            h.radiate.invoke(h.manager, entity, dose);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
    }

    /** The entity's accumulated dose in Sv, or a negative number when it has none to read. */
    static double dose(LivingEntity entity) {
        Object cap = capability(entity);
        if (cap == null) return -1.0D;
        try {
            return (double) handles.getRadiation.invoke(cap);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return -1.0D;
        }
    }

    static void setDose(LivingEntity entity, double dose) {
        Object cap = capability(entity);
        if (cap == null) return;
        try {
            handles.set.invoke(cap, Math.max(0.0D, dose));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
        }
    }

    private static Object capability(LivingEntity entity) {
        Handles h = handles();
        return h == null ? null : entity.getCapability(h.entityCapability);
    }

    private static Handles handles() {
        if (resolved) return handles;
        resolved = true;
        if (!ModList.get().isLoaded("mekanism")) return null;
        try {
            Class<?> managerType = Class.forName("mekanism.api.radiation.IRadiationManager");
            Class<?> entityType = Class.forName("mekanism.api.radiation.capability.IRadiationEntity");
            @SuppressWarnings("unchecked")
            EntityCapability<Object, Void> capability =
                    (EntityCapability<Object, Void>) Class.forName("mekanism.common.capabilities.Capabilities")
                            .getField("RADIATION_ENTITY").get(null);
            handles = new Handles(
                    managerType.getField("INSTANCE").get(null),
                    managerType.getMethod("isRadiationEnabled"),
                    managerType.getMethod("radiate", LivingEntity.class, double.class),
                    capability,
                    entityType.getMethod("getRadiation"),
                    entityType.getMethod("set", double.class));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("Mekanism radiation API not found; carried uranium and clean water are off", e);
        }
        return handles;
    }

    private static void fail(Throwable e) {
        LOGGER.warn("Mekanism radiation API call failed; carried uranium and clean water are now off", e);
        handles = null;
    }

    private record Handles(Object manager, Method isRadiationEnabled, Method radiate,
                           EntityCapability<Object, Void> entityCapability, Method getRadiation, Method set) { }
}
