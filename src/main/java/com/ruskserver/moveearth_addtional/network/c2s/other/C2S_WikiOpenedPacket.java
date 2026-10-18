package com.ruskserver.moveearth_addtional.network.c2s.other;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.advancement.ModCriteria;
import com.ruskserver.moveearth_addtional.analytics.event.GameEventType;
import com.ruskserver.moveearth_addtional.analytics.event.GameEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The in-game wiki, which lives entirely on the client, was opened ({@code opened}) or moved to another page.
 * Opening completes a tutorial step; both are counted in analytics, so how often players reach the wiki,
 * by which way and for which items, and which pages they read, can be measured.
 */
public record C2S_WikiOpenedPacket(boolean opened, String source, String item, String page)
        implements CustomPacketPayload {
    public static final Type<C2S_WikiOpenedPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "wiki_opened"));

    public static final StreamCodec<FriendlyByteBuf, C2S_WikiOpenedPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBoolean(packet.opened);
                buffer.writeUtf(packet.source, 16);
                buffer.writeUtf(packet.item, 128);
                buffer.writeUtf(packet.page, 128);
            },
            buffer -> new C2S_WikiOpenedPacket(buffer.readBoolean(), buffer.readUtf(16), buffer.readUtf(128),
                    buffer.readUtf(128)));

    public C2S_WikiOpenedPacket {
        source = source == null ? "" : source;
        item = item == null ? "" : item;
        page = page == null ? "" : page;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            String page = WikiUsagePolicy.page(this.page);
            if (opened) {
                ModCriteria.trigger(player, ModCriteria.WIKI_OPENED);
                com.ruskserver.moveearth_addtional.s2.presence.HiddenAdvancementService.wikiOpened(player);
                GameEvents.player(GameEventType.WIKI_OPENED, player, 1L,
                        WikiUsagePolicy.openedDetail(source, item));
            }
            if (!page.isEmpty()) GameEvents.player(GameEventType.WIKI_PAGE_VIEWED, player, 1L, page);
        });
    }
}
