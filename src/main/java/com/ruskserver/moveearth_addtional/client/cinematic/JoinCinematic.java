package com.ruskserver.moveearth_addtional.client.cinematic;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.ClientDisplayConfig;
import com.ruskserver.moveearth_addtional.config.StartupClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Joining the server, the camera opens high above the player looking straight down and drops in three cuts
 * before easing into the player's eyes. It only moves the camera: the player is
 * where they logged in throughout and can act at once, and any key or click ends it.
 *
 * <p>The server says whether it is safe to show ({@code S2C_JoinCinematicPacket}): not while combat-tagged
 * and not in another nation's land, where a few seconds looking at the sky could cost a fight. With Distant
 * Horizons the camera climbs far higher, since the distant land is there to see; the fog is pushed out to
 * match while it plays, or the ground far below would be lost in it.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, value = Dist.CLIENT)
public final class JoinCinematic {
    /** How long to wait for the server's answer once the world is showing before going without. */
    private static final long PERMIT_WAIT_MILLIS = 2_500L;
    private static final double FLASH_SECONDS = 0.22;

    private enum State { IDLE, WAITING, RUNNING }

    public record CameraPose(Vec3 position, float yaw, float pitch, boolean detached) { }

    private static State state = State.IDLE;
    private static Boolean permitted;
    private static long readySince;
    private static long startMillis;
    private static double openingHeight;
    private static double previousSeconds = -1.0;
    private static double lastCutSeconds = -1.0;
    private static boolean previousSmartCull;

    private JoinCinematic() { }

    /** The server's answer: whether the cinematic may play for this login. */
    public static void permit(boolean allowed) {
        permitted = allowed;
    }

    public static boolean running() {
        return state == State.RUNNING;
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        state = State.WAITING;
        permitted = null;
        readySince = 0L;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
        state = State.IDLE;
        permitted = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (state == State.RUNNING) {
            if (minecraft.player == null || JoinCinematicPath.finished(seconds())) stop();
            return;
        }
        if (state != State.WAITING || minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            return;
        }
        long now = Util.getMillis();
        if (readySince == 0L) readySince = now;
        if (permitted == null) {
            if (now - readySince > PERMIT_WAIT_MILLIS) state = State.IDLE;
            return;
        }
        if (!permitted || !ClientDisplayConfig.JOIN_CINEMATIC.get() || StartupClientConfig.reducedMotion()
                || minecraft.player.isSpectator() || moving(minecraft)) {
            state = State.IDLE;
            return;
        }
        start(minecraft);
    }

    private static boolean moving(Minecraft minecraft) {
        var options = minecraft.options;
        return options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown()
                || options.keyRight.isDown() || options.keyJump.isDown();
    }

    private static void start(Minecraft minecraft) {
        state = State.RUNNING;
        startMillis = Util.getMillis();
        previousSeconds = -1.0;
        lastCutSeconds = -1.0;
        openingHeight = JoinCinematicPath.openingHeight(minecraft.options.getEffectiveRenderDistance() * 16,
                distantHorizonsBlocks());
        // Seen from above, the cave-culling graph built for the player's own position hides whole sections.
        previousSmartCull = minecraft.smartCull;
        minecraft.smartCull = false;
        minecraft.levelRenderer.needsUpdate();
    }

    private static void stop() {
        if (state != State.RUNNING) return;
        state = State.IDLE;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.smartCull = previousSmartCull;
        minecraft.levelRenderer.needsUpdate();
    }

    private static double seconds() {
        return (Util.getMillis() - startMillis) / 1000.0;
    }

    /** The camera this frame, or null when the cinematic is not playing. Called from the camera's setup. */
    public static CameraPose pose(Entity entity, float partialTick) {
        if (state != State.RUNNING || entity == null) return null;
        double now = seconds();
        JoinCinematicPath.Frame frame = JoinCinematicPath.at(now, openingHeight, previousSeconds);
        previousSeconds = now;
        if (frame.shotStart()) {
            lastCutSeconds = now;
            if (frame.shot() < 3) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.25F - frame.shot() * 0.15F, 0.35F));
            }
        }
        Vec3 eye = entity.getEyePosition(partialTick);
        Vec3 above = eye.add(0.0, frame.height(), 0.0);
        double landing = frame.landing();
        Vec3 position = above.lerp(eye, landing);
        float playerYaw = entity.getViewYRot(partialTick);
        float playerPitch = entity.getViewXRot(partialTick);
        float yaw = (float) (playerYaw + frame.yawOffset() * (1.0 - landing));
        float pitch = (float) (90.0 + (playerPitch - 90.0) * landing);
        // The player's own body shows from above, and is left out once the camera is all but in their head.
        return new CameraPose(position, yaw, pitch, landing < 0.9);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (state == State.RUNNING && event.getAction() == GLFW.GLFW_PRESS) stop();
    }

    @SubscribeEvent
    public static void onMouse(InputEvent.MouseButton.Pre event) {
        if (state == State.RUNNING && event.getAction() == GLFW.GLFW_PRESS) stop();
    }

    /** Behind the hand-pass mixin, for anything that draws a held item through NeoForge's hand event. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void onHand(net.neoforged.neoforge.client.event.RenderHandEvent event) {
        if (state == State.RUNNING) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (state == State.RUNNING) event.setCanceled(true);
    }

    /** A short white flash on each cut, as the camera switches. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (state != State.RUNNING || lastCutSeconds < 0.0) return;
        double since = seconds() - lastCutSeconds;
        if (since >= FLASH_SECONDS) return;
        int alpha = (int) (110 * (1.0 - since / FLASH_SECONDS));
        var graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | 0xFFFFFF);
    }

    /** Pushes the fog out past the ground below while the camera is high. */
    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (state != State.RUNNING) return;
        float far = (float) Math.max(event.getFarPlaneDistance(), openingHeight * 1.6 + 64.0);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(far * 0.7F);
        event.setCanceled(true);
    }

    /**
     * Distant Horizons' LOD distance in blocks, read through its API by reflection since it is optional;
     * 0 without it. When the API cannot be read, its default of 128 chunks is assumed.
     */
    private static int distantHorizonsBlocks() {
        if (!ModList.get().isLoaded("distanthorizons")) return 0;
        try {
            Class<?> delayed = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs = delayed.getField("configs").get(null);
            if (configs == null) return 128 * 16;
            Object graphics = configs.getClass().getMethod("graphics").invoke(configs);
            Object distance = graphics.getClass().getMethod("chunkRenderDistance").invoke(graphics);
            Object value = distance.getClass().getMethod("getValue").invoke(distance);
            return value instanceof Integer chunks ? Math.max(0, chunks) * 16 : 128 * 16;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unreadable) {
            return 128 * 16;
        }
    }
}
