package com.ruskserver.moveearth_addtional.s2.reinforcement;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Prevents wrench rotation, pickup and configuration from desynchronizing reinforced blocks. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class ReinforcementWrenchEvents {
    private static final long WARNING_COOLDOWN_TICKS = 20L;
    private static final Map<UUID, Long> LAST_WARNING_TICK = new HashMap<>();

    private ReinforcementWrenchEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());
        boolean wrench = ReinforcementWrenchPolicy.isWrench(
                held.is(Tags.Items.TOOLS_WRENCH), itemId.getNamespace(), itemId.getPath());
        boolean reinforced = ReinforcementSavedData.get(level).get(event.getPos()).isPresent();
        if (!ReinforcementWrenchPolicy.shouldBlock(reinforced, wrench)) return;

        // Cancel before Create's own HIGH-priority wrench bridge. This covers rotation,
        // sneak-pickup and IWrenchable configuration without treating the wrench as damage.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notifyLocked(player, level.getGameTime());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_WARNING_TICK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_WARNING_TICK.clear();
    }

    private static void notifyLocked(ServerPlayer player, long gameTime) {
        Long previous = LAST_WARNING_TICK.put(player.getUUID(), gameTime);
        if (previous == null || gameTime - previous >= WARNING_COOLDOWN_TICKS) {
            player.displayClientMessage(Component.translatable(
                    "message.moveearth_addtional.reinforcement.wrench_locked"), true);
        }
    }
}
