package com.ruskserver.moveearth_addtional.nether;

import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Whether a Create fan blowing through lava reaches a block: the same current
 * that blasts items on a belt. The fan must face the block along a straight
 * line, its current must reach the block's face, and the air just before the
 * block must already carry the blasting type, i.e. have passed the lava.
 */
public final class LavaFanHeat {
    /** Create's default fan range; a fan further away cannot reach anyway. */
    private static final int MAX_DISTANCE = 20;

    private LavaFanHeat() { }

    public static boolean isHeated(Level level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            for (int distance = 2; distance <= MAX_DISTANCE; distance++) {
                BlockPos fanPos = pos.relative(side, distance);
                if (!level.isLoaded(fanPos)) break;
                if (!(level.getBlockEntity(fanPos) instanceof EncasedFanBlockEntity fan)) continue;
                AirCurrent current = fan.getAirCurrent();
                if (current != null && current.direction == side.getOpposite()
                        && reaches(current.maxDistance, distance)
                        && current.getTypeAt(distance - 1) == AllFanProcessingTypes.BLASTING) {
                    return true;
                }
                break;
            }
        }
        return false;
    }

    /** A current that stops at the block's face reaches the block {@code distance} away. */
    static boolean reaches(float maxDistance, int distance) {
        return maxDistance >= distance - 1.0F - 0.001F;
    }
}
