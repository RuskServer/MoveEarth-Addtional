package com.ruskserver.moveearth_addtional.compat.cdg;

import com.ruskserver.moveearth_addtional.region.RegionProfiles;
import com.ruskserver.moveearth_addtional.region.RegionResolver;
import com.ruskserver.moveearth_addtional.region.TraceChance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * How much oil a chunk may hold, given the region it lies in.
 *
 * <p>Oil is the third kind of resource this system touches and the only one
 * that is not a block. Create: Diesel Generators keeps it as an amount per
 * chunk, worked out from the seed, so there is nothing in the world to gate --
 * only a number to change before anyone reads it.
 *
 * <p>That number is read by the pumpjack, by the Oil Scanner and by the mod's
 * own commands, and all three reach it through one function. Changing it there
 * means the scanner and the ground agree without anything being kept in step,
 * which is the failure the deposit gate had to be checked for separately.
 *
 * <p>Falls back to leaving oil alone. A world with no region map, or a lookup
 * that cannot place the chunk, gets plain Create: Diesel Generators behaviour
 * rather than a guess: too much oil is a balance problem, while oil that
 * silently stops existing is a world nobody can fix.
 */
public final class RegionOilGate {

    /** The name the region profiles know oil by. */
    public static final String MATERIAL = "crude_oil";

    private RegionOilGate() { }

    /**
     * The multiplier for a chunk: 0 when the region may not have oil at all.
     *
     * <p>Only the overworld. Oil in the nether would be gated by a map that
     * describes the overworld's regions, which is how the deposit gate first
     * went wrong.
     */
    public static double multiplierAt(ServerLevel level, ChunkPos chunk) {
        if (level == null || chunk == null || !RegionProfiles.ready()) {
            return 1.0;
        }
        if (!Level.OVERWORLD.equals(level.dimension())) {
            return 1.0;
        }
        int region = RegionResolver.regionAt(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        double density = RegionProfiles.densityFor(region, MATERIAL);
        if (RegionProfiles.allows(region, MATERIAL)) {
            // A region that was given oil, or a world where oil is not
            // exclusive at all: the figure says how rich the ground is, so it
            // scales what a well holds.
            return density;
        }
        // A region that was not given oil still has some, and here the figure
        // means something else. Scaling every chunk by it would leave the whole
        // region drilled through with wells that are all nearly dry -- a worse
        // world rather than a rarer resource, and still enough to supply a
        // patient nation. Spent on rarity instead: almost no chunk has oil, and
        // the few that do are worth finding.
        return TraceChance.occurs(level.getSeed(), chunk.x, chunk.z, MATERIAL, density)
                ? 1.0
                : 0.0;
    }

    /**
     * Applies a multiplier to an amount without inventing an infinite well.
     *
     * <p>Create: Diesel Generators uses {@link Integer#MAX_VALUE} to mean "this
     * chunk never runs out". A scaled-up amount that happened to land on that
     * value would turn a rich chunk into a bottomless one, so the result stops
     * one short of it. The sentinel itself never reaches here: a region that
     * may not have oil is cut off before the amount is worked out, and one that
     * may keeps whatever the mod decided.
     */
    public static int scale(int amount, double multiplier) {
        double scaled = amount * multiplier;
        if (scaled >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE - 1;
        }
        return (int) Math.max(0L, Math.round(scaled));
    }
}
