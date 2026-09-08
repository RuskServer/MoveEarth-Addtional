package com.ruskserver.moveearth_addtional.tpa;

import com.ruskserver.moveearth_addtional.block.entity.PlayerDetectorBlockEntity;
import com.ruskserver.moveearth_addtional.data.DetectorBlockPositionSavedData;
import com.ruskserver.moveearth_addtional.data.PlayerWhitelistSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;

/** Resolves the detector that authorizes a TPA destination host. */
public final class DetectorTeleportAccess {
    private DetectorTeleportAccess() {
    }

    public static Optional<Access> findForHost(ServerPlayer host) {
        ServerLevel level = host.serverLevel();
        Vec3 destination = host.position();
        long now = System.currentTimeMillis();
        PlayerWhitelistSavedData whitelists = PlayerWhitelistSavedData.get(level);
        double rangeSqr = PlayerDetectorBlockEntity.DETECTION_RANGE
                * PlayerDetectorBlockEntity.DETECTION_RANGE;

        for (BlockPos detectorPos : DetectorBlockPositionSavedData.get(level).getPositions()) {
            if (Vec3.atCenterOf(detectorPos).distanceToSqr(destination) > rangeSqr
                    || !(level.getBlockEntity(detectorPos) instanceof PlayerDetectorBlockEntity detector)
                    || !detector.isOperational(now)
                    || !isMember(host, detector, whitelists)) {
                continue;
            }
            return Optional.of(new Access(level, detectorPos));
        }
        return Optional.empty();
    }

    private static boolean isMember(ServerPlayer host, PlayerDetectorBlockEntity detector,
                                    PlayerWhitelistSavedData whitelists) {
        if (host.getUUID().equals(detector.getOwnerUUID())) {
            return true;
        }
        return whitelists.isWhitelisted(detector.getOwnerUUID(), host.getUUID());
    }

    public record Access(ServerLevel level, BlockPos detectorPos) {
        public boolean contains(Vec3 position) {
            return Vec3.atCenterOf(detectorPos).distanceToSqr(position)
                    <= PlayerDetectorBlockEntity.DETECTION_RANGE
                    * PlayerDetectorBlockEntity.DETECTION_RANGE;
        }
    }
}
