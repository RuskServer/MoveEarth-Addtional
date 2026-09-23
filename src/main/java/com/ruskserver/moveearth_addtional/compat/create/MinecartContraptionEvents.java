package com.ruskserver.moveearth_addtional.compat.create;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.UUID;

/** Makes cart assemblers managed national infrastructure instead of disposable raid tools. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class MinecartContraptionEvents {
    private MinecartContraptionEvents() {
    }

    @SubscribeEvent
    public static void onAssemblerPlaced(BlockEvent.EntityPlaceEvent event) {
        var id = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock());
        if (!"create".equals(id.getNamespace()) || !"cart_assembler".equals(id.getPath())
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) return;

        NationSavedData nations = NationSavedData.get(level.getServer());
        UUID actorNation = nations.nationIdFor(player.getUUID()).orElse(null);
        UUID controllingNation = TerritorySavedData.get(level.getServer())
                .controllingNation(level.getServer(), level.dimension().location(), event.getPos())
                .orElse(null);
        if (MinecartContraptionPolicy.canPlaceAssembler(actorNation, controllingNation,
                nations.can(player.getUUID(), S2Permission.MANAGE_TERRITORY))) return;

        event.setCanceled(true);
        player.sendSystemMessage(MoveEarthMessage.warning(Component.translatable(
                "message.moveearth_addtional.minecart.assembler_permission")));
    }
}
