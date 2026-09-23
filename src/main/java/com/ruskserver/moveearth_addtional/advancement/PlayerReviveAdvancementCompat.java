package com.ruskserver.moveearth_addtional.advancement;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

/** Optional, reflection-only PlayerRevive event bridge. */
public final class PlayerReviveAdvancementCompat {
    private static boolean registered;

    private PlayerReviveAdvancementCompat() { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerIfPresent() {
        if (registered) return;
        registered = true;
        try {
            Class<? extends Event> type = (Class<? extends Event>) Class.forName(
                    "team.creative.playerrevive.api.event.ReviveCompleteEvent");
            Method getEntity = type.getMethod("getEntity");
            Method getTarget = type.getMethod("getTarget");
            Consumer listener = event -> handle(event, getEntity, getTarget);
            NeoForge.EVENT_BUS.addListener((Class) type, listener);
        } catch (ReflectiveOperationException | LinkageError exception) {
            Moveearth_addtional.LOGGER.info("PlayerRevive advancement bridge is unavailable");
        }
    }

    private static void handle(Object event, Method getEntity, Method getTarget) {
        try {
            if (!(getEntity.invoke(event) instanceof ServerPlayer helper)
                    || !(getTarget.invoke(event) instanceof ServerPlayer target)) return;
            NationSavedData nations = NationSavedData.get(helper.server);
            UUID helperNation = nations.nationIdFor(helper.getUUID()).orElse(null);
            UUID targetNation = nations.nationIdFor(target.getUUID()).orElse(null);
            if (helperNation != null && helperNation.equals(targetNation)
                    && SiegeSavedData.get(helper.server).isNationLocked(helperNation)) {
                ModCriteria.trigger(helper, ModCriteria.ALLY_REVIVED);
            }
        } catch (ReflectiveOperationException exception) {
            Moveearth_addtional.LOGGER.debug("Could not read PlayerRevive completion event", exception);
        }
    }
}
