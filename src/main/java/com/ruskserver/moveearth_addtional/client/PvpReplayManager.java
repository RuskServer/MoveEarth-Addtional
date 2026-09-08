package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.S2C_KillcamReplayPacket;
import com.ruskserver.moveearth_addtional.pvp.PvpReplayFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class PvpReplayManager {
    public static final PvpReplayManager INSTANCE = new PvpReplayManager();

    private boolean active;
    private S2C_KillcamReplayPacket packet;
    private float currentFrame;
    private int totalFrames;
    private boolean slowMotion;
    private net.minecraft.world.entity.Entity dummyCamera;

    private PvpReplayManager() {}

    public boolean isActive() {
        return active;
    }

    public S2C_KillcamReplayPacket packet() {
        return packet;
    }

    public float progress() {
        if (totalFrames <= 0) return 1.0F;
        return Mth.clamp(currentFrame / totalFrames, 0.0F, 1.0F);
    }

    public void startReplay(S2C_KillcamReplayPacket replayPacket) {
        this.packet = replayPacket;
        this.active = true;
        this.currentFrame = 0.0F;
        this.totalFrames = replayPacket.killerFrames().size();
        this.slowMotion = false;
        
        Minecraft mc = Minecraft.getInstance();
        if (totalFrames > 0 && mc.level != null) {
            dummyCamera = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, 0, 0, 0);
            dummyCamera.setInvisible(true);
            dummyCamera.setNoGravity(true);
            mc.setCameraEntity(dummyCamera);
        } else {
            this.active = false;
        }
    }

    public void stopReplay() {
        this.active = false;
        this.packet = null;
        this.currentFrame = 0.0F;
        this.totalFrames = 0;
        this.slowMotion = false;
        
        Minecraft mc = Minecraft.getInstance();
        if (dummyCamera != null && mc.getCameraEntity() == dummyCamera) {
            mc.setCameraEntity(mc.player);
        }
        dummyCamera = null;
    }

    public void tick() {
        if (!active || packet == null || dummyCamera == null) return;
        Minecraft mc = Minecraft.getInstance();

        // スペースキー押下によるスキップ判定
        if (mc.getWindow() != null && GLFW.glfwGetKey(mc.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) {
            stopReplay();
            return;
        }

        // フレーム進行速度（キル直前のラスト10フレームは0.5倍速スローモーション）
        float speed = (currentFrame >= totalFrames - 10) ? 0.5F : 1.0F;
        currentFrame += speed;

        if (currentFrame >= totalFrames - 1) {
            stopReplay();
            return;
        }

        updateDummyCamera();
    }

    private void updateDummyCamera() {
        List<PvpReplayFrame> frames = packet.killerFrames();
        if (frames.isEmpty()) return;

        int indexA = Mth.clamp((int) currentFrame, 0, frames.size() - 1);
        int indexB = Mth.clamp(indexA + 1, 0, frames.size() - 1);
        float alpha = currentFrame - indexA;

        PvpReplayFrame frameA = frames.get(indexA);
        PvpReplayFrame frameB = frames.get(indexB);

        double x = Mth.lerp(alpha, frameA.x(), frameB.x());
        double y = Mth.lerp(alpha, frameA.y() + 1.62D, frameB.y() + 1.62D);
        double z = Mth.lerp(alpha, frameA.z(), frameB.z());

        float yaw = Mth.rotLerp(alpha, frameA.yaw(), frameB.yaw());
        float pitch = Mth.lerp(alpha, frameA.pitch(), frameB.pitch());

        dummyCamera.xo = dummyCamera.getX();
        dummyCamera.yo = dummyCamera.getY();
        dummyCamera.zo = dummyCamera.getZ();
        dummyCamera.yRotO = dummyCamera.getYRot();
        dummyCamera.xRotO = dummyCamera.getXRot();

        dummyCamera.setPos(x, y, z);
        dummyCamera.setYRot(yaw);
        dummyCamera.setXRot(pitch);
        dummyCamera.setYHeadRot(yaw);
    }
}
