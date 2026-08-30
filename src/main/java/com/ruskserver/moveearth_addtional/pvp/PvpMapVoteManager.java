package com.ruskserver.moveearth_addtional.pvp;

import com.ruskserver.moveearth_addtional.network.S2C_StartMapVotePacket;
import com.ruskserver.moveearth_addtional.network.S2C_UpdateMapVotePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public final class PvpMapVoteManager {
    public static final PvpMapVoteManager INSTANCE = new PvpMapVoteManager();
    public static final int VOTE_DURATION_SECONDS = 15;

    private boolean active;
    private int ticksLeft;
    private final List<PvpMapDefinition> candidates = new ArrayList<>();
    private final Map<UUID, String> votes = new HashMap<>();
    private final Set<UUID> voterUuids = new HashSet<>();

    private PvpMapVoteManager() {}

    public boolean isActive() {
        return active;
    }

    public void startVote(MinecraftServer server, Set<UUID> participants, List<PvpMapDefinition> availableMaps, int seconds) {
        if (availableMaps.isEmpty()) return;
        active = true;
        ticksLeft = seconds * 20;
        votes.clear();
        voterUuids.clear();
        voterUuids.addAll(participants);

        candidates.clear();
        if (availableMaps.size() <= 4) {
            candidates.addAll(availableMaps);
        } else {
            // ランダムに4件選出
            List<PvpMapDefinition> pool = new ArrayList<>(availableMaps);
            Collections.shuffle(pool);
            candidates.addAll(pool.subList(0, 4));
        }

        S2C_StartMapVotePacket startPacket = new S2C_StartMapVotePacket(candidates, seconds);
        for (UUID id : voterUuids) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, startPacket);
            }
        }
        broadcastVoteUpdate(server);
    }

    public void vote(ServerPlayer player, String mapId) {
        if (!active || !voterUuids.contains(player.getUUID())) return;
        boolean valid = candidates.stream().anyMatch(c -> c.id().equals(mapId));
        if (!valid) return;
        votes.put(player.getUUID(), mapId);
        broadcastVoteUpdate(player.server);
    }

    public void tick(MinecraftServer server) {
        if (!active) return;
        ticksLeft--;
        if (ticksLeft % 20 == 0) {
            broadcastVoteUpdate(server);
        }
        if (ticksLeft <= 0) {
            finishVote(server);
        }
    }

    public void cancelVote() {
        active = false;
        ticksLeft = 0;
        candidates.clear();
        votes.clear();
        voterUuids.clear();
    }

    private void finishVote(MinecraftServer server) {
        if (!active) return;
        active = false;

        Map<String, Integer> counts = tallyVotes();
        PvpMapDefinition selected = determineWinner(counts);

        candidates.clear();
        votes.clear();
        voterUuids.clear();

        if (selected != null) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§6[PvP] マップ投票が終了しました: §b" + selected.displayName() + " §fが選ばれました！"), false);
            PvpMatchManager.INSTANCE.onMapVoteFinished(server, selected);
        } else {
            PvpMatchManager.INSTANCE.stop(server);
        }
    }

    private PvpMapDefinition determineWinner(Map<String, Integer> counts) {
        if (candidates.isEmpty()) return null;
        int maxVotes = -1;
        for (int count : counts.values()) {
            if (count > maxVotes) maxVotes = count;
        }

        List<PvpMapDefinition> topMaps = new ArrayList<>();
        for (PvpMapDefinition map : candidates) {
            int c = counts.getOrDefault(map.id(), 0);
            if (c == maxVotes || (maxVotes == 0)) {
                topMaps.add(map);
            }
        }

        if (topMaps.isEmpty()) {
            return candidates.get(0);
        }
        return topMaps.get(new Random().nextInt(topMaps.size()));
    }

    private Map<String, Integer> tallyVotes() {
        Map<String, Integer> counts = new HashMap<>();
        for (PvpMapDefinition candidate : candidates) {
            counts.put(candidate.id(), 0);
        }
        for (String mapId : votes.values()) {
            counts.computeIfPresent(mapId, (k, v) -> v + 1);
        }
        return counts;
    }

    private void broadcastVoteUpdate(MinecraftServer server) {
        Map<String, Integer> tally = tallyVotes();
        int secondsRemaining = Math.max(0, (ticksLeft + 19) / 20);
        S2C_UpdateMapVotePacket packet = new S2C_UpdateMapVotePacket(tally, secondsRemaining);
        for (UUID id : voterUuids) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, packet);
            }
        }
    }
}
