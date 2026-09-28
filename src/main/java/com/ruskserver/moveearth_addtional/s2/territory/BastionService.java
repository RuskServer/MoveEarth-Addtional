package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.s2.nation.AllyPermission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class BastionService {
    private static final long NOTICE_COOLDOWN_TICKS = 20L;
    private static final Map<NoticeKey, Long> LAST_NOTICE = new HashMap<>();
    /** Players whose most recent Bastion check was refused only for lack of the host's ally grant. */
    private static final java.util.Set<UUID> LAST_REFUSED_AS_ALLY = new java.util.HashSet<>();

    private BastionService() {
    }

    static boolean isRestricted(ServerPlayer player, ServerLevel level, BlockPos pos) {
        NationSavedData nations = NationSavedData.get(player.server);
        var controllingNation = TerritorySavedData.get(player.server)
                .controllingNation(level.getServer(), level.dimension().location(), pos);
        var actorNation = nations.nationIdFor(player.getUUID());
        boolean allied = controllingNation.isPresent() && actorNation.isPresent()
                && nations.isAllied(controllingNation.get(), actorNation.get());
        // An ally is only accepted where the host nation granted it BUILD; its own roles grant nothing here.
        boolean acceptedAlly = allied && nations.allyPermits(
                controllingNation.get(), player.getUUID(), AllyPermission.BUILD);
        LAST_REFUSED_AS_ALLY.remove(player.getUUID());
        if (controllingNation.isPresent()
                && !NationUpkeepService.penalty(player.server, controllingNation.get()).bastionEnabled()) {
            return false;
        }
        boolean restricted = BastionPolicy.isRestricted(
                controllingNation, actorNation, acceptedAlly,
                nations.can(player.getUUID(), S2Permission.BASTION_ACCESS),
                player.hasPermissions(2) || player.isCreative() || player.isSpectator());
        if (restricted && allied && !acceptedAlly) LAST_REFUSED_AS_ALLY.add(player.getUUID());
        return restricted;
    }

    static void deny(ServerPlayer player, Action action) {
        long now = player.server.overworld().getGameTime();
        NoticeKey key = new NoticeKey(player.getUUID(), action);
        Long previous = LAST_NOTICE.get(key);
        if (previous != null && now - previous < NOTICE_COOLDOWN_TICKS) return;
        LAST_NOTICE.put(key, now);
        boolean allyRefusal = LAST_REFUSED_AS_ALLY.contains(player.getUUID())
                && action != Action.DISMOUNT && action != Action.NO_SAFE_RETURN;
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(allyRefusal
                ? "message.moveearth_addtional.ally_access.build_denied" : action.messageKey)));
    }

    static void clear() {
        LAST_NOTICE.clear();
        LAST_REFUSED_AS_ALLY.clear();
    }

    enum Action {
        BLOCK_PLACE("message.moveearth_addtional.bastion.block_place"),
        FLUID_PLACE("message.moveearth_addtional.bastion.fluid_place"),
        VEHICLE_PLACE("message.moveearth_addtional.bastion.vehicle_place"),
        ENDER_PEARL("message.moveearth_addtional.bastion.ender_pearl"),
        GLUE("message.moveearth_addtional.bastion.glue"),
        DISMOUNT("message.moveearth_addtional.bastion.dismount"),
        NO_SAFE_RETURN("message.moveearth_addtional.bastion.no_safe_return");

        private final String messageKey;

        Action(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private record NoticeKey(UUID playerId, Action action) {
    }
}
