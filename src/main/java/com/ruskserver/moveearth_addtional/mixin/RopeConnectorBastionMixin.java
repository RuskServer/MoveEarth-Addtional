package com.ruskserver.moveearth_addtional.mixin;

import com.ruskserver.moveearth_addtional.s2.territory.RemoteBlockPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps thrown and launched rope connectors out of other nations' Bastions. The mod places the connector by
 * calling {@code BlockItem.place} itself, which raises no placement event, so the Bastion rule never ran: a
 * connector could be thrown into enemy land, or shot there by a launcher on redstone with no one aboard.
 *
 * <p>Checked where the projectile lands, the one point every path passes through: a hand throw and a crewed
 * launcher carry the shooter as owner, a redstone shot only the launcher's position. A refused shot fails the
 * mod's own way, handing the connector back. Blocks on vehicles are not territory, so ships stay fair game.
 *
 * <p>{@code @Pseudo}: Throwable Rope Connector is optional.
 */
@Pseudo
@Mixin(targets = "net.sprocketgames.create_aeronautics_throwable_rope_connector.entity.ThrowableRopeConnectorProjectile",
        remap = false)
public abstract class RopeConnectorBastionMixin {
    @Shadow(remap = false)
    private BlockPos mountedSourcePos;

    @Shadow(remap = false)
    private void failAndReturn(Component message) {
    }

    @Inject(method = "onHitBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void moveearth$keepOutOfBastions(BlockHitResult result, CallbackInfo ci) {
        Projectile self = (Projectile) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) return;
        ServerPlayer shooter = self.getOwner() instanceof ServerPlayer player ? player : null;
        BlockPos placePos = result.getBlockPos().relative(result.getDirection());
        // A failed hand throw shows its message on the action bar; a launcher's failure shows nothing.
        boolean launched = mountedSourcePos != null;
        if (!RemoteBlockPlacement.refused(level, shooter, shooter == null ? mountedSourcePos : null, placePos,
                launched)) return;
        failAndReturn(Component.translatable("message.moveearth_addtional.bastion.block_place"));
        ci.cancel();
    }
}
