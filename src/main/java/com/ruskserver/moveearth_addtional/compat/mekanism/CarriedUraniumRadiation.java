package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.mekanism.CleanWaterRule.Water;
import com.ruskserver.moveearth_addtional.config.MekanismBalanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Uranium in a player's inventory irradiates them, and clean water washes the
 * dose off ({@link CarriedUraniumPolicy}, {@link CleanWaterRule}). Both run once
 * a second per player, on the server only.
 *
 * <p>The inventory includes armor, the offhand, and the contents of shulker
 * boxes and bundles carried in it; chests and other placed storage are safe.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class CarriedUraniumRadiation {
    private static final String KEY = "message.moveearth_addtional.radiation.";
    private static final long CARRY_NOTICE_TICKS = 30L * 20L;
    private static final long DIRTY_WATER_NOTICE_TICKS = 30L * 20L;
    /** Contaminated water is only pointed out once a dose is worth washing. */
    private static final double DIRTY_WATER_NOTICE_DOSE = 1.0E-5D;
    private static final int GOLD = 0xFFB454;
    private static final int AQUA = 0x6FD3F0;

    private static final Map<ServerPlayer, Notices> NOTICES = new WeakHashMap<>();

    private CarriedUraniumRadiation() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.isCreative()) return;
        if ((player.tickCount + player.getId()) % 20 != 0 || !player.isAlive()) return;
        if (!MekanismRadiationBridge.available()) return;

        long now = player.serverLevel().getGameTime();
        Notices notices = NOTICES.computeIfAbsent(player, ignored -> new Notices());
        boolean spoke = wash(player, notices, now);
        com.ruskserver.moveearth_addtional.s2.presence.HiddenAdvancementService.dose(player, MekanismRadiationBridge.dose(player));

        double perSecond = CarriedUraniumPolicy.dosePerSecond(
                carriedIngots(player), MekanismBalanceConfig.carriedUraniumDosePerHour());
        if (perSecond <= 0.0D) return;
        double before = MekanismRadiationBridge.dose(player);
        MekanismRadiationBridge.radiate(player, perSecond);
        double after = MekanismRadiationBridge.dose(player);
        // Shielding that blocks it all leaves the dose unchanged: nothing to warn about.
        if (!spoke && after > before && now >= notices.nextCarry) {
            notices.nextCarry = now + CARRY_NOTICE_TICKS;
            player.displayClientMessage(styled(Component.translatableWithFallback(KEY + "carrying",
                    "ウランを持っているため被曝しています（被曝量 %s）。防護服で防げます",
                    CarriedUraniumPolicy.formatDose(after)), GOLD), true);
        }
    }

    /** Washes the dose in clean water. Returns whether a message was shown. */
    private static boolean wash(ServerPlayer player, Notices notices, long now) {
        double kept = MekanismBalanceConfig.cleanWaterKeptPerSecond();
        if (kept >= 1.0D || !player.isInWater()) return false;
        double dose = MekanismRadiationBridge.dose(player);
        if (dose < CarriedUraniumPolicy.NEGLIGIBLE_DOSE) {
            notices.washPeak = 0.0D;
            return false;
        }
        if (inCleanWater(player)) {
            notices.washPeak = Math.max(notices.washPeak, dose);
            double left = CarriedUraniumPolicy.washed(dose, kept);
            MekanismRadiationBridge.setDose(player, left);
            if (left <= 0.0D) {
                com.ruskserver.moveearth_addtional.s2.presence.HiddenAdvancementService.washedClean(player, notices.washPeak);
                notices.washPeak = 0.0D;
            }
            player.displayClientMessage(left <= 0.0D
                    ? styled(Component.translatableWithFallback(KEY + "washed", "除染が完了しました"), AQUA)
                    : styled(Component.translatableWithFallback(KEY + "washing",
                            "きれいな水で除染しています（被曝量 %s）", CarriedUraniumPolicy.formatDose(left)), AQUA),
                    true);
            return true;
        }
        if (dose >= DIRTY_WATER_NOTICE_DOSE && now >= notices.nextDirtyWater) {
            notices.nextDirtyWater = now + DIRTY_WATER_NOTICE_TICKS;
            player.displayClientMessage(styled(Component.translatableWithFallback(KEY + "dirty_water",
                    "この水は汚染されていて除染に使えません。バケツで置いた水に入ってください"), GOLD), true);
            return true;
        }
        return false;
    }

    private static boolean inCleanWater(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        CleanWaterSavedData placed = CleanWaterSavedData.get(level);
        CleanWaterRule.Lookup lookup = (x, y, z) -> water(level, placed, new BlockPos(x, y, z));
        BlockPos feet = player.blockPosition();
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        return CleanWaterRule.clean(lookup, feet.getX(), feet.getY(), feet.getZ())
                || !eyes.equals(feet) && CleanWaterRule.clean(lookup, eyes.getX(), eyes.getY(), eyes.getZ());
    }

    private static Water water(ServerLevel level, CleanWaterSavedData placed, BlockPos pos) {
        if (!level.isLoaded(pos)) return Water.NONE;
        FluidState fluid = level.getFluidState(pos);
        boolean marked = placed.contains(pos);
        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) {
            // A placed source that was scooped up or replaced is no longer clean water.
            if (marked) placed.forget(pos);
            return fluid.is(FluidTags.WATER) ? Water.FLOWING : Water.NONE;
        }
        return marked ? Water.PLACED_SOURCE : Water.OTHER_SOURCE;
    }

    /** Uranium the player carries, in ingots. */
    static double carriedIngots(ServerPlayer player) {
        double ingots = 0.0D;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ingots += ingots(inventory.getItem(slot), true);
        }
        return ingots;
    }

    private static double ingots(ItemStack stack, boolean openContainers) {
        if (stack.isEmpty()) return 0.0D;
        double ingots = stack.getCount()
                * CarriedUraniumPolicy.ingotsPerItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (!openContainers) return ingots;
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStack inner : container.nonEmptyItems()) ingots += ingots(inner, false);
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack inner : bundle.items()) ingots += ingots(inner, false);
        }
        return ingots;
    }

    private static MutableComponent styled(MutableComponent message, int rgb) {
        return message.withColor(rgb);
    }

    private static final class Notices {
        long nextCarry;
        long nextDirtyWater;
        /** The highest dose since this wash began, for 「風呂上がり」. */
        double washPeak;
    }
}
