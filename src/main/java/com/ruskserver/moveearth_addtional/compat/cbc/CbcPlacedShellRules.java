package com.ruskserver.moveearth_addtional.compat.cbc;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Placed shells are ammunition, not wired or physics bombs. Ordinary fired projectiles are unaffected. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class CbcPlacedShellRules {
    private static final Map<ServerPlayer, Long> LAST_NOTICE = new WeakHashMap<>();
    private static final ClassValue<Boolean> SHELL_BLOCK = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if (current.getName().equals("rbasamoyai.createbigcannons.munitions.big_cannon.FuzedProjectileBlock")) return true;
            }
            return false;
        }
    };

    private CbcPlacedShellRules() { }

    @SubscribeEvent
    public static void onAttachWiredFuze(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) return;
        var item = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!item.getNamespace().equals("createbigcannons") || !item.getPath().equals("wired_fuze")) return;
        if (!SHELL_BLOCK.get(level.getBlockState(event.getPos()).getBlock().getClass())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        warn(player);
    }

    public static void warnNearby(ServerLevel level, BlockPos pos) {
        for (ServerPlayer player : level.players()) {
            if (SableVehicleTopology.distanceSquared(level, player, pos) <= 32.0D * 32.0D) warn(player);
        }
    }

    private static void warn(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_NOTICE.get(player);
        if (last != null && now >= last && now - last < 100) return;
        LAST_NOTICE.put(player, now);
        boolean japanese = player.clientInformation().language().toLowerCase(java.util.Locale.ROOT).startsWith("ja_");
        player.sendSystemMessage(MoveEarthMessage.warning(Component.translatableWithFallback(
                "message.moveearth_addtional.cbc.placed_shell_disabled", japanese
                        ? "設置した砲弾の起爆と有線信管は使用できません。砲弾は砲に装填して発射してください。"
                        : "Placed shells and wired fuzes cannot be detonated. Load shells into a cannon and fire them.")));
    }
}
