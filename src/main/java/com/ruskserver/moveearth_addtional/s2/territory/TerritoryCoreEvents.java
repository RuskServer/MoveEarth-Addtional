package com.ruskserver.moveearth_addtional.s2.territory;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.block.ModBlocks;
import com.ruskserver.moveearth_addtional.block.entity.TerritoryCoreBlockEntity;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class TerritoryCoreEvents {
    private TerritoryCoreEvents() {
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(ModBlocks.TERRITORY_CORE.get())
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.hasPermissions(2)) return;
        if (NationSavedData.get(player.server).nationIdFor(player.getUUID()).isEmpty()) {
            event.setCanceled(true);
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.territory_core.foundation_required")));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.getState().is(ModBlocks.TERRITORY_CORE.get())
                || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof TerritoryCoreBlockEntity core)) {
            return;
        }
        NationSavedData data = NationSavedData.get(player.server);
        java.util.UUID playerNation = data.nationIdFor(player.getUUID()).orElse(null);
        if (event.getLevel() instanceof ServerLevel level
                && playerNation != null && !playerNation.equals(core.nationId())) {
            SiegeService.recordAttack(player, level, event.getPos(), false);
        }
        if (core.coreType() == TerritorySavedData.CoreType.CAPITAL) {
            event.setCanceled(true);
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.territory_core.capital_locked")));
            return;
        }
        boolean allowed = playerNation != null && playerNation.equals(core.nationId())
                && data.can(player.getUUID(), S2Permission.MANAGE_TERRITORY);
        if (allowed && SiegeSavedData.get(player.server).isCoreLocked(core.coreId())) {
            event.setCanceled(true);
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.territory_core.siege_locked")));
            return;
        }
        if (!allowed) {
            event.setCanceled(true);
            player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.territory_core.break_denied")));
        }
    }
}
