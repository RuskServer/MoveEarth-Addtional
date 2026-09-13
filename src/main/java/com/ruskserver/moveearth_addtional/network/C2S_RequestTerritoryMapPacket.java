package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Requests the public nation-territory layer used by vanilla maps and atlas mods. */
public record C2S_RequestTerritoryMapPacket() implements CustomPacketPayload {
    private static final int MAX_CORES = 4096;
    private static final int MAX_NATIONS = 512;
    private static final String LAST_REQUEST_TAG = "moveearth_addtional:territory_map_request_tick";

    public static final Type<C2S_RequestTerritoryMapPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "territory_map_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RequestTerritoryMapPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> { }, buffer -> new C2S_RequestTerritoryMapPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            long now = player.server.overworld().getGameTime();
            long previous = player.getPersistentData().getLong(LAST_REQUEST_TAG);
            if (previous > 0L && now >= previous && now - previous < 40L) return;
            player.getPersistentData().putLong(LAST_REQUEST_TAG, now);
            TerritorySavedData territories = TerritorySavedData.get(player.server);
            NationSavedData nations = NationSavedData.get(player.server);
            UUID viewerNation = nations.nationIdFor(player.getUUID()).orElse(null);

            var cores = territories.cores().stream()
                    .filter(core -> core.state() == TerritorySavedData.CoreState.ACTIVE
                            || core.state() == TerritorySavedData.CoreState.EXPOSED
                            || core.state() == TerritorySavedData.CoreState.FALLEN)
                    .sorted(Comparator.comparing((TerritorySavedData.CoreRecord core) ->
                                    core.dimension().toString())
                            .thenComparingInt(core -> core.pos().getX())
                            .thenComparingInt(core -> core.pos().getZ()))
                    .limit(MAX_CORES)
                    .map(core -> new S2C_TerritoryMapPacket.CoreEntry(
                            core.nationId(), core.dimension(), core.pos().getX() >> 4,
                            core.pos().getZ() >> 4, core.radius(),
                            S2C_TerritoryMapPacket.CoreState.valueOf(core.state().name()),
                            core.type() == TerritorySavedData.CoreType.CAPITAL))
                    .toList();
            LinkedHashSet<UUID> referencedNations = new LinkedHashSet<>();
            cores.forEach(core -> referencedNations.add(core.nationId()));
            var nationEntries = referencedNations.stream().limit(MAX_NATIONS).map(nationId -> {
                NationSavedData.Nation nation = nations.nation(nationId).orElse(null);
                S2C_TerritoryMapPacket.Relation relation;
                if (viewerNation != null && viewerNation.equals(nationId)) {
                    relation = S2C_TerritoryMapPacket.Relation.OWN;
                } else if (viewerNation != null && nations.isAllied(viewerNation, nationId)) {
                    relation = S2C_TerritoryMapPacket.Relation.ALLIED;
                } else if (viewerNation != null
                        && nations.relation(viewerNation, nationId) == NationSavedData.DiplomacyRelation.HOSTILE) {
                    relation = S2C_TerritoryMapPacket.Relation.HOSTILE;
                } else {
                    relation = S2C_TerritoryMapPacket.Relation.FOREIGN;
                }
                return new S2C_TerritoryMapPacket.NationEntry(nationId,
                        nation == null ? "Unknown" : nation.name(), nation == null ? "" : nation.tag(), relation);
            }).toList();
            PacketDistributor.sendToPlayer(player, new S2C_TerritoryMapPacket(nationEntries, cores));
        });
    }
}
