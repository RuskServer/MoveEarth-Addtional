package com.ruskserver.moveearth_addtional.compat.cbc;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.s2.combat.RealPlayers;
import com.ruskserver.moveearth_addtional.s2.dispatch.AttributionSnapshotService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.UUID;

/**
 * Attributes Create Big Cannons shots, which CBC fires without any owner.
 *
 * <p>Mixins wrap the CBC methods that spawn munitions: {@code fireShot} of mounted big cannons and
 * autocannons (and of the CBC Modern Warfare cannons built on the same contraption base) and the delayed
 * drop-mortar launch open a <em>firing</em> frame for the cannon contraption,
 * and the detonations that release fragment bursts open a <em>detonation</em> frame for the parent
 * munition. A CBC munition entering the world inside a frame is stamped by
 * {@link AttributionSnapshotService} before anything can read it: inside a firing frame it receives the
 * {@link CbcShotAttributionPolicy} decision, inside a detonation frame it inherits its parent's snapshot.
 *
 * <p>Responsibility follows the policy: the controlling real player, else the real player who placed the
 * mount, else the nation of the vehicle or territory the mount is on. A placer answers for every shot of
 * an automatic (redstone-fired) cannon even while offline: whoever installs a gun answers for it. When the
 * chosen player is online the munition's vanilla owner is set as well, so everything reading
 * {@link Projectile#getOwner()} agrees with the snapshot.
 *
 * <p>The placer is kept in the mount block entity's persistent data, which NeoForge saves with the block
 * entity, so it survives restarts and Sable or Create moving the mount. It is also part of the block
 * entity's client sync data, as every NeoForge persistent-data entry is.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class CbcShotAttribution {
    static final String PLACER = "moveearth_cbc_mount_placer";
    /** The placer's nation at placement; absent (not just empty) on mounts placed before it was recorded. */
    static final String PLACER_NATION = "moveearth_cbc_mount_placer_nation";
    static final String PLACER_NATIONLESS = "moveearth_cbc_mount_placer_nationless";
    private static final String MOUNT_INTERFACE = "rbasamoyai.createbigcannons.cannon_control.ControlPitchContraption$Block";

    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    /** CBC's {@code PitchOrientedContraptionEntity#getController}, looked up once per class. */
    private static final ClassValue<Method> CONTROLLER = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("getController");
            } catch (NoSuchMethodException exception) {
                return null;
            }
        }
    };
    private static final ClassValue<Boolean> MOUNT_BLOCK_ENTITY = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return implementsInterface(type, MOUNT_INTERFACE);
        }
    };

    private CbcShotAttribution() { }

    /** Opens a firing frame; every call must be paired with {@link #end()}. */
    public static void beginFiring(Entity cannon) {
        FRAMES.get().push(new Firing(cannon));
    }

    /** Opens a detonation frame for a munition releasing sub-munitions; pair with {@link #end()}. */
    public static void beginDetonation(Entity munition) {
        FRAMES.get().push(new Detonation(munition));
    }

    public static void end() {
        ArrayDeque<Frame> frames = FRAMES.get();
        if (!frames.isEmpty()) frames.pop();
    }

    /**
     * Stamps a CBC munition joining the world inside a frame. Returns true when the entity was spawned by
     * a framed CBC call, attributed or not, so that no other rule re-attributes it.
     */
    public static boolean attach(ServerLevel level, Entity entity) {
        ArrayDeque<Frame> frames = FRAMES.get();
        // Rounds of CBC add-ons (CBC Modern Warfare ammunition fired from CBC cannons) are Projectiles of
        // another namespace; the casing an autocannon ejects is an ItemEntity and stays unattributed.
        if (frames.isEmpty() || !(entity instanceof Projectile || CbcReinforcementCompat.isCbcMunition(entity))) {
            return false;
        }
        Frame frame = frames.peek();
        if (frame instanceof Detonation detonation) {
            AttributionSnapshotService.copy(detonation.parent(), entity);
            if (detonation.parent() instanceof Projectile parent && entity instanceof Projectile child
                    && parent.getOwner() != null) {
                child.setOwner(parent.getOwner());
            }
            return true;
        }
        Firing firing = (Firing) frame;
        CbcShotAttributionPolicy.Decision decision = firing.decision(level);
        MinecraftServer server = level.getServer();
        switch (decision.basis()) {
            case PASSENGER, PLACER -> {
                AttributionSnapshotService.write(entity, AttributionSnapshotService.capture(server, decision.actorId()));
                ServerPlayer online = RealPlayers.real(server.getPlayerList().getPlayer(decision.actorId()));
                if (online != null && entity instanceof Projectile projectile) projectile.setOwner(online);
            }
            case VEHICLE_NATION, TERRITORY_NATION -> AttributionSnapshotService.write(entity,
                    new AttributionSnapshotService.Snapshot(null, decision.nationId(), null, null));
            case NONE -> { }
        }
        return true;
    }

    /** Records the real player who places a CBC cannon mount (rotating or fixed). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        ServerPlayer placer = RealPlayers.real(event.getEntity());
        if (placer == null) return;
        BlockEntity blockEntity = level.getBlockEntity(event.getPos());
        if (blockEntity == null || !MOUNT_BLOCK_ENTITY.get(blockEntity.getClass())) return;
        blockEntity.getPersistentData().putUUID(PLACER, placer.getUUID());
        UUID nation = NationSavedData.get(level.getServer()).nationIdFor(placer.getUUID()).orElse(null);
        if (nation != null) blockEntity.getPersistentData().putUUID(PLACER_NATION, nation);
        else blockEntity.getPersistentData().putBoolean(PLACER_NATIONLESS, true);
        blockEntity.setChanged();
    }

    /** A frame left open by an exception must not attribute a later, unrelated munition. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        FRAMES.get().clear();
    }

    private static CbcShotAttributionPolicy.Decision resolve(ServerLevel level, Entity cannon) {
        if (cannon == null) return CbcShotAttributionPolicy.Decision.NONE;
        Object controller = controller(cannon);
        BlockEntity mount = controller instanceof BlockEntity blockEntity ? blockEntity : null;
        BlockPos mountPos = mount != null ? mount.getBlockPos()
                : controller instanceof Entity carriage ? carriage.blockPosition() : cannon.blockPosition();
        UUID placer = mount != null && mount.getPersistentData().hasUUID(PLACER)
                ? mount.getPersistentData().getUUID(PLACER) : null;
        if (placer != null && !placerAnswers(level, mount, placer, mountPos)) placer = null;
        return CbcShotAttributionPolicy.decide(realPassenger(cannon), placer,
                () -> vehicleNation(level, mountPos), () -> territoryNation(level, mountPos));
    }

    private static boolean placerAnswers(ServerLevel level, BlockEntity mount, UUID placer, BlockPos mountPos) {
        var data = mount.getPersistentData();
        boolean recorded = data.hasUUID(PLACER_NATION) || data.getBoolean(PLACER_NATIONLESS);
        UUID atPlacement = data.hasUUID(PLACER_NATION) ? data.getUUID(PLACER_NATION) : null;
        NationSavedData nations = NationSavedData.get(level.getServer());
        UUID now = nations.nationIdFor(placer).orElse(null);
        UUID owner = vehicleNation(level, mountPos);
        if (owner == null) owner = territoryNation(level, mountPos);
        boolean allied = owner != null && now != null && nations.isAllied(now, owner);
        return CbcShotAttributionPolicy.placerAnswers(recorded, atPlacement, now, owner, allied);
    }

    /** The cannon's own controlling passenger, or the rider of the carriage carrying it. */
    private static UUID realPassenger(Entity cannon) {
        ServerPlayer player = RealPlayers.real(cannon.getControllingPassenger());
        if (player == null && cannon.getVehicle() != null) {
            player = RealPlayers.real(cannon.getVehicle().getControllingPassenger());
        }
        return player == null ? null : player.getUUID();
    }

    private static Object controller(Entity cannon) {
        Method method = CONTROLLER.get(cannon.getClass());
        if (method == null) return null;
        try {
            return method.invoke(cannon);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Moveearth_addtional.LOGGER.debug("Cannot read the controller of CBC cannon {}", cannon, exception);
            return null;
        }
    }

    private static UUID vehicleNation(ServerLevel level, BlockPos mountPos) {
        try {
            return SableVehicleTopology.at(level, mountPos).map(vehicle -> vehicle.vehicle().nationId()).orElse(null);
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    private static UUID territoryNation(ServerLevel level, BlockPos mountPos) {
        BlockPos world = SableVehicleTopology.placement(level, mountPos).worldPos();
        return TerritorySavedData.get(level.getServer())
                .controllingNation(level.getServer(), level.dimension().location(), world).orElse(null);
    }

    private static boolean implementsInterface(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Class<?> implemented : current.getInterfaces()) {
                if (name.equals(implemented.getName()) || implementsInterface(implemented, name)) return true;
            }
        }
        return false;
    }

    private sealed interface Frame permits Firing, Detonation { }

    private record Detonation(Entity parent) implements Frame { }

    /** One cannon discharge; the decision is made once, when its first munition appears. */
    private static final class Firing implements Frame {
        private final Entity cannon;
        private CbcShotAttributionPolicy.Decision decision;

        private Firing(Entity cannon) {
            this.cannon = cannon;
        }

        private CbcShotAttributionPolicy.Decision decision(ServerLevel level) {
            if (decision == null) decision = resolve(level, cannon);
            return decision;
        }
    }
}
