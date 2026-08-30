package com.ruskserver.moveearth_addtional.pvp;

import net.minecraft.network.FriendlyByteBuf;

public class PvpReplayFrame {
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private boolean isCrouching;
    private boolean isAiming;
    private boolean isShooting;

    public PvpReplayFrame() {}

    public PvpReplayFrame(double x, double y, double z, float yaw, float pitch, boolean isCrouching, boolean isAiming, boolean isShooting) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.isCrouching = isCrouching;
        this.isAiming = isAiming;
        this.isShooting = isShooting;
    }

    public void set(double x, double y, double z, float yaw, float pitch, boolean isCrouching, boolean isAiming, boolean isShooting) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.isCrouching = isCrouching;
        this.isAiming = isAiming;
        this.isShooting = isShooting;
    }

    public PvpReplayFrame copy() {
        return new PvpReplayFrame(x, y, z, yaw, pitch, isCrouching, isAiming, isShooting);
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean isCrouching() { return isCrouching; }
    public boolean isAiming() { return isAiming; }
    public boolean isShooting() { return isShooting; }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        byte flags = 0;
        if (isCrouching) flags |= 1;
        if (isAiming) flags |= 2;
        if (isShooting) flags |= 4;
        buffer.writeByte(flags);
    }

    public static PvpReplayFrame read(FriendlyByteBuf buffer) {
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        float yaw = buffer.readFloat();
        float pitch = buffer.readFloat();
        byte flags = buffer.readByte();
        return new PvpReplayFrame(
                x, y, z, yaw, pitch,
                (flags & 1) != 0,
                (flags & 2) != 0,
                (flags & 4) != 0
        );
    }
}
