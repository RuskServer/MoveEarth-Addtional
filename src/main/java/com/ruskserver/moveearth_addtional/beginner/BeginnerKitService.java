package com.ruskserver.moveearth_addtional.beginner;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

public final class BeginnerKitService {
    public static final int MAX_PLAY_TIME_TICKS = 8 * 60 * 60 * 20;
    public static final int RESERVE_AMMO_COUNT = 8;
    public static final int FOOD_COUNT = 16;
    public static final int ATLAS_EMPTY_MAPS = 32;

    private static final String NBT_KEY_GRANTED = "MoveEarthBeginnerKitGranted";
    // Keep the automatic kit independent from optional external GunPacks. Kar98 and
    // its ammunition are part of TaCZ's built-in default pack on both client and server.
    private static final ResourceLocation STARTER_GUN_ID =
            ResourceLocation.fromNamespaceAndPath("tacz", "kar98");
    private static final ResourceLocation STARTER_AMMO_ID =
            ResourceLocation.fromNamespaceAndPath("tacz", "792x57");
    // Map Atlases is not a compile dependency: its atlas and the component that stores its blank maps
    // are looked up by id, and the blank maps are written through the component's own codec.
    private static final ResourceLocation ATLAS_ID =
            ResourceLocation.fromNamespaceAndPath("map_atlases", "atlas");
    private static final ResourceLocation ATLAS_EMPTY_MAPS_ID =
            ResourceLocation.fromNamespaceAndPath("map_atlases", "empty_maps");

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
        List<ItemStack> kit = new ArrayList<>();
        kit.add(enchantedArmor(player, new ItemStack(Items.IRON_HELMET), false));
        kit.add(enchantedArmor(player, new ItemStack(Items.IRON_CHESTPLATE), false));
        kit.add(enchantedArmor(player, new ItemStack(Items.IRON_LEGGINGS), false));
        kit.add(enchantedArmor(player, new ItemStack(Items.IRON_BOOTS), true));

        ItemStack gun = createStarterGun();
        ItemStack ammo = createStarterAmmo();
        if (!gun.isEmpty() && !ammo.isEmpty()) {
            kit.add(gun);
            kit.add(ammo);
        } else {
            Moveearth_addtional.LOGGER.warn(
                    "TaCZ built-in starter weapon is unavailable for {}; granting armor and food without it.",
                    player.getGameProfile().getName());
        }
        kit.add(new ItemStack(Items.COOKED_BEEF, FOOD_COUNT));
        ItemStack atlas = createStarterAtlas();
        if (!atlas.isEmpty()) kit.add(atlas);
        return List.copyOf(kit);
    }

    /**
     * An atlas already holding blank maps, so it starts mapping from the first step while it sits in the
     * hotbar. Nation territory is drawn on maps, and few players found the atlas on their own: crafting
     * one, and the compass every blank map costs, kept most of them without any map at all.
     */
    private static ItemStack createStarterAtlas() {
        if (!BuiltInRegistries.ITEM.containsKey(ATLAS_ID)) return ItemStack.EMPTY;
        ItemStack atlas = new ItemStack(BuiltInRegistries.ITEM.get(ATLAS_ID));
        DataComponentType<?> emptyMaps = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ATLAS_EMPTY_MAPS_ID);
        JsonObject counts = new JsonObject();
        counts.addProperty("vanilla", ATLAS_EMPTY_MAPS);
        if (emptyMaps == null || !setFromJson(atlas, emptyMaps, counts)) {
            Moveearth_addtional.LOGGER.warn("Map Atlases blank-map storage was not found; the starter kit leaves the atlas out.");
            return ItemStack.EMPTY;
        }
        return atlas;
    }

    private static <T> boolean setFromJson(ItemStack stack, DataComponentType<T> type, JsonObject value) {
        if (type.codec() == null) return false;
        var parsed = type.codec().parse(JsonOps.INSTANCE, value).result();
        parsed.ifPresent(component -> stack.set(type, component));
        return parsed.isPresent();
    }

    private static ItemStack createStarterGun() {
        if (TimelessAPI.getCommonGunIndex(STARTER_GUN_ID).isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(com.tacz.guns.init.ModItems.MODERN_KINETIC_GUN.get());
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return ItemStack.EMPTY;
        }

        gun.setGunId(stack, STARTER_GUN_ID);
        int magazineSize = TimelessAPI.getCommonGunIndex(STARTER_GUN_ID)
                .map(index -> index.getGunData().getAmmoAmount())
                .orElse(4);
        gun.setCurrentAmmoCount(stack, magazineSize);
        gun.setBulletInBarrel(stack, true);
        return stack;
    }

    private static ItemStack createStarterAmmo() {
        ItemStack stack = new ItemStack(com.tacz.guns.init.ModItems.AMMO.get(), RESERVE_AMMO_COUNT);
        IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
        if (ammo == null) {
            return ItemStack.EMPTY;
        }
        ammo.setAmmoId(stack, STARTER_AMMO_ID);
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
