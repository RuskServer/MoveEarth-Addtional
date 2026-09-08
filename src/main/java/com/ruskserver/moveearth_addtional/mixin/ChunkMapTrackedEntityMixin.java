package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.handler.occlusion.PlayerVisibilityTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * サーバー側エンティティトラッキング（ChunkMap.TrackedEntity）に介入し、
 * サブチャンク透過グラフによる視界判定を適用するMixin。
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class ChunkMapTrackedEntityMixin {

    @Redirect(
            method = "updatePlayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;broadcastToPlayer(Lnet/minecraft/server/level/ServerPlayer;)Z"
            )
    )
    private boolean moveearthAdditional$filterEntityVisibility(Entity entity, ServerPlayer player) {
        // バニラの標準可視判定をチェック
        if (!entity.broadcastToPlayer(player)) {
            return false;
        }

        // サブチャンク透過グラフによる可視判定
        return PlayerVisibilityTracker.canPlayerTrackEntity(player, entity);
    }
}
