package com.ruskserver.moveearth_addtional.beginner;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

public final class BeginnerKitService {
    public static final int MAX_PLAY_TIME_TICKS = 8 * 60 * 60 * 20;
    public static final int RESERVE_AMMO_COUNT = 8;

    private static final String NBT_KEY_GRANTED = "MoveEarthBeginnerKitGranted";
    private static final ResourceLocation TYPE_38_ID =
            ResourceLocation.fromNamespaceAndPath("cib", "type38");
    private static final ResourceLocation TYPE_38_AMMO_ID =
            ResourceLocation.fromNamespaceAndPath("cib", "65x50");

    private BeginnerKitService() {
    }

    public static boolean hasReceived(ServerPlayer player) {
        return persistedData(player).getBoolean(NBT_KEY_GRANTED);
    }

    public static boolean isWithinPlayTimeLimit(ServerPlayer player) {
        return playTimeTicks(player) < MAX_PLAY_TIME_TICKS;
    }

    public static boolean isEligible(ServerPlayer player) {
        return isWithinPlayTimeLimit(player) && !hasReceived(player);
    }

    public static boolean isFirstLogin(ServerPlayer player) {
        return playTimeTicks(player) <= 20
                && player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) == 0;
    }

    public static int playTimeTicks(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
    }

    public static GrantResult grant(ServerPlayer player, boolean force, String reason) {
        if (!force && hasReceived(player)) {
            return GrantResult.ALREADY_RECEIVED;
        }
        if (!force && !isWithinPlayTimeLimit(player)) {
            return GrantResult.PLAY_TIME_EXCEEDED;
        }

        List<ItemStack> kit = createKit(player);
        if (kit.isEmpty()) {
            Moveearth_addtional.LOGGER.error(
                    "Could not create beginner kit for {}: CIB Type 38 or its ammo is unavailable.",
                    player.getGameProfile().getName());
            return GrantResult.CONTENT_UNAVAILABLE;
        }

        persistedData(player).putBoolean(NBT_KEY_GRANTED, true);
        kit.forEach(stack -> giveOrDrop(player, stack));
        Moveearth_addtional.LOGGER.info(
                "Granted beginner kit to {} ({}) via {} at {} play-time ticks.",
                player.getGameProfile().getName(), player.getUUID(), reason, playTimeTicks(player));
        return GrantResult.GRANTED;
    }

    public static void reset(ServerPlayer player) {
        persistedData(player).remove(NBT_KEY_GRANTED);
        Moveearth_addtional.LOGGER.info(
                "Reset beginner-kit claim state for {} ({}).",
                player.getGameProfile().getName(), player.getUUID());
    }

    private static List<ItemStack> createKit(ServerPlayer player) {
        ItemStack gun = createType38();
        ItemStack ammo = createType38Ammo();
        if (gun.isEmpty() || ammo.isEmpty()) {
            return List.of();
        }

        return List.of(
                enchantedArmor(player, new ItemStack(Items.IRON_HELMET), false),
                enchantedArmor(player, new ItemStack(Items.IRON_CHESTPLATE), false),
                enchantedArmor(player, new ItemStack(Items.IRON_LEGGINGS), false),
                enchantedArmor(player, new ItemStack(Items.IRON_BOOTS), true),
                gun,
                ammo
        );
    }

    private static ItemStack createType38() {
        if (TimelessAPI.getCommonGunIndex(TYPE_38_ID).isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(com.tacz.guns.init.ModItems.MODERN_KINETIC_GUN.get());
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return ItemStack.EMPTY;
        }

        gun.setGunId(stack, TYPE_38_ID);
        int magazineSize = TimelessAPI.getCommonGunIndex(TYPE_38_ID)
                .map(index -> index.getGunData().getAmmoAmount())
                .orElse(4);
        gun.setCurrentAmmoCount(stack, magazineSize);
        gun.setBulletInBarrel(stack, true);
        return stack;
    }

    private static ItemStack createType38Ammo() {
        ItemStack stack = new ItemStack(com.tacz.guns.init.ModItems.AMMO.get(), RESERVE_AMMO_COUNT);
        IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
        if (ammo == null) {
            return ItemStack.EMPTY;
        }
        ammo.setAmmoId(stack, TYPE_38_AMMO_ID);
        return stack;
    }

    private static ItemStack enchantedArmor(ServerPlayer player, ItemStack stack, boolean boots) {
        var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        stack.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 2);
        stack.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 1);
        if (boots) {
            stack.enchant(enchantments.getOrThrow(Enchantments.FEATHER_FALLING), 2);
        }
        return stack;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static CompoundTag persistedData(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public enum GrantResult {
        GRANTED,
        ALREADY_RECEIVED,
        PLAY_TIME_EXCEEDED,
        CONTENT_UNAVAILABLE
    }
}
