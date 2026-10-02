package com.ruskserver.moveearth_addtional.s2.reinforcement;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.item.ModItems;
import com.ruskserver.moveearth_addtional.network.s2c.vehicle.S2C_WeldingTargetPacket;
import com.ruskserver.moveearth_addtional.s2.siege.CoreSabotageService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves the aimed block on the server for welder users (and for anyone near an active sabotage charge),
 * and tells the client what that block means: whether it may be reinforced, and which sabotage prompt applies.
 * Packets are sent only when the result changes.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class WeldingTargetSync {
    private static final Map<UUID, S2C_WeldingTargetPacket> LAST_SENT = new HashMap<>();

    private WeldingTargetSync() { }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            S2C_WeldingTargetPacket packet = resolve(player);
            if (packet.equals(LAST_SENT.getOrDefault(player.getUUID(), S2C_WeldingTargetPacket.NONE))) continue;
            LAST_SENT.put(player.getUUID(), packet);
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        LAST_SENT.clear();
    }

    private static S2C_WeldingTargetPacket resolve(ServerPlayer player) {
        if (player.isSpectator()) return S2C_WeldingTargetPacket.NONE;
        boolean welding = player.getMainHandItem().is(ModItems.WELDING_TOOL.get());
        if (!welding && !CoreSabotageService.nearCharge(player)) return S2C_WeldingTargetPacket.NONE;
        BlockPos target = target(player);
        if (target == null) return S2C_WeldingTargetPacket.NONE;
        return new S2C_WeldingTargetPacket(target, welding && ReinforcementService.reinforceableBy(player, target),
                CoreSabotageService.prompt(player, target), welding ? reservationMinutes(player, target)
                : com.ruskserver.moveearth_addtional.s2.territory.ConfiguringReservationPolicy.NO_RESERVATION);
    }

    private static int reservationMinutes(ServerPlayer player, BlockPos target) {
        var nationId = com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(player.server)
                .nationIdFor(player.getUUID()).orElse(null);
        return com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData.get(player.server)
                .reservationMinutesLeft(player.server, nationId, player.level().dimension().location(), target);
    }

    private static BlockPos target(ServerPlayer player) {
        // Slightly past the client's pick range so both sides agree on the aimed block at the edge.
        double reach = player.blockInteractionRange() + 0.5D;
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().immutable() : null;
    }
}
