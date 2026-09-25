package com.ruskserver.moveearth_addtional.advancement;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Optional Mekanism progress checks. No Mekanism class is loaded when the mod is absent. */
final class MekanismAdvancementBridge {
    private static final Set<String> REPORTED_ACCESS_FAILURES = ConcurrentHashMap.newKeySet();

    private MekanismAdvancementBridge() { }

    static void inspect(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (id == null) return;
        BlockEntity tile = level.getBlockEntity(pos);
        if (tile == null) return;

        if (id.toString().equals("mekanism:metallurgic_infuser")) {
            if (Boolean.TRUE.equals(invoke(tile, "getActive"))) {
                ModCriteria.trigger(player, ModCriteria.MEKANISM_MACHINE_OPERATED);
            }
        } else if (id.toString().equals("mekanism:chemical_infuser")) {
            Object tank = field(tile, "centerTank");
            Object stack = invoke(tank, "getStack");
            Object holder = invoke(stack, "getChemicalHolder");
            if (number(invoke(tile, "getEnergyUsed")) > 0
                    && number(invoke(stack, "getAmount")) > 0
                    && "mekanism:fissile_fuel".equals(invoke(holder, "getRegisteredName"))) {
                ModCriteria.trigger(player, ModCriteria.FISSILE_FUEL_PRODUCED);
            }
        } else if (id.getNamespace().equals("mekanismgenerators")
                && (id.getPath().startsWith("fission_reactor_")
                || id.getPath().startsWith("turbine_"))) {
            Object multiblock = invoke(tile, "getMultiblock");
            if (multiblock == null) return;
            if (id.getPath().startsWith("fission_reactor_")
                    && Boolean.TRUE.equals(invoke(multiblock, "isBurning"))) {
                Object damage = invoke(multiblock, "getDamagePercent");
                if (damage instanceof Number damaged && damaged.doubleValue() < 50.0D) {
                    ModCriteria.trigger(player, ModCriteria.FISSION_REACTOR_OPERATED);
                }
            } else if (id.getPath().startsWith("turbine_")
                    && number(invoke(multiblock, "getProductionRate")) > 0) {
                ModCriteria.trigger(player, ModCriteria.TURBINE_OPERATED);
            }
        }
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static Object invoke(Object target, String method) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            report(target, method, failure);
            return null;
        }
    }

    private static Object field(Object target, String name) {
        try {
            return target.getClass().getField(name).get(target);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            report(target, name, failure);
            return null;
        }
    }

    private static void report(Object target, String member, Exception failure) {
        String key = target.getClass().getName() + "#" + member;
        if (REPORTED_ACCESS_FAILURES.add(key)) {
            Moveearth_addtional.LOGGER.warn("Optional Mekanism advancement check unavailable for {}: {}",
                    key, failure.toString());
        }
    }
}
