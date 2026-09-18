package com.ruskserver.moveearth_addtional.terrain;

/**
 * What a river channel dictates for one position, before the aquifer gets a say.
 *
 * <p>Rivers used to be filled by raising the aquifer's water table over them and
 * letting its own rules place the water. That cannot work, for two reasons that
 * are both built into the aquifer.
 *
 * <p>It samples one jittered point per 16x12x16 cell and gives the whole cell
 * that point's water level. A channel is narrower than a cell, so the sample
 * usually lands beside the river, the cell answers sea level, and the column
 * comes out dry -- which is where the grass in riverbeds came from, since the
 * surface rules read the water they find in the column and lay grass when there
 * is none.
 *
 * <p>And where two neighbouring cells do disagree about their level, {@code
 * calculatePressure} deliberately walls them off from each other with stone. A
 * river that descends makes neighbouring cells disagree by definition, so the
 * disagreement is not an accident to be tuned away: raising the table over a
 * river at y=120 above a sea at y=63 asks for a barrier through everything near
 * the midpoint, y=91. That is the stone appearing where the water level changes.
 *
 * <p>So the channel is decided here instead, from the tile, and the aquifer is
 * skipped for those positions. Deterministic, no sampling, no pressure.
 */
public final class RiverChannelWater {
    /**
     * How far below the water line the channel still claims the column.
     *
     * <p>The deepest carve is about eleven blocks, so anything much below that
     * is a cave that happens to pass under the river rather than the river's own
     * bed, and vanilla should keep deciding it.
     */
    public static final int FILL_DEPTH = 32;

    /** Absent when the channel has no opinion and the aquifer should decide. */
    public enum Fill { NONE, WATER, AIR }

    private RiverChannelWater() { }

    /**
     * @param influence  {@link TerrainTile#channelInfluence}: above zero inside
     *                   the carved bowl, including its banks
     * @param waterLevel {@link TerrainTile#riverWaterLevel}, or
     *                   {@link Integer#MIN_VALUE} where there is no channel
     */
    public static Fill decide(double influence, int waterLevel, int blockY) {
        return decide(influence, waterLevel, blockY, FILL_DEPTH);
    }

    static Fill decide(double influence, int waterLevel, int blockY, int fillDepth) {
        if (waterLevel == Integer.MIN_VALUE || !(influence > 0.0)) {
            return Fill.NONE;
        }
        if (blockY >= waterLevel) {
            // Above its own water line the channel is open air. Saying so keeps
            // the aquifer from hanging a pocket of water or a plug of stone over
            // the river, which is the other half of what it was doing wrong.
            return Fill.AIR;
        }
        return blockY >= waterLevel - fillDepth ? Fill.WATER : Fill.NONE;
    }
}
