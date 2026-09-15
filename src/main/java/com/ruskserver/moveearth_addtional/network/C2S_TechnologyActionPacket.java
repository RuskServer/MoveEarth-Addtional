package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/** Records harmless UI tutorial actions; gameplay achievements never trust this packet. */
public record C2S_TechnologyActionPacket(String action) implements CustomPacketPayload {
    private static final Set<String> ALLOWED = Set.of(
            "jei_recipe_opened", "combat_rules_viewed", "storage_rules_viewed");
    public static final Type<C2S_TechnologyActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "technology_action"));
    public static final StreamCodec<FriendlyByteBuf, C2S_TechnologyActionPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeUtf(packet.action, 48),
            buffer -> new C2S_TechnologyActionPacket(buffer.readUtf(48)));

    public C2S_TechnologyActionPacket {
        action = action == null ? "" : action;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && ALLOWED.contains(action)) {
                NationTechnologySavedData.get(player.server).recordAction(player, action);
            }
        });
    }
}
