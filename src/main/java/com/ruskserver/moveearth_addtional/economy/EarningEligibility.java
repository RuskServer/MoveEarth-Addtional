package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.config.EconomyGuardConfig;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Jobs income and event points require nation membership and a player who is actually at the
 * keyboard: turning the view, or deliberate GUI work such as taking crafting, furnace and gun smith
 * table results. The analytics AFK flag is not used: it counts kills and Jobs XP themselves as
 * activity, so an autoclicker mob farm would look active. Container slot clicks are not counted:
 * NeoForge has no server-side hook for them short of a packet mixin.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class EarningEligibility {
    private static final long NOTICE_INTERVAL_MILLIS = 180_000L;
    private static final Map<UUID, Look> looks = new HashMap<>();
    private static final Map<UUID, Long> lastNotice = new HashMap<>();

    private record Look(float yaw, float pitch, long lastInputMillis) { }

    private EarningEligibility() { }

    public static EarningPolicy.Refusal refusal(ServerPlayer player) {
        boolean inNation = NationSavedData.get(player.server).nationIdFor(player.getUUID()).isPresent();
        return EarningPolicy.refusal(inNation, idle(player));
    }

    /** No input within {@code idleSeconds}; also decides whether active play time accrues. */
    public static boolean idle(ServerPlayer player) {
        Look look = looks.get(player.getUUID());
        return EarningPolicy.idle(look == null ? 0L : look.lastInputMillis(),
                System.currentTimeMillis(), EconomyGuardConfig.idleMillis());
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

    /**
     * Records a server-visible action as input when {@link EarningPolicy#countsAsInput} says so.
     * Fake players (Create deployers and the like) never count.
     */
    public static void noteActivity(ServerPlayer player, EarningPolicy.Activity activity) {
        if (player == null || player instanceof FakePlayer || !EarningPolicy.countsAsInput(activity)) return;
        long now = System.currentTimeMillis();
        Look old = looks.get(player.getUUID());
        looks.put(player.getUUID(), old == null
                ? new Look(player.getYRot(), player.getXRot(), now)
                : new Look(old.yaw(), old.pitch(), EarningPolicy.lastInputAfter(old.lastInputMillis(), activity, now)));
    }

    // HIGHEST: the craft that pays Jobs XP (JobEvents listens at LOWEST) already counts as input.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.getCrafting().isEmpty())
            noteActivity(player, EarningPolicy.Activity.CRAFT);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.getSmelting().isEmpty())
            noteActivity(player, EarningPolicy.Activity.SMELT);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
                || player.tickCount % EarningPolicy.ACTIVE_STEP_TICKS != 0) return;
        float yaw = player.getYRot(), pitch = player.getXRot();
        Look old = looks.get(player.getUUID());
        if (old == null) {
            // Logging in is not input: stay idle until the view is turned once.
            looks.put(player.getUUID(), new Look(yaw, pitch, 0L));
        } else if (EarningPolicy.turned(old.yaw(), old.pitch(), yaw, pitch)) {
            looks.put(player.getUUID(), new Look(yaw, pitch, EarningPolicy.lastInputAfter(
                    old.lastInputMillis(), EarningPolicy.Activity.TURN_VIEW, System.currentTimeMillis())));
        }
        // One second online, credited as active play time unless idle (new-account transfer limit).
        ActivePlayTimeSavedData.get(player.server).tick(player.getUUID(), idle(player));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        looks.remove(event.getEntity().getUUID());
        lastNotice.remove(event.getEntity().getUUID());
    }
}
