package com.ruskserver.moveearth_addtional.oxygen;

import com.ruskserver.moveearth_addtional.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.UUID;

public class GasMaskItem extends ArmorItem {
    private static final String TAG_FILTER_TICKS = "FilterTicks";
    private static final String TAG_MAX_FILTER_TICKS = "MaxFilterTicks";
    private static final String TAG_MASK_ID = "MaskId";

    public GasMaskItem(Properties properties) {
        super(ArmorMaterials.IRON, Type.HELMET, properties);
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return ModSounds.GAS_MASK_EQUIP;
    }

    public static int getMaxFilterTicks() {
        return OxygenConfig.FILTER_BASE_DURATION_SECONDS.get() * 20;
    }

    public static int getFilterTicks(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains(TAG_FILTER_TICKS)) {
                return tag.getInt(TAG_FILTER_TICKS);
            }
        }
        return getMaxFilterTicks(); // デフォルトは満タン
    }

    public static void setFilterTicks(ItemStack stack, int ticks) {
        int max = getMaxFilterTicks();
        int clamped = Math.max(0, Math.min(ticks, max));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(TAG_FILTER_TICKS, clamped);
            tag.putInt(TAG_MAX_FILTER_TICKS, max);
        });
    }

    /**
     * The mask's own identity, written once when it is first worn. It lets the server
     * find a mask again after it left the head, to charge filter it used meanwhile.
     */
    static UUID getMaskId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(TAG_MASK_ID)) return null;
        CompoundTag tag = customData.copyTag();
        return tag.hasUUID(TAG_MASK_ID) ? tag.getUUID(TAG_MASK_ID) : null;
    }

    static UUID ensureMaskId(ItemStack stack) {
        UUID existing = getMaskId(stack);
        if (existing != null) return existing;
        UUID created = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID(TAG_MASK_ID, created));
        return created;
    }

    public static float getFilterPercentage(ItemStack stack) {
        int max = getMaxFilterTicks();
        if (max <= 0) return 0f;
        return (float) getFilterTicks(stack) / max;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true; // 常にフィルター残量バーを表示
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        float pct = getFilterPercentage(stack);
        return Math.round(13.0f * pct);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float pct = getFilterPercentage(stack);
        if (pct > 0.5f) {
            return 0x00FF66; // 緑（良好）
        } else if (pct > 0.2f) {
            return 0xFFAA00; // 黄橙（注意）
        } else {
            return 0xFF2222; // 赤（危険）
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        int current = getFilterTicks(stack);
        int max = getMaxFilterTicks();
        int pct = Math.round(getFilterPercentage(stack) * 100.0f);

        ChatFormatting color = pct > 50 ? ChatFormatting.GREEN : (pct > 20 ? ChatFormatting.YELLOW : ChatFormatting.RED);

        tooltipComponents.add(Component.translatable("tooltip.moveearth_addtional.gas_mask.filter", pct, current / 20)
                .withStyle(color));
        tooltipComponents.add(Component.translatable("tooltip.moveearth_addtional.gas_mask.desc")
                .withStyle(ChatFormatting.GRAY));

        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
