package com.ruskserver.moveearth_addtional.network.s2c.pvp;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The deploy screen: opened after the killcam and when joining a match, refreshed while it is open, closed on
 * deploying. Only the player's own team's points are sent.
 *
 * @param first      the opening deploy of a match or of joining one, rather than after a death
 * @param autoTicks  ticks until the player is deployed anyway
 * @param selected   the chosen point's id, or {@link #NO_POINT} to let the server pick
 * @param hasDeath   whether {@code deathX}/{@code deathZ} hold where the player fell
 * @param hasKiller  whether {@code killerX}/{@code killerZ} hold where their killer stood
 */
public record S2C_PvpDeployPacket(boolean open, String mapId, String mapName, boolean first, int autoTicks, int team, List<Point> points,
                                  long selected, String loadoutId, boolean hasDeath, int deathX, int deathZ,
                                  boolean hasKiller, int killerX, int killerZ) implements CustomPacketPayload {
    public static final long NO_POINT = Long.MIN_VALUE;
    public static final int MAX_POINTS = 64;

    /** A deploy point; {@code danger} when an enemy is close enough that it cannot be chosen. */
    public record Point(long id, String name, int x, int y, int z, boolean danger) { }

    public static final Type<S2C_PvpDeployPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "pvp_deploy"));
    public static final StreamCodec<FriendlyByteBuf, S2C_PvpDeployPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.open);
                buf.writeUtf(p.mapId, 64);
                buf.writeUtf(p.mapName, 128);
                buf.writeBoolean(p.first);
                buf.writeVarInt(p.autoTicks);
                buf.writeVarInt(p.team);
                int count = Math.min(p.points.size(), MAX_POINTS);
                buf.writeVarInt(count);
                for (Point point : p.points.subList(0, count)) {
                    buf.writeLong(point.id);
                    buf.writeUtf(point.name, 96);
                    buf.writeInt(point.x);
                    buf.writeInt(point.y);
                    buf.writeInt(point.z);
                    buf.writeBoolean(point.danger);
                }
                buf.writeLong(p.selected);
                buf.writeUtf(p.loadoutId, 64);
                buf.writeBoolean(p.hasDeath);
                buf.writeInt(p.deathX);
                buf.writeInt(p.deathZ);
                buf.writeBoolean(p.hasKiller);
                buf.writeInt(p.killerX);
                buf.writeInt(p.killerZ);
            },
            buf -> {
                boolean open = buf.readBoolean();
                String mapId = buf.readUtf(64);
                String mapName = buf.readUtf(128);
                boolean first = buf.readBoolean();
                int autoTicks = buf.readVarInt();
                int team = buf.readVarInt();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_POINTS) throw new IllegalArgumentException("Invalid deploy point count: " + count);
                List<Point> points = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    points.add(new Point(buf.readLong(), buf.readUtf(96), buf.readInt(), buf.readInt(), buf.readInt(),
                            buf.readBoolean()));
                }
                return new S2C_PvpDeployPacket(open, mapId, mapName, first, autoTicks, team, points, buf.readLong(), buf.readUtf(64),
                        buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readInt(), buf.readInt());
            });

    public static S2C_PvpDeployPacket closed() {
        return new S2C_PvpDeployPacket(false, "", "", false, 0, 0, List.of(), NO_POINT, "", false, 0, 0, false, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handlePvpDeploy(this));
    }
}
