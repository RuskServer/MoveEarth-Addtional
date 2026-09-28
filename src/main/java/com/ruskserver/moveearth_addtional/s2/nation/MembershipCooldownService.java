package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Player-facing side of {@link MembershipCooldownPolicy}. */
public final class MembershipCooldownService {
    private MembershipCooldownService() { }

    /** Open-time duration as "Xh Ym", rounded up to the minute. */
    public static Component duration(long openTicks) {
        long minutes = MembershipCooldownPolicy.remainingMinutes(openTicks);
        return Component.translatable("screen.moveearth_addtional.open_time.duration",
                minutes / 60L, minutes % 60L);
    }

    /** Tells a player who just lost a membership how long they must wait before joining again. */
    public static void notifyStarted(ServerPlayer player) {
        if (player == null) return;
        long remaining = NationSavedData.get(player.server)
                .membershipCooldownRemaining(player.getUUID(), OpenTimeService.now(player.server));
        if (remaining <= 0L) return;
        player.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                "message.moveearth_addtional.nation.membership_cooldown_started", duration(remaining))));
    }

    /** Tells a kicked player they may join elsewhere now but stay bound to the former nation's truces. */
    public static void notifyKicked(ServerPlayer player) {
        if (player == null) return;
        long remaining = NationSavedData.get(player.server)
                .formerNationBindingRemaining(player.getUUID(), OpenTimeService.now(player.server));
        if (remaining <= 0L) return;
        player.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                "message.moveearth_addtional.nation.kicked_binding_started", duration(remaining))));
    }

    /** Tells a refused player how much server-opening time is left before they may join or found a nation. */
    public static void notifyRefused(ServerPlayer player) {
        long remaining = NationSavedData.get(player.server)
                .membershipCooldownRemaining(player.getUUID(), OpenTimeService.now(player.server));
        if (remaining <= 0L) return;
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                "message.moveearth_addtional.nation.membership_cooldown", duration(remaining))));
    }
}
