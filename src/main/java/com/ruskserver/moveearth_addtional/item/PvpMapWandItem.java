package com.ruskserver.moveearth_addtional.item;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.pvp.map.PvpMapEditing;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * The PvP map area wand, handed out by {@code /pvp admin map wand <id>}: left-click a block for one corner of
 * the map's battle area and right-click for the other, as with WorldEdit. Only operators can use it.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class PvpMapWandItem extends Item {
    private static final String MAP_ID = "MoveEarthPvpMap";

    public PvpMapWandItem(Properties properties) {
        super(properties);
    }

    public static ItemStack forMap(String mapId) {
        ItemStack stack = new ItemStack(ModItems.PVP_MAP_WAND.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(MAP_ID, mapId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("PvPマップ範囲指定ワンド: " + mapId));
        return stack;
    }

    private static String mapId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(MAP_ID);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!player.hasPermissions(2)) return InteractionResult.FAIL;
        PvpMapEditing.setCorner(player, mapId(context.getItemInHand()), false, context.getClickedPos());
        return InteractionResult.CONSUME;
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof PvpMapWandItem)) return;
        event.setCanceled(true);
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
        PvpMapEditing.setCorner(player, mapId(stack), true, event.getPos());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7左クリック: 戦闘エリアの角1 / 右クリック: 角2"));
        tooltip.add(Component.literal("§7両方の角を置くと戦術マップを作り直します"));
    }
}
