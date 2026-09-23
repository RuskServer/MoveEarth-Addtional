package com.ruskserver.moveearth_addtional.network;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Requests the public nation-territory layer used by vanilla maps and atlas mods. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public record C2S_RequestTerritoryMapPacket(ResourceLocation dimension) implements CustomPacketPayload {
    private static final int MAX_CORES = 4096;
    private static final int MAX_NATIONS = 512;
    private static final Map<UUID, RequestState> LAST_REQUESTS = new HashMap<>();

    public static final Type<C2S_RequestTerritoryMapPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "territory_map_request"));
    public static final StreamCodec<FriendlyByteBuf, C2S_RequestTerritoryMapPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> buffer.writeResourceLocation(packet.dimension),
            buffer -> new C2S_RequestTerritoryMapPacket(buffer.readResourceLocation()));

    public C2S_RequestTerritoryMapPacket {
        if (dimension == null) dimension = ResourceLocation.withDefaultNamespace("overworld");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension)) == null) return;
            long now = player.server.overworld().getGameTime();
            RequestState previous = LAST_REQUESTS.get(player.getUUID());
            if (previous != null && now >= previous.requestTick && now - previous.requestTick < 40L) return;
            TerritorySavedData territories = TerritorySavedData.get(player.server);
            NationSavedData nations = NationSavedData.get(player.server);
            UUID viewerNation = nations.nationIdFor(player.getUUID()).orElse(null);

            var candidateCores = territories.cores().stream()
                    .filter(core -> core.dimension().equals(dimension))
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
                            core.pos().getZ() >> 4, NationUpkeepService.effectiveTerritoryRadius(
                                    player.server, core.nationId(), core.radius()),
                            S2C_TerritoryMapPacket.CoreState.valueOf(core.state().name()),
                            core.type() == TerritorySavedData.CoreType.CAPITAL))
                    .toList();
            LinkedHashSet<UUID> referencedNations = new LinkedHashSet<>();
            for (S2C_TerritoryMapPacket.CoreEntry core : candidateCores) {
                if (referencedNations.contains(core.nationId())) continue;
                if (referencedNations.size() >= MAX_NATIONS) continue;
                referencedNations.add(core.nationId());
            }
            var cores = candidateCores.stream()
                    .filter(core -> referencedNations.contains(core.nationId())).toList();
            var nationEntries = referencedNations.stream().map(nationId -> {
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
            S2C_TerritoryMapPacket response = new S2C_TerritoryMapPacket(dimension, nationEntries, cores);
            long signature = signature(response);
            LAST_REQUESTS.put(player.getUUID(), new RequestState(now, dimension, signature));
            com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.TERRITORY_VIEWED);
            if (previous != null && previous.dimension.equals(dimension) && previous.signature == signature) return;
            PacketDistributor.sendToPlayer(player, response);
        });
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_REQUESTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_REQUESTS.clear();
    }

    private static long signature(S2C_TerritoryMapPacket packet) {
        long hash = mix(0xcbf29ce484222325L, packet.dimension().hashCode());
        for (S2C_TerritoryMapPacket.NationEntry nation : packet.nations()) {
            hash = mix(hash, nation.nationId().getMostSignificantBits());
            hash = mix(hash, nation.nationId().getLeastSignificantBits());
            hash = mix(hash, nation.name().hashCode());
            hash = mix(hash, nation.tag().hashCode());
            hash = mix(hash, nation.relation().ordinal());
        }
        for (S2C_TerritoryMapPacket.CoreEntry core : packet.cores()) {
            hash = mix(hash, core.nationId().getMostSignificantBits());
            hash = mix(hash, core.nationId().getLeastSignificantBits());
            hash = mix(hash, core.centerChunkX());
            hash = mix(hash, core.centerChunkZ());
            hash = mix(hash, core.radius());
            hash = mix(hash, core.state().ordinal());
            hash = mix(hash, core.capital() ? 1L : 0L);
        }
        return hash;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }

    private record RequestState(long requestTick, ResourceLocation dimension, long signature) { }
}
