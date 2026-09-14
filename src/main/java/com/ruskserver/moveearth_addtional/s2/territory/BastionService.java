package com.ruskserver.moveearth_addtional.s2.territory;

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

    private BastionService() {
    }

    static boolean isRestricted(ServerPlayer player, ServerLevel level, BlockPos pos) {
        NationSavedData nations = NationSavedData.get(player.server);
        var controllingNation = TerritorySavedData.get(player.server)
                .controllingNation(level.getServer(), level.dimension().location(), pos);
        var actorNation = nations.nationIdFor(player.getUUID());
        boolean allied = controllingNation.isPresent() && actorNation.isPresent()
                && nations.isAllied(controllingNation.get(), actorNation.get());
        if (controllingNation.isPresent()
                && !NationUpkeepService.penalty(player.server, controllingNation.get()).bastionEnabled()) {
            return false;
        }
        return BastionPolicy.isRestricted(
                controllingNation, actorNation, allied,
                nations.can(player.getUUID(), S2Permission.BASTION_ACCESS),
                player.hasPermissions(2) || player.isCreative() || player.isSpectator());
    }

    static void deny(ServerPlayer player, Action action) {
        long now = player.server.overworld().getGameTime();
        NoticeKey key = new NoticeKey(player.getUUID(), action);
        Long previous = LAST_NOTICE.get(key);
        if (previous != null && now - previous < NOTICE_COOLDOWN_TICKS) return;
        LAST_NOTICE.put(key, now);
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(action.messageKey)));
    }

    static void clear() {
        LAST_NOTICE.clear();
    }

    enum Action {
        BLOCK_PLACE("message.moveearth_addtional.bastion.block_place"),
        FLUID_PLACE("message.moveearth_addtional.bastion.fluid_place"),
        VEHICLE_PLACE("message.moveearth_addtional.bastion.vehicle_place"),
        ENDER_PEARL("message.moveearth_addtional.bastion.ender_pearl"),
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
