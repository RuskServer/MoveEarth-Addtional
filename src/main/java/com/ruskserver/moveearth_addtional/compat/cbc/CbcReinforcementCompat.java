package com.ruskserver.moveearth_addtional.compat.cbc;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.reinforcement.CbcMunitionDamage;
import com.ruskserver.moveearth_addtional.s2.reinforcement.SiegeDamageService;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import com.ruskserver.moveearth_addtional.warehouse.WarehouseSites;
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
    private static Method terrainDamageHook;
    /** getLevel/getPos of each concrete ProjectileDamageEvent class, looked up once instead of per impact. */
    private static final ClassValue<Method[]> EVENT_ACCESSORS = new ClassValue<>() {
        @Override
        protected Method[] computeValue(Class<?> type) {
            try {
                return new Method[] {type.getMethod("getLevel"), type.getMethod("getPos")};
            } catch (NoSuchMethodException exception) {
                return new Method[0];
            }
        }
    };
    /** Server-thread cache: whether a CBC entity type is a munition rather than a mount or contraption. */
    private static final Map<net.minecraft.world.entity.EntityType<?>, Boolean> MUNITION_TYPES =
            new java.util.IdentityHashMap<>();
    private static final String PROJECTILE_BURST = "rbasamoyai.ritchiesprojectilelib.projectile_burst.ProjectileBurst";

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
            Method[] accessors = EVENT_ACCESSORS.get(event.getClass());
            if (accessors.length != 2) throw new NoSuchMethodException(event.getClass().getName() + ".getLevel/getPos");
            if (!(accessors[0].invoke(event) instanceof ServerLevel level)
                    || !(accessors[1].invoke(event) instanceof BlockPos pos)) return;
            // CBC's direct block-damage event can bypass the generic explosion block list.
            if (WarehouseSites.get(level.getServer()).protects(level.dimension().location(), pos)) {
                if (event instanceof ICancellableEvent cancellable) cancellable.setCanceled(true);
                return;
            }
            // Most impacts land nowhere near a reinforcement, vehicle core or exposed core and follow no
            // recently intercepted munition. Those would be neither intercepted nor recorded below, so the
            // entity search, attribution and area pass are skipped.
            purgeOldImpacts(level.getGameTime());
            if (INTERCEPTED_MUNITIONS.isEmpty()
                    && !recentImpactNear(level, pos, S2TerritoryConfig.cbcProtectedBlastRadius(), level.getGameTime())
                    && !SiegeDamageService.cbcAreaMayBeProtected(level, pos, S2TerritoryConfig.cbcProtectedBlastRadius())) {
                return;
            }
            Entity munition = nearbyMunition(level, pos);
            String entityPath = entityPath(munition);
            CbcMunitionDamage.Kind kind = CbcMunitionDamage.classify(entityPath);
            ServerPlayer attacker = SiegeService.attributablePlayer(munition);
            SiegeService.AttackAttribution attribution =
                    com.ruskserver.moveearth_addtional.s2.dispatch.AttributionSnapshotService
                            .attribution(munition, "cbc_projectile");
            if (attribution == null && attacker != null) {
                attribution = new SiegeService.AttackAttribution(
                        com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(level.getServer())
                                .nationIdFor(attacker.getUUID()).orElse(null), attacker.getUUID(), "cbc_projectile");
            }
            long gameTime = level.getGameTime();
            purgeOldImpacts(gameTime);
            boolean duplicate = munition != null && INTERCEPTED_MUNITIONS.getOrDefault(
                    munition.getUUID(), Long.MIN_VALUE) >= gameTime;
            if (!duplicate && munition == null) {
                duplicate = recentImpactNear(level, pos, S2TerritoryConfig.cbcProtectedBlastRadius(), gameTime);
            }
            int radius = CbcMunitionDamage.usesBlastArea(entityPath)
                    ? S2TerritoryConfig.cbcProtectedBlastRadius() : 0;
            boolean intercepted = duplicate || SiegeDamageService.interceptCbcProtectedArea(
                    attribution, level, pos, kind, radius);
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

    /** CBC calls this before replacing a directly penetrated block with air. Its event has no projectile field. */
    public static boolean canDamageTerrain(Entity projectile, Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            ReinforcementSavedData data = ReinforcementSavedData.get(serverLevel);
            var entry = data.get(pos).orElse(null);
            if (entry != null && entry.enabled() && !serverLevel.getBlockState(pos).isAir()) {
                if (SiegeDamageService.penaltyAt(serverLevel, pos).reinforcementProtectionEnabled()) {
                    var attribution = com.ruskserver.moveearth_addtional.s2.dispatch.AttributionSnapshotService
                            .attribution(projectile, "cbc_projectile");
                    SiegeDamageService.interceptCbcProtectedArea(
                            attribution, serverLevel, pos, kind(projectile), 0);
                    long gameTime = serverLevel.getGameTime();
                    purgeOldImpacts(gameTime);
                    long expires = gameTime + DUPLICATE_IMPACT_TICKS;
                    INTERCEPTED_MUNITIONS.put(projectile.getUUID(), expires);
                    RECENT_PROTECTED_IMPACTS.put(new ProtectedImpact(
                            serverLevel.dimension().location().toString(), pos.immutable()), expires);
                    return false;
                }
                // A disabled reinforcement must not survive an ordinary CBC block break.
                data.remove(pos);
                ReinforcementService.syncChangedNearbyManagers(serverLevel, java.util.Set.of(pos));
            }
        }
        try {
            Method hook = terrainDamageHook;
            if (hook == null) {
                hook = Class.forName("rbasamoyai.createbigcannons.munitions.ProjectileDamageHooks")
                        .getMethod("canDamageTerrain", Level.class, BlockPos.class);
                terrainDamageHook = hook;
            }
            return (Boolean) hook.invoke(null, level, pos);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot invoke CBC terrain damage hook", exception);
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

    /**
     * The closest CBC munition (cannon projectile or fragment burst) around the impact. Cannon mounts,
     * carriages and other CBC contraption entities are never the munition, even when nearer.
     */
    private static Entity nearbyMunition(ServerLevel level, BlockPos pos) {
        return level.getEntities((Entity) null, new AABB(pos).inflate(6.0D), CbcReinforcementCompat::isCbcMunition)
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(pos.getCenter())))
                .orElse(null);
    }

    /**
     * The munition behind a CBC blast built without a source entity (mortar stone explosions): CBC
     * explodes it from {@code onImpact}, while the round is still in the world, so the nearest munition is
     * the one whose attribution snapshot applies.
     */
    public static Entity impactMunition(ServerLevel level, BlockPos pos) {
        return nearbyMunition(level, pos);
    }

    public static boolean isCbcMunition(Entity entity) {
        if (entity == null) return false;
        return MUNITION_TYPES.computeIfAbsent(entity.getType(), ignored -> {
            String path = entityPath(entity);
            if (path.isEmpty()) return false;
            return CbcMunitionSelectionPolicy.isMunition(
                    entity instanceof net.minecraft.world.entity.projectile.Projectile,
                    extendsClass(entity.getClass(), PROJECTILE_BURST),
                    CbcMunitionDamage.classify(path) != CbcMunitionDamage.Kind.UTILITY);
        });
    }

    private static boolean extendsClass(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (name.equals(current.getName())) return true;
        }
        return false;
    }

    public static void clearRuntimeState() {
        INTERCEPTED_MUNITIONS.clear();
        RECENT_PROTECTED_IMPACTS.clear();
        MUNITION_TYPES.clear();
    }

    public static boolean wasRecentlyPreHandled(Entity source, ServerLevel level, BlockPos center) {
        long gameTime = level.getGameTime();
        purgeOldImpacts(gameTime);
        if (source != null && INTERCEPTED_MUNITIONS.getOrDefault(source.getUUID(), Long.MIN_VALUE) >= gameTime) {
            return true;
        }
        return recentImpactNear(level, center, S2TerritoryConfig.cbcProtectedBlastRadius(), gameTime);
    }

    private static boolean recentImpactNear(ServerLevel level, BlockPos pos, int radius, long gameTime) {
        if (RECENT_PROTECTED_IMPACTS.isEmpty()) return false;
        String dimension = level.dimension().location().toString();
        long radiusSquared = (long) radius * radius;
        for (Map.Entry<ProtectedImpact, Long> entry : RECENT_PROTECTED_IMPACTS.entrySet()) {
            if (entry.getValue() >= gameTime && entry.getKey().dimension().equals(dimension)
                    && entry.getKey().pos().distSqr(pos) <= radiusSquared) return true;
        }
        return false;
    }

    private static void purgeOldImpacts(long gameTime) {
        INTERCEPTED_MUNITIONS.entrySet().removeIf(entry -> entry.getValue() < gameTime);
        RECENT_PROTECTED_IMPACTS.entrySet().removeIf(entry -> entry.getValue() < gameTime);
    }

    private record ProtectedImpact(String dimension, BlockPos pos) { }
}
