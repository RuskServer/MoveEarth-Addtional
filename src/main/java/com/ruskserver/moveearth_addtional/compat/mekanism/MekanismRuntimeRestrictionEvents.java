package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative placement, interaction, combat and equipment ban for removed Mekanism content. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class MekanismRuntimeRestrictionEvents {
    private static final long MESSAGE_COOLDOWN_TICKS = 20L;
    private static final Map<UUID, Long> LAST_MESSAGE_TICK = new HashMap<>();

    private MekanismRuntimeRestrictionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) return;
        if (!isRestricted(event.getItemStack()) && !isRestricted(
                event.getLevel().getBlockState(event.getPos()))) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notifyDenied(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !isRestricted(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notifyDenied(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.isCanceled() || !isRestricted(event.getItemStack())) return;
        event.setCanceled(true);
        notifyDenied(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.isCanceled() || !isRestricted(event.getEntity().getMainHandItem())) return;
        event.setCanceled(true);
        notifyDenied(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || !isRestricted(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notifyDenied(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.isCanceled() || !isRestricted(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        notifyDenied(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!isRestricted(event.getPlacedBlock())) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) notifyDenied(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEquipmentChanged(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isArmorSlot(event.getSlot())
                || !isRestricted(event.getTo())) return;

        ItemStack returning = event.getTo().copy();
        player.setItemSlot(event.getSlot(), ItemStack.EMPTY);
        player.getInventory().add(returning);
        if (!returning.isEmpty()) player.drop(returning, false);
        notifyDenied(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_MESSAGE_TICK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_MESSAGE_TICK.clear();
    }

    private static boolean isRestricted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return MekanismRuntimeRestrictionPolicy.isRestrictedItem(id.getNamespace(), id.getPath());
    }

    private static boolean isRestricted(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return MekanismRuntimeRestrictionPolicy.isRestrictedBlock(id.getNamespace(), id.getPath());
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private static void notifyDenied(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        long gameTime = serverPlayer.serverLevel().getGameTime();
        Long previous = LAST_MESSAGE_TICK.put(serverPlayer.getUUID(), gameTime);
        if (previous == null || gameTime < previous || gameTime - previous >= MESSAGE_COOLDOWN_TICKS) {
            serverPlayer.sendSystemMessage(MoveEarthMessage.error(Component.translatable(
                    "message.moveearth_addtional.mekanism.content_disabled")));
        }
    }
}
