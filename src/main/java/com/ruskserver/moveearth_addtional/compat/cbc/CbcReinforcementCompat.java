package com.ruskserver.moveearth_addtional.compat.cbc;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.reinforcement.CbcMunitionDamage;
import com.ruskserver.moveearth_addtional.s2.reinforcement.SiegeDamageService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.server.level.ServerPlayer;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Runtime-only bridge to CBC 5.11.6+'s ProjectileDamageEvent. */
public final class CbcReinforcementCompat {
    private static final String EVENT_CLASS = "rbasamoyai.createbigcannons.events.ProjectileDamageEvent";
    private static final long DUPLICATE_IMPACT_TICKS = 3L;
    private static final Map<UUID, Long> INTERCEPTED_MUNITIONS = new HashMap<>();
    private static final Map<ProtectedImpact, Long> RECENT_PROTECTED_IMPACTS = new HashMap<>();

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
            Entity munition = nearbyMunition(level, pos);
            String entityPath = entityPath(munition);
            CbcMunitionDamage.Kind kind = CbcMunitionDamage.classify(entityPath);
            ServerPlayer attacker = SiegeService.attributablePlayer(munition);
            long gameTime = level.getGameTime();
            purgeOldImpacts(gameTime);
            boolean duplicate = munition != null && INTERCEPTED_MUNITIONS.getOrDefault(
                    munition.getUUID(), Long.MIN_VALUE) >= gameTime;
            if (!duplicate && munition == null) {
                int fallbackRadius = S2TerritoryConfig.cbcProtectedBlastRadius();
                duplicate = RECENT_PROTECTED_IMPACTS.entrySet().stream()
                        .anyMatch(entry -> entry.getValue() >= gameTime
                                && entry.getKey().dimension().equals(level.dimension().location().toString())
                                && entry.getKey().pos().distSqr(pos) <= (long) fallbackRadius * fallbackRadius);
            }
            int radius = CbcMunitionDamage.usesBlastArea(entityPath)
                    ? S2TerritoryConfig.cbcProtectedBlastRadius() : 0;
            boolean intercepted = duplicate || SiegeDamageService.interceptCbcProtectedArea(
                    attacker, level, pos, kind, radius);
            if (intercepted && !duplicate) {
                long expires = gameTime + DUPLICATE_IMPACT_TICKS;
                if (munition != null) INTERCEPTED_MUNITIONS.put(munition.getUUID(), expires);
                RECENT_PROTECTED_IMPACTS.put(new ProtectedImpact(
                        level.dimension().location().toString(), pos.immutable()), expires);
            }
            if (intercepted && event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
        } catch (ReflectiveOperationException exception) {
            Moveearth_addtional.LOGGER.debug("Failed to read CBC projectile damage event", exception);
        }
    }

    public static CbcMunitionDamage.Kind kind(Entity source) {
        return CbcMunitionDamage.classify(entityPath(source));
    }

    private static String entityPath(Entity source) {
        if (source == null) return "";
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
        return id != null && "createbigcannons".equals(id.getNamespace()) ? id.getPath() : "";
    }

    public static boolean isCbc(Entity source) {
        if (source == null) return false;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(source.getType());
        return id != null && "createbigcannons".equals(id.getNamespace());
    }

    /** CBC custom explosions such as MortarStoneExplosion deliberately have no direct source entity. */
    public static boolean isCbcExplosion(Explosion explosion) {
        return explosion != null && explosion.getClass().getName()
                .startsWith("rbasamoyai.createbigcannons.");
    }

    public static CbcMunitionDamage.Kind kind(Explosion explosion) {
        return explosion == null ? CbcMunitionDamage.Kind.UTILITY
                : CbcMunitionDamage.classifyExplosionClass(explosion.getClass().getSimpleName());
    }

    private static Entity nearbyMunition(ServerLevel level, BlockPos pos) {
        return level.getEntities((Entity) null, new AABB(pos).inflate(6.0D), CbcReinforcementCompat::isCbc)
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(pos.getCenter())))
                .orElse(null);
    }

    public static void clearRuntimeState() {
        INTERCEPTED_MUNITIONS.clear();
        RECENT_PROTECTED_IMPACTS.clear();
    }

    public static boolean wasRecentlyPreHandled(Entity source, ServerLevel level, BlockPos center) {
        long gameTime = level.getGameTime();
        purgeOldImpacts(gameTime);
        if (source != null && INTERCEPTED_MUNITIONS.getOrDefault(source.getUUID(), Long.MIN_VALUE) >= gameTime) {
            return true;
        }
        int radius = S2TerritoryConfig.cbcProtectedBlastRadius();
        return RECENT_PROTECTED_IMPACTS.entrySet().stream()
                .anyMatch(entry -> entry.getValue() >= gameTime
                        && entry.getKey().dimension().equals(level.dimension().location().toString())
                        && entry.getKey().pos().distSqr(center) <= (long) radius * radius);
    }

    private static void purgeOldImpacts(long gameTime) {
        INTERCEPTED_MUNITIONS.entrySet().removeIf(entry -> entry.getValue() < gameTime);
        RECENT_PROTECTED_IMPACTS.entrySet().removeIf(entry -> entry.getValue() < gameTime);
    }

    private record ProtectedImpact(String dimension, BlockPos pos) { }
}
