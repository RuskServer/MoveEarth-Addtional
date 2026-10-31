package com.ruskserver.moveearth_addtional.compat.paintings;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.s2.nation.AllyAccessService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Applies {@link PaintingEditPolicy}: a refused player's click does not open the painting screen, and an edit
 * sent without the screen ({@code mixin.paintings.PaintingEditGuardMixin}) is dropped.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class PaintingEditGuard {
    private static final String NAMESPACE = "immersive_paintings";

    private PaintingEditGuard() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !NAMESPACE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType()).getNamespace())
                || mayEdit(player, event.getTarget())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.displayClientMessage(Component.translatable("message.moveearth_addtional.painting_edit.refused"), true);
    }

    public static boolean mayEdit(ServerPlayer player, Entity painting) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID actor = nations.nationIdFor(player.getUUID()).orElse(null);
        UUID guardian = guardian(player, painting.blockPosition());
        return PaintingEditPolicy.mayEdit(player.hasPermissions(2), guardian, actor,
                actor != null && guardian != null && nations.isAllied(guardian, actor));
    }

    /** The nation whose vehicle carries {@code pos}, or else whose land holds it. */
    private static UUID guardian(ServerPlayer player, BlockPos pos) {
        UUID vehicle = SableVehicleTopology.owningNation(player.serverLevel(), pos);
        return vehicle != null ? vehicle : AllyAccessService.hostAt(player, pos);
    }
}
