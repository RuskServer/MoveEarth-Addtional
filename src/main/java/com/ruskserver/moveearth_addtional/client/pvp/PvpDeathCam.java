package com.ruskserver.moveearth_addtional.client.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.s2c.pvp.S2C_PvpKillcamPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The death camera, done the classic way: the view stays where the player fell and turns to follow their
 * killer as they move now, closing in a little on a distant one. The killer already glows, so they show through
 * walls. When the killer cannot be seen, such as once they are on their own deploy screen, the camera holds on
 * where they were last.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class PvpDeathCam {
    /** How quickly the view catches up with the killer; higher is snappier. */
    private static final double FOLLOW_RATE = 6.0;
    /** The most the view narrows, for a killer far away. */
    private static final double MAX_ZOOM = 0.35;
    private static final double FULL_ZOOM_DISTANCE = 48.0;

    private static boolean tracking;
    private static float yaw;
    private static float pitch;
    private static long lastNanos;
    private static Vec3 lastTarget;

    private PvpDeathCam() {
    }

    /** Called when a death camera begins: the view eases from wherever the player was looking. */
    static void start() {
        tracking = false;
        lastTarget = null;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        S2C_PvpKillcamPacket killcam = PvpClientState.killcam();
        // With nobody to blame there is nothing to follow; the view stays where it was.
        if (killcam == null || !killcam.hasKiller()) return;
        Vec3 eye = event.getCamera().getPosition();
        Vec3 target = target(killcam, (float) event.getPartialTick());
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float wantYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float wantPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);

        long now = System.nanoTime();
        if (!tracking) {
            yaw = event.getYaw();
            pitch = event.getPitch();
            lastNanos = now;
            tracking = true;
        }
        double seconds = Mth.clamp((now - lastNanos) / 1.0E9, 0.0, 0.1);
        lastNanos = now;
        float follow = (float) (1.0 - Math.exp(-seconds * FOLLOW_RATE));
        yaw += Mth.wrapDegrees(wantYaw - yaw) * follow;
        pitch += (wantPitch - pitch) * follow;
        event.setYaw(yaw);
        event.setPitch(Mth.clamp(pitch, -90.0F, 90.0F));
        event.setRoll(0.0F);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        S2C_PvpKillcamPacket killcam = PvpClientState.killcam();
        if (killcam == null || !killcam.hasKiller()) return;
        Vec3 eye = event.getCamera().getPosition();
        double distance = target(killcam, (float) event.getPartialTick()).distanceTo(eye);
        float progress = PvpClientState.killcamProgress((float) event.getPartialTick());
        double ease = 1.0 - Math.pow(1.0 - Math.min(1.0, progress / 0.35), 3);
        double zoom = 1.0 - MAX_ZOOM * ease * Mth.clamp(distance / FULL_ZOOM_DISTANCE, 0.0, 1.0);
        event.setFOV(event.getFOV() * zoom);
    }

    /** The killer's eyes now, or where they were last seen, or where they stood when the shot landed. */
    private static Vec3 target(S2C_PvpKillcamPacket killcam, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (killcam.hasKiller() && minecraft.level != null) {
            Player killer = minecraft.level.getPlayerByUUID(killcam.killerId());
            if (killer != null && !killer.isSpectator() && killer.isAlive()) {
                lastTarget = killer.getEyePosition(partialTick);
                return lastTarget;
            }
        }
        return lastTarget != null ? lastTarget : new Vec3(killcam.x(), killcam.y(), killcam.z());
    }
}
