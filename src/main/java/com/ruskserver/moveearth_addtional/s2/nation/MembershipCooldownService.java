package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Player-facing side of {@link MembershipCooldownPolicy}. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
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

    /**
     * Tells a kicked player who tried to found a nation that they may not while still bound to their
     * former nation's truces and alliances, and for how much longer.
     */
    public static void notifyFoundingBound(ServerPlayer player) {
        NationSavedData nations = NationSavedData.get(player.server);
        long now = OpenTimeService.now(player.server);
        long remaining = nations.formerNationBindingRemaining(player.getUUID(), now);
        if (remaining <= 0L) return;
        String nationName = nations.cooldownFormerNation(player.getUUID(), now)
                .flatMap(nations::nation).map(NationSavedData.Nation::name).orElse("?");
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                "message.moveearth_addtional.nation.former_nation_bound_founding", nationName, duration(remaining))));
    }

    /** Tells the owner who just disbanded their nation how long they may not join or found one. */
    public static void notifyDisbandedOwner(ServerPlayer owner, String nationName) {
        if (owner == null) return;
        long remaining = NationSavedData.get(owner.server)
                .membershipCooldownRemaining(owner.getUUID(), OpenTimeService.now(owner.server));
        owner.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.nation.disbanded_owner", nationName, duration(remaining))));
    }

    /**
     * Tells a member that their nation was disbanded, and what (if anything) still restricts them:
     * a disbandment itself locks only the owner, but a lock or binding from an earlier loss still runs.
     * Offline members are told at their next login instead.
     */
    public static void notifyDisbandedMember(net.minecraft.server.MinecraftServer server,
                                             java.util.UUID memberId, String nationName) {
        ServerPlayer online = server.getPlayerList().getPlayer(memberId);
        if (online == null) {
            DisbandNoticeSavedData.get(server).add(memberId, nationName);
            return;
        }
        online.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.nation.disbanded", nationName)));
        notifyRestriction(online);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DisbandNoticeSavedData.get(player.server).take(player.getUUID()).ifPresent(nationName -> {
            // Rejoined meanwhile (e.g. by an admin): the notice is still news, the restriction is not.
            player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                    "message.moveearth_addtional.nation.disbanded_while_away", nationName)));
            if (NationSavedData.get(player.server).nationIdFor(player.getUUID()).isEmpty()) notifyRestriction(player);
        });
    }

    /** What a nationless player may do now: join and found freely, or what still holds them and how long. */
    private static void notifyRestriction(ServerPlayer player) {
        NationSavedData nations = NationSavedData.get(player.server);
        long now = OpenTimeService.now(player.server);
        long lock = nations.membershipCooldownRemaining(player.getUUID(), now);
        long binding = nations.formerNationBindingRemaining(player.getUUID(), now);
        switch (MembershipCooldownPolicy.restriction(lock, binding)) {
            case JOIN_LOCKED -> player.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                    "message.moveearth_addtional.nation.membership_cooldown", duration(lock))));
            case FOUNDING_BOUND -> {
                String formerName = nations.cooldownFormerNation(player.getUUID(), now)
                        .flatMap(nations::nation).map(NationSavedData.Nation::name).orElse("?");
                player.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                        "message.moveearth_addtional.nation.former_nation_bound_founding", formerName,
                        duration(binding))));
            }
            case NONE -> player.sendSystemMessage(MoveEarthMessage.info(Component.translatable(
                    "message.moveearth_addtional.nation.free_to_join")));
        }
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
