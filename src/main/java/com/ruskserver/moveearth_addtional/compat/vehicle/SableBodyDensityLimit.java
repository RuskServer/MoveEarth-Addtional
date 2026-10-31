package com.ruskserver.moveearth_addtional.compat.vehicle;

import com.ruskserver.moveearth_addtional.config.AeronauticsSwivelConfig;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class SableBodyDensityLimit {
    private static final Map<ServerPlayer, Long> LAST_NOTICE = new WeakHashMap<>();

    private SableBodyDensityLimit() { }

    public static Component review(ServerLevel level, BlockPos anchor, ServerPlayer actor) {
        return review(level, List.of(anchor), actor, false);
    }

    /** Null permits the whole batch. Refusal occurs before Sable moves blocks or runs split listeners. */
    public static Component review(ServerLevel level, List<BlockPos> anchors, ServerPlayer actor, boolean split) {
        if (!AeronauticsSwivelConfig.bodyDensityEnabled() || anchors.isEmpty()) return null;
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return null;
        List<SableBodyDensityPolicy.Point> existing = new ArrayList<>();
        for (var body : container.getAllSubLevels()) {
            if (body.isRemoved()) continue;
            var position = body.logicalPose().position();
            existing.add(new SableBodyDensityPolicy.Point(position.x(), position.z()));
        }
        List<SableBodyDensityPolicy.Point> proposed = new ArrayList<>();
        for (BlockPos anchor : anchors) {
            Vec3 point = Vec3.atCenterOf(anchor);
            var source = Sable.HELPER.getContaining(level, anchor);
            if (source != null) point = source.logicalPose().transformPosition(point);
            proposed.add(new SableBodyDensityPolicy.Point(point.x, point.z));
        }
        int radius = AeronauticsSwivelConfig.bodyDensityRadius();
        int max = AeronauticsSwivelConfig.bodyDensityMax();
        int projected = SableBodyDensityPolicy.projectedCount(existing, proposed, radius, max);
        if (projected <= max) return null;
        var message = split ? SableSafetyMessageCatalog.Message.SPLIT_DEFERRED : SableSafetyMessageCatalog.Message.ASSEMBLY_REFUSED;
        Component reason = SableSafetyMessages.component(message, actor, radius, projected, max);
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            var worldPoint = SableBodyProtection.worldPosition(player);
            var playerPoint = new SableBodyDensityPolicy.Point(worldPoint.x, worldPoint.z);
            if (player != actor && proposed.stream().noneMatch(p -> p.within(playerPoint, radius))) continue;
            Long last = LAST_NOTICE.get(player);
            if (last != null && now >= last && now - last < 100) continue;
            LAST_NOTICE.put(player, now);
            player.sendSystemMessage(MoveEarthMessage.warning(
                    SableSafetyMessages.component(message, player, radius, projected, max)));
        }
        return reason;
    }
}
