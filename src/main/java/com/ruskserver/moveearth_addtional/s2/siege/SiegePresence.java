package com.ruskserver.moveearth_addtional.s2.siege;

import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/**
 * Whether a siege is being pressed on the ground right now.
 *
 * <p>A siege ended when nobody damaged the walls for half an hour, which read
 * as "the attack was abandoned" and was true until someone worked out that a
 * defence only has to make the approach take longer than that. Strip the land
 * inside your border down to bedrock and no attacker can bridge it -- Bastion
 * stops them placing a block -- so they descend, tunnel, and the siege expires
 * underneath them while they are still digging. Nothing was defended; the clock
 * simply ran out.
 *
 * <p>So the clock stops while an enemy is standing in your territory. A
 * besieging force in your land is a siege whether or not it is hitting the wall
 * this minute, and the answer to one is to go and remove it rather than to wait.
 *
 * <p>Not free forever: the hold is budgeted, so a single player hiding in a hole
 * cannot keep a nation under siege indefinitely. When the budget runs out the
 * clock resumes and the attack has to land a hit like any other.
 */
public final class SiegePresence {

    private SiegePresence() { }

    /** True when someone on the attacking side stands inside the defended territory. */
    public static boolean contested(MinecraftServer server, SiegeSavedData.SiegeRecord siege) {
        if (server == null || siege == null) {
            return false;
        }
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(server)
                .coreById(siege.coreId()).orElse(null);
        if (core == null) {
            return false;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !attacks(server, siege, player)) {
                continue;
            }
            if (inside(core, player.level().dimension().location(),
                    player.chunkPosition())) {
                return true;
            }
        }
        return false;
    }

    /** Whether this player is on the attacking side of this siege. */
    public static boolean attacks(MinecraftServer server, SiegeSavedData.SiegeRecord siege,
                                  ServerPlayer player) {
        if (siege.individualAttacker()) {
            return siege.attackerNation().equals(player.getUUID());
        }
        UUID nation = NationSavedData.get(server).nationIdFor(player.getUUID()).orElse(null);
        return nation != null && nation.equals(siege.attackerNation());
    }

    /** Whether a chunk lies in the core's claimed square. */
    private static boolean inside(TerritorySavedData.CoreRecord core, ResourceLocation dimension,
                                  ChunkPos chunk) {
        if (!core.dimension().equals(dimension)) {
            return false;
        }
        // The claim is a square of chunks around the core, so the test is on
        // chunk coordinates rather than blocks: a player one block outside the
        // last claimed chunk is outside, and the edge has to agree with what
        // the territory itself calls its own.
        ChunkPos centre = new ChunkPos(core.pos());
        int radius = core.radius();
        return Math.abs(chunk.x - centre.x) <= radius && Math.abs(chunk.z - centre.z) <= radius;
    }
}
