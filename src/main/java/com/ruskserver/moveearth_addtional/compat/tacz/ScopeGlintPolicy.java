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

    /**
     * Below this magnification an optic does not give its owner away.
     *
     * <p>Read off the pack rather than guessed. Every optic in the default gun
     * pack, by its highest zoom:
     *
     * <pre>
     *   1.35 - 2.00   ten red dots and holographics, including every pistol one
     *   2.50          ACOG, and the T1/T2/UH-1 sights
     *   3.00 - 3.25   QMK152, HAMR, retro 2x
     *   4.25 - 25.00  ELCAN, K98 and everything a sniper actually carries
     * </pre>
     *
     * <p>Four is the line because the point of this is that lying still with a
     * rifle costs something. A mid-range combat optic is carried by someone who
     * is moving anyway, and there is no overlap to argue about there -- the gap
     * between 3.25 and 4.25 is the widest in the whole list.
     *
     * <p>A constant rather than a setting. It decides what other players can
     * see, so a client that could lower it would be a client that spots snipers
     * nobody else can.
     */
    private static final float MINIMUM_ZOOM = 4.0F;

    /** Ignore the first part of the raise; the lens is not up yet. */
    private static final float MINIMUM_PROGRESS = 0.35F;

    private ScopeGlintPolicy() { }

    /** True when this player is aiming a gun that carries a magnified scope. */
    public static boolean scopedAndAiming(Player player, float partialTick) {
        return describe(player, partialTick).glints();
    }

    /**
     * Every condition, and what each one answered.
     *
     * <p>The glint has four ways to be absent and they look identical from
     * inside the game. Worse, the zoom threshold was chosen against gun-pack
     * data nobody had looked at, so "no glint" could equally mean the rifle is
     * not scoped, the aim has not finished, or the number is simply wrong for
     * this pack. This reports each separately so the answer is read rather
     * than guessed.
     *
     * @param zoom the optic's zoom values as the pack declares them, so a
     *             threshold can be set from what is there instead of from
     *             what was assumed
     */
    public record Reading(boolean gun, float progress, String scopeId, float[] zoom,
                          boolean glints) {

        static Reading none(boolean gun, float progress) {
            return new Reading(gun, progress, "", new float[0], false);
        }
    }

    public static Reading describe(Player player, float partialTick) {
        float progress = aimingProgress(player, partialTick);
        ItemStack held = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(held);
        if (gun == null) {
            return Reading.none(false, progress);
        }
        ResourceLocation opticId = null;
        ItemStack scope = gun.getAttachment(player.registryAccess(), held, AttachmentType.SCOPE);
        if (!scope.isEmpty()) {
            IAttachment attachment = IAttachment.getIAttachmentOrNull(scope);
            opticId = attachment == null ? null : attachment.getAttachmentId(scope);
        }
        if (opticId == null) {
            // A built-in optic still counts: a rifle whose scope cannot be
            // removed is the most scoped thing in the game.
            opticId = gun.getBuiltInAttachmentId(held, AttachmentType.SCOPE);
        }
        if (opticId == null) {
            return Reading.none(true, progress);
        }
        float[] zoom = zoomOf(opticId);
        return new Reading(true, progress, opticId.toString(), zoom,
                progress > 0.0F && magnified(zoom));
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
    private static float[] zoomOf(ResourceLocation attachmentId) {
        return TimelessAPI.getClientAttachmentIndex(attachmentId)
                .map(index -> {
                    float[] zoom = index.getZoom();
                    return zoom == null ? new float[0] : zoom;
                })
                .orElse(new float[0]);
    }

    /**
     * Whether an optic magnifies enough to show.
     *
     * <p>The highest setting decides. A variable scope is one piece of glass
     * whichever way it is dialled, and taking the lowest would have excluded
     * every variable optic in the pack -- the 1.25x bottom end of a 25x Mk5HD
     * counts as a red dot by that reading. It would also have handed everyone
     * an obvious way to carry a sniper scope that never glints.
     */
    private static boolean magnified(float[] zoom) {
        float highest = 0.0F;
        for (float value : zoom) {
            highest = Math.max(highest, value);
        }
        return highest >= MINIMUM_ZOOM;
    }
}
