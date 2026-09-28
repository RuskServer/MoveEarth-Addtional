package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.EconomyGuardConfig;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Jobs income and event points require nation membership and a player who is actually at the
 * keyboard. The analytics AFK flag is not used: it counts kills and Jobs XP themselves as
 * activity, so an autoclicker mob farm would look active.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EarningEligibility {
    private static final long NOTICE_INTERVAL_MILLIS = 180_000L;
    private static final Map<UUID, Look> looks = new HashMap<>();
    private static final Map<UUID, Long> lastNotice = new HashMap<>();

    private record Look(float yaw, float pitch, long lastTurnMillis) { }

    private EarningEligibility() { }

    public static EarningPolicy.Refusal refusal(ServerPlayer player) {
        boolean inNation = NationSavedData.get(player.server).nationIdFor(player.getUUID()).isPresent();
        Look look = looks.get(player.getUUID());
        boolean idle = EarningPolicy.idle(look == null ? 0L : look.lastTurnMillis(),
                System.currentTimeMillis(), EconomyGuardConfig.idleMillis());
        return EarningPolicy.refusal(inNation, idle);
    }

    public static boolean mayEarn(ServerPlayer player) {
        return refusal(player) == EarningPolicy.Refusal.NONE;
    }

    /** Same check, telling the player why at most every few minutes. */
    public static boolean mayEarnWithNotice(ServerPlayer player) {
        EarningPolicy.Refusal refusal = refusal(player);
        if (refusal == EarningPolicy.Refusal.NONE) return true;
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUUID());
        if (last == null || now - last >= NOTICE_INTERVAL_MILLIS) {
            lastNotice.put(player.getUUID(), now);
            player.displayClientMessage(Component.translatable(refusal == EarningPolicy.Refusal.NO_NATION
                    ? "message.moveearth_addtional.jobs.no_income_no_nation"
                    : "message.moveearth_addtional.jobs.no_income_idle"), true);
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) return;
        float yaw = player.getYRot(), pitch = player.getXRot();
        Look old = looks.get(player.getUUID());
        if (old == null) {
            // Logging in is not input: stay idle until the view is turned once.
            looks.put(player.getUUID(), new Look(yaw, pitch, 0L));
        } else if (EarningPolicy.turned(old.yaw(), old.pitch(), yaw, pitch)) {
            looks.put(player.getUUID(), new Look(yaw, pitch, System.currentTimeMillis()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        looks.remove(event.getEntity().getUUID());
        lastNotice.remove(event.getEntity().getUUID());
    }
}
