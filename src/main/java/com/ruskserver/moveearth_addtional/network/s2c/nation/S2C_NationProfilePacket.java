package com.ruskserver.moveearth_addtional.network.s2c.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Another nation as the diplomacy tab shows it: who its citizens are, how large it is, who it is allied and
 * hostile with, and the wars it is fighting. Whether citizens are online is sent only to its own citizens and
 * its allies ({@code onlineShown}), so nobody can time a raid for when a rival is asleep. Pending alliance
 * requests, the treasury, roles and the capital's whereabouts are never in it.
 */
public record S2C_NationProfilePacket(UUID nationId, String name, String tag, String owner, int chunks,
                                      int outposts, boolean onlineShown, List<Member> members, List<Ref> allies,
                                      List<Ref> hostiles, List<War> wars) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 512;

    public record Member(String name, boolean online) { }

    public record Ref(UUID id, String name, String tag) { }

    /** A siege the nation is in: {@code attacking} when it is the attacker; {@code solo} for a lone raider. */
    public record War(String opponentName, String opponentTag, boolean attacking, boolean solo) { }

    public static final Type<S2C_NationProfilePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nation_profile"));
    public static final StreamCodec<FriendlyByteBuf, S2C_NationProfilePacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.nationId);
                buf.writeUtf(p.name, 128);
                buf.writeUtf(p.tag, 32);
                buf.writeUtf(p.owner, 32);
                buf.writeVarInt(p.chunks);
                buf.writeVarInt(p.outposts);
                buf.writeBoolean(p.onlineShown);
                writeList(buf, p.members, (b, m) -> {
                    b.writeUtf(m.name, 32);
                    b.writeBoolean(m.online);
                });
                writeList(buf, p.allies, S2C_NationProfilePacket::writeRef);
                writeList(buf, p.hostiles, S2C_NationProfilePacket::writeRef);
                writeList(buf, p.wars, (b, w) -> {
                    b.writeUtf(w.opponentName, 128);
                    b.writeUtf(w.opponentTag, 32);
                    b.writeBoolean(w.attacking);
                    b.writeBoolean(w.solo);
                });
            },
            buf -> new S2C_NationProfilePacket(buf.readUUID(), buf.readUtf(128), buf.readUtf(32), buf.readUtf(32),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    readList(buf, b -> new Member(b.readUtf(32), b.readBoolean())),
                    readList(buf, S2C_NationProfilePacket::readRef),
                    readList(buf, S2C_NationProfilePacket::readRef),
                    readList(buf, b -> new War(b.readUtf(128), b.readUtf(32), b.readBoolean(), b.readBoolean()))));

    private static void writeRef(FriendlyByteBuf buf, Ref ref) {
        buf.writeUUID(ref.id);
        buf.writeUtf(ref.name, 128);
        buf.writeUtf(ref.tag, 32);
    }

    private static Ref readRef(FriendlyByteBuf buf) {
        return new Ref(buf.readUUID(), buf.readUtf(128), buf.readUtf(32));
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, java.util.function.BiConsumer<FriendlyByteBuf, T> writer) {
        int count = Math.min(list.size(), MAX_ENTRIES);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) writer.accept(buf, list.get(i));
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid nation profile list size: " + count);
        List<T> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) list.add(reader.apply(buf));
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> com.ruskserver.moveearth_addtional.client.ClientPacketHandler.handleNationProfile(this));
    }
}
