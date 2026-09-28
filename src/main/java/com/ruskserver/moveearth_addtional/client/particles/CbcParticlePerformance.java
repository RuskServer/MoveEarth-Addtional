package com.ruskserver.moveearth_addtional.client.particles;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.ruskserver.moveearth_addtional.mixin.client.ParticlePhysicsAccess;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class CbcParticlePerformance {
    private static CbcSmokeBudget budget = new CbcSmokeBudget();
    private static CbcSmokeBudget explosionBudget = new CbcSmokeBudget();
    private static CbcParticleMetrics metrics = new CbcParticleMetrics();
    private static final Map<Particle, String> origins = new WeakHashMap<>();
    private static String emissionSource;

    private CbcParticlePerformance() { }

    public static void reset() {
        budget = new CbcSmokeBudget();
        explosionBudget = new CbcSmokeBudget();
        metrics = new CbcParticleMetrics();
        origins.clear();
        emissionSource = null;
    }

    public static boolean discard(Particle particle, double x, double y, double z) {
        if (particle == null || (!CbcParticleConfig.PROFILE.get() && !CbcParticleConfig.LIMIT.get())) return false;
        String type = particle.getClass().getName();
        boolean cbc = type.startsWith("rbasamoyai.createbigcannons.");
        if (!cbc && emissionSource == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        boolean dropped = false;
        boolean explosion = CbcSmokeBudget.explosionSmoke(type);
        if (CbcParticleConfig.LIMIT.get() && (CbcSmokeBudget.decorativeSmoke(type) || explosion) && minecraft.level != null) {
            var camera = minecraft.gameRenderer.getMainCamera().getPosition();
            double distance = CbcParticleConfig.NEAR_DISTANCE.get();
            boolean near = camera.distanceToSqr(x, y, z) <= distance * distance;
            CbcSmokeBudget selectedBudget = explosion ? explosionBudget : budget;
            dropped = !selectedBudget.accept(minecraft.level.getGameTime(), near,
                    explosion ? CbcParticleConfig.EXPLOSION_NEAR_BUDGET.get() : CbcParticleConfig.NEAR_BUDGET.get(),
                    explosion ? CbcParticleConfig.EXPLOSION_FAR_BUDGET.get() : CbcParticleConfig.FAR_BUDGET.get());
        }
        if (CbcParticleConfig.PROFILE.get()) {
            String label = cbc ? type : emissionSource + " -> " + type.substring(type.lastIndexOf('.') + 1);
            metrics.created(label, dropped);
            if (!dropped) origins.put(particle, label);
        }
        return dropped;
    }

    public static void tick(Particle particle, Runnable update) {
        boolean collisionLod = collisionLod(particle);
        if (!CbcParticleConfig.PROFILE.get()) {
            update(particle, update, collisionLod);
            return;
        }
        String type = particle.getClass().getName();
        if (!type.startsWith("rbasamoyai.createbigcannons.")) {
            type = origins.get(particle);
            if (type == null) {
                update.run();
                return;
            }
        }
        String previousSource = emissionSource;
        emissionSource = type;
        long start = System.nanoTime();
        try {
            update(particle, update, collisionLod);
        } finally {
            long elapsed = System.nanoTime() - start;
            emissionSource = previousSource;
            metrics.updated(type, elapsed, collisionLod);
        }
    }

    private static boolean collisionLod(Particle particle) {
        if (!CbcParticleConfig.COLLISION_LOD.get()) return false;
        String type = particle.getClass().getName();
        if (!CbcSmokeBudget.explosionSmoke(type)
                && !type.equals("rbasamoyai.createbigcannons.effects.particles.smoke.TrailSmokeParticle")) return false;
        ParticlePhysicsAccess physics = (ParticlePhysicsAccess) particle;
        if (!physics.moveearth$hasPhysics()) return false;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return CbcSmokeBudget.skipCollision(type, physics.moveearth$age(),
                camera.distanceToSqr(physics.moveearth$positionX(), physics.moveearth$positionY(), physics.moveearth$positionZ()),
                CbcParticleConfig.COLLISION_DISTANCE.get());
    }

    private static void update(Particle particle, Runnable update, boolean collisionLod) {
        if (!collisionLod) {
            update.run();
            return;
        }
        ParticlePhysicsAccess physics = (ParticlePhysicsAccess) particle;
        boolean original = physics.moveearth$hasPhysics();
        physics.moveearth$setPhysics(false);
        try {
            update.run();
        } finally {
            physics.moveearth$setPhysics(original);
        }
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("moveearthparticles")
                .then(Commands.literal("profile").then(Commands.literal("on").executes(context -> {
                    metrics = new CbcParticleMetrics();
                    origins.clear();
                    CbcParticleConfig.PROFILE.set(true);
                    CbcParticleConfig.SPEC.save();
                    context.getSource().sendSuccess(() -> MoveEarthMessage.info(Component.translatable(
                            "particles.moveearth_addtional.profile", "ON")), false);
                    return 1;
                })).then(Commands.literal("off").executes(context -> {
                    CbcParticleConfig.PROFILE.set(false);
                    CbcParticleConfig.SPEC.save();
                    context.getSource().sendSuccess(() -> MoveEarthMessage.info(Component.translatable(
                            "particles.moveearth_addtional.profile", "OFF")), false);
                    return 1;
                })))
                .then(Commands.literal("limit").then(Commands.literal("on").executes(context -> {
                    budget = new CbcSmokeBudget();
                    explosionBudget = new CbcSmokeBudget();
                    CbcParticleConfig.LIMIT.set(true);
                    CbcParticleConfig.SPEC.save();
                    context.getSource().sendSuccess(() -> MoveEarthMessage.warning(Component.translatable(
                            "particles.moveearth_addtional.limit", "ON")), false);
                    return 1;
                })).then(Commands.literal("off").executes(context -> {
                    CbcParticleConfig.LIMIT.set(false);
                    CbcParticleConfig.SPEC.save();
                    context.getSource().sendSuccess(() -> MoveEarthMessage.info(Component.translatable(
                            "particles.moveearth_addtional.limit", "OFF")), false);
                    return 1;
                })))
                .then(Commands.literal("collision").then(Commands.literal("on").executes(context -> {
                    CbcParticleConfig.COLLISION_LOD.set(true);
                    CbcParticleConfig.SPEC.save();
                    context.getSource().sendSuccess(() -> MoveEarthMessage.warning(Component.translatable(
                            "particles.moveearth_addtional.collision", "ON")), false);
                    return 1;
                })).then(Commands.literal("off").executes(context -> {
                    CbcParticleConfig.COLLISION_LOD.set(false);
                    CbcParticleConfig.SPEC.save();
                    context.getSource().sendSuccess(() -> MoveEarthMessage.info(Component.translatable(
                            "particles.moveearth_addtional.collision", "OFF")), false);
                    return 1;
                })))
                .then(Commands.literal("status").executes(context -> {
                    context.getSource().sendSuccess(() -> MoveEarthMessage.info(Component.translatable(
                            "particles.moveearth_addtional.status", CbcParticleConfig.PROFILE.get(), CbcParticleConfig.LIMIT.get(),
                            CbcParticleConfig.COLLISION_LOD.get())), false);
                    List<CbcParticleMetrics.Summary> lines = metrics.summary();
                    if (lines.isEmpty()) context.getSource().sendSuccess(() -> MoveEarthMessage.tip(Component.translatable(
                            "particles.moveearth_addtional.empty")), false);
                    for (CbcParticleMetrics.Summary line : lines) context.getSource().sendSuccess(() -> MoveEarthMessage.info(
                            Component.translatable("particles.moveearth_addtional.metric", line.type(), line.created(),
                                    line.dropped(), line.updates(), String.format(Locale.ROOT, "%.3f", line.tickNanos() / 1_000_000.0),
                                    line.collisionLodUpdates())), false);
                    return 1;
                })));
    }
}
