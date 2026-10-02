package com.ruskserver.moveearth_addtional.client;

import com.ruskserver.moveearth_addtional.network.s2c.vehicle.S2C_WeldingTargetPacket;
import com.ruskserver.moveearth_addtional.s2.siege.CoreSabotagePrompt;
import net.minecraft.core.BlockPos;

/** Latest server resolution of the aimed block; answers only for that exact block so stale data never shows. */
public final class WeldingTargetClientState {
    private static S2C_WeldingTargetPacket latest = S2C_WeldingTargetPacket.NONE;

    private WeldingTargetClientState() { }

    public static void update(S2C_WeldingTargetPacket packet) {
        latest = packet == null ? S2C_WeldingTargetPacket.NONE : packet;
    }

    public static void clear() {
        latest = S2C_WeldingTargetPacket.NONE;
    }

    public static CoreSabotagePrompt prompt(BlockPos target) {
        return matches(target) ? latest.prompt() : CoreSabotagePrompt.NONE;
    }

    public static boolean hasPrompt(BlockPos target) {
        return prompt(target).kind() != CoreSabotagePrompt.Kind.NONE;
    }

    /** {@code null} until the server has resolved this block. */
    public static Boolean reinforceable(BlockPos target) {
        return matches(target) ? latest.reinforceable() : null;
    }

    /** Minutes left on the own configuring reservation at this block, 0 once lapsed, -1 where none applies. */
    public static int reservationMinutes(BlockPos target) {
        return matches(target) ? latest.reservationMinutes()
                : com.ruskserver.moveearth_addtional.s2.territory.ConfiguringReservationPolicy.NO_RESERVATION;
    }

    private static boolean matches(BlockPos target) {
        return target != null && target.equals(latest.target());
    }
}
