package com.ruskserver.moveearth_addtional.s2.technology;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Converts real player activity into personal guide progress after protection handlers accept it. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class TechnologyEvents {
    private TechnologyEvents() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()) return;
        NationTechnologySavedData.get(player.server).recordPlacement(player, event.getPlacedBlock(), event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getCrafting().isEmpty()
                || player.isCreative() || player.isSpectator()) return;
        NationTechnologySavedData.get(player.server).recordCraft(player, event.getCrafting());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (com.ruskserver.moveearth_addtional.s2.nation.NationSavedData.get(player.server)
                    .nationIdFor(player.getUUID()).isPresent()) {
                NationTechnologySavedData.get(player.server).recordObjective(player,
                        TechnologyDefinition.ObjectiveType.JOIN_OR_FOUND_NATION, null, 1L, player.blockPosition());
            }
            TechnologyViewService.sync(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()) return;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
        NationTechnologySavedData.get(player.server).recordObjective(player,
                TechnologyDefinition.ObjectiveType.OPERATE_BLOCK, id, 1L, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || event.getItemStack().isEmpty() || player.isCreative() || player.isSpectator()) return;
        NationTechnologySavedData.get(player.server).recordObjective(player,
                TechnologyDefinition.ObjectiveType.USE_EQUIPMENT,
                BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()), 1L, player.blockPosition());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()) return;
        NationTechnologySavedData.get(player.server).recordObjective(player,
                TechnologyDefinition.ObjectiveType.DAMAGE_TRAINING_TARGET,
                BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType()), 1L, event.getTarget().blockPosition());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ArmorStand)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()) return;
        NationTechnologySavedData.get(player.server).recordObjective(player,
                TechnologyDefinition.ObjectiveType.DAMAGE_TRAINING_TARGET,
                BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()), 1L,
                event.getEntity().blockPosition());
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 7
                || player.isCreative() || player.isSpectator()) return;
        recordEquipment(player, player.getMainHandItem());
        recordEquipment(player, player.getOffhandItem());
        player.getArmorSlots().forEach(stack -> recordEquipment(player, stack));
    }

    private static void recordEquipment(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        NationTechnologySavedData.get(player.server).recordObjective(player,
                TechnologyDefinition.ObjectiveType.USE_EQUIPMENT,
                BuiltInRegistries.ITEM.getKey(stack.getItem()), 1L, player.blockPosition());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            TechnologyViewService.sync(event.getPlayer());
        } else {
            event.getPlayerList().getPlayers().forEach(TechnologyViewService::sync);
        }
    }
}
