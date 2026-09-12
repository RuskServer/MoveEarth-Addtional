package com.ruskserver.moveearth_addtional.compat.cbc;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.reinforcement.CbcMunitionDamage;
import com.ruskserver.moveearth_addtional.s2.reinforcement.SiegeDamageService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.function.Consumer;

/** Runtime-only bridge to CBC 5.11.6+'s ProjectileDamageEvent. */
public final class CbcReinforcementCompat {
    private static final String EVENT_CLASS = "rbasamoyai.createbigcannons.events.ProjectileDamageEvent";

    private CbcReinforcementCompat() { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerIfPresent() {
        if (!ModList.get().isLoaded("createbigcannons")) return;
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(EVENT_CLASS);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, (Class) eventClass,
                    (Consumer<Event>) CbcReinforcementCompat::onProjectileDamage);
            Moveearth_addtional.LOGGER.info("Enabled Create Big Cannons reinforcement damage bridge");
        } catch (ReflectiveOperationException | LinkageError exception) {
            Moveearth_addtional.LOGGER.warn("Create Big Cannons is present but its projectile damage hook is unavailable", exception);
        }
    }

    private static void onProjectileDamage(Event event) {
        try {
            Method getLevel = event.getClass().getMethod("getLevel");
            Method getPos = event.getClass().getMethod("getPos");
            if (!(getLevel.invoke(event) instanceof ServerLevel level)
                    || !(getPos.invoke(event) instanceof BlockPos pos)) return;
            CbcMunitionDamage.Kind kind = nearbyMunition(level, pos);
            if (SiegeDamageService.interceptCbcImpact(level, pos, kind)
                    && event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
        } catch (ReflectiveOperationException exception) {
            Moveearth_addtional.LOGGER.debug("Failed to read CBC projectile damage event", exception);
        }
    }

    public static CbcMunitionDamage.Kind kind(Entity source) {
        if (source == null) return CbcMunitionDamage.Kind.UTILITY;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
        return id != null && "createbigcannons".equals(id.getNamespace())
                ? CbcMunitionDamage.classify(id.getPath()) : CbcMunitionDamage.Kind.UTILITY;
    }

    public static boolean isCbc(Entity source) {
        if (source == null) return false;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
        return id != null && "createbigcannons".equals(id.getNamespace());
    }

    private static CbcMunitionDamage.Kind nearbyMunition(ServerLevel level, BlockPos pos) {
        return level.getEntities((Entity) null, new AABB(pos).inflate(6.0D), CbcReinforcementCompat::isCbc)
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(pos.getCenter())))
                .map(CbcReinforcementCompat::kind).orElse(CbcMunitionDamage.Kind.UTILITY);
    }
}
