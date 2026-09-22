package com.ruskserver.moveearth_addtional.compat.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Whether a player is looking down a magnified optic right now.
 *
 * <p>Everything here is read from state Timeless Ammunition already keeps in
 * sync for its own animations, so the answer is available on any client for
 * any player it can see, without a packet of our own.
 *
 * <p>Magnified only. An iron sight or a red dot has no objective lens to catch
 * the light, and a pistol that gave its owner away every time they aimed would
 * turn a rifle's tell into everybody's problem.
 */
public final class ScopeGlintPolicy {

    /** Below this, an optic is a red dot rather than a scope. */
    private static final float MINIMUM_ZOOM = 1.5F;

    /** Ignore the first part of the raise; the lens is not up yet. */
    private static final float MINIMUM_PROGRESS = 0.35F;

    private ScopeGlintPolicy() { }

    /** True when this player is aiming a gun that carries a magnified scope. */
    public static boolean scopedAndAiming(Player player, float partialTick) {
        if (aimingProgress(player, partialTick) <= 0.0F) {
            return false;
        }
        ItemStack held = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(held);
        if (gun == null) {
            return false;
        }
        ItemStack scope = gun.getAttachment(player.registryAccess(), held, AttachmentType.SCOPE);
        if (scope.isEmpty()) {
            // A built-in optic still counts: a rifle whose scope cannot be
            // removed is the most scoped thing in the game.
            ResourceLocation builtin = gun.getBuiltInAttachmentId(held, AttachmentType.SCOPE);
            return builtin != null && magnified(builtin);
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
        return attachment != null && magnified(attachment.getAttachmentId(scope));
    }

    /**
     * How far into the aim the player is, 0 when not aiming.
     *
     * <p>Rises with the animation rather than snapping on, so the glint comes
     * up with the rifle. A flag alone would pop, and a popped highlight reads
     * as a bug before it reads as a sniper.
     */
    public static float aimingProgress(Player player, float partialTick) {
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (operator == null || !operator.getSynIsAiming()) {
            return 0.0F;
        }
        float progress = operator.getSynAimingProgress();
        if (progress <= MINIMUM_PROGRESS) {
            return 0.0F;
        }
        return (progress - MINIMUM_PROGRESS) / (1.0F - MINIMUM_PROGRESS);
    }

    /**
     * Whether an optic magnifies.
     *
     * <p>Zoom is a list, a scope being allowed several settings. The lowest is
     * what decides: a variable optic dialled down is still a scope, and its
     * front lens is the same piece of glass either way.
     */
    private static boolean magnified(ResourceLocation attachmentId) {
        return TimelessAPI.getClientAttachmentIndex(attachmentId)
                .map(index -> {
                    float[] zoom = index.getZoom();
                    if (zoom == null || zoom.length == 0) {
                        return false;
                    }
                    float lowest = zoom[0];
                    for (float value : zoom) {
                        lowest = Math.min(lowest, value);
                    }
                    return lowest >= MINIMUM_ZOOM;
                })
                .orElse(false);
    }
}
