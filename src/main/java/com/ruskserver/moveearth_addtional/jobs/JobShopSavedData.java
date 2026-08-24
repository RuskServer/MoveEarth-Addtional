package com.ruskserver.moveearth_addtional.jobs;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** World-persistent administrator-defined shop catalog and per-player purchase counts. */
public final class JobShopSavedData extends SavedData {
    public static final int MAX_PRODUCTS = 128;
    private static final int DATA_VERSION = 1;
    private final Map<UUID, Product> products = new LinkedHashMap<>();

    public Optional<UUID> addProduct(ItemStack heldStack, int price, int purchaseLimit) {
        if (heldStack.isEmpty() || products.size() >= MAX_PRODUCTS
                || !JobShopRules.validConfiguration(price, purchaseLimit)) {
            return Optional.empty();
        }
        UUID id = UUID.randomUUID();
        products.put(id, new Product(heldStack.copy(), price, purchaseLimit, true));
        setDirty();
        return Optional.of(id);
    }

    public boolean updateProduct(UUID productId, int price, int purchaseLimit) {
        Product product = products.get(productId);
        if (product == null || !JobShopRules.validConfiguration(price, purchaseLimit)) {
            return false;
        }
        product.price = price;
        product.purchaseLimit = purchaseLimit;
        setDirty();
        return true;
    }

    public boolean toggleProduct(UUID productId) {
        Product product = products.get(productId);
        if (product == null) return false;
        product.enabled = !product.enabled;
        setDirty();
        return true;
    }

    public boolean removeProduct(UUID productId) {
        if (products.remove(productId) == null) return false;
        setDirty();
        return true;
    }

    public Optional<ProductSnapshot> product(UUID productId, UUID playerId) {
        Product product = products.get(productId);
        return product == null ? Optional.empty() : Optional.of(snapshot(productId, product, playerId));
    }

    public List<ProductSnapshot> products(UUID playerId, boolean includeDisabled) {
        List<ProductSnapshot> result = new ArrayList<>();
        products.forEach((id, product) -> {
            if (includeDisabled || product.enabled) {
                result.add(snapshot(id, product, playerId));
            }
        });
        return List.copyOf(result);
    }

    public void recordPurchase(UUID productId, UUID playerId) {
        Product product = products.get(productId);
        if (product == null) return;
        int current = product.purchases.getOrDefault(playerId, 0);
        product.purchases.put(playerId, current == Integer.MAX_VALUE ? current : current + 1);
        setDirty();
    }

    private static ProductSnapshot snapshot(UUID id, Product product, UUID playerId) {
        return new ProductSnapshot(id, product.template.copy(), product.price, product.purchaseLimit,
                product.purchases.getOrDefault(playerId, 0), product.enabled);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", DATA_VERSION);
        ListTag productTags = new ListTag();
        products.forEach((id, product) -> {
            CompoundTag productTag = new CompoundTag();
            productTag.putString("Id", id.toString());
            productTag.put("Item", product.template.save(registries));
            productTag.putInt("Price", product.price);
            productTag.putInt("PurchaseLimit", product.purchaseLimit);
            productTag.putBoolean("Enabled", product.enabled);
            CompoundTag purchases = new CompoundTag();
            product.purchases.forEach((playerId, count) -> purchases.putInt(playerId.toString(), count));
            productTag.put("Purchases", purchases);
            productTags.add(productTag);
        });
        tag.put("Products", productTags);
        return tag;
    }

    public static JobShopSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        JobShopSavedData data = new JobShopSavedData();
        ListTag products = tag.getList("Products", Tag.TAG_COMPOUND);
        for (int i = 0; i < products.size() && data.products.size() < MAX_PRODUCTS; i++) {
            CompoundTag productTag = products.getCompound(i);
            try {
                UUID id = UUID.fromString(productTag.getString("Id"));
                ItemStack template = ItemStack.parse(registries, productTag.get("Item")).orElse(ItemStack.EMPTY);
                int price = productTag.getInt("Price");
                int purchaseLimit = productTag.getInt("PurchaseLimit");
                if (template.isEmpty() || !JobShopRules.validConfiguration(price, purchaseLimit)) continue;
                Product product = new Product(template, price, purchaseLimit,
                        !productTag.contains("Enabled") || productTag.getBoolean("Enabled"));
                CompoundTag purchases = productTag.getCompound("Purchases");
                for (String playerKey : purchases.getAllKeys()) {
                    try {
                        int count = purchases.getInt(playerKey);
                        if (count > 0) product.purchases.put(UUID.fromString(playerKey), count);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                data.products.put(id, product);
            } catch (IllegalArgumentException | NullPointerException ignored) {
            }
        }
        return data;
    }

    public static JobShopSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(JobShopSavedData::new, JobShopSavedData::load, null),
                "moveearth_job_shop");
    }

    public record ProductSnapshot(UUID id, ItemStack template, int price, int purchaseLimit,
                                  int purchased, boolean enabled) {
        public int remainingPurchases() {
            return JobShopRules.remainingPurchases(purchased, purchaseLimit);
        }
    }

    private static final class Product {
        private final ItemStack template;
        private int price;
        private int purchaseLimit;
        private boolean enabled;
        private final Map<UUID, Integer> purchases = new LinkedHashMap<>();

        private Product(ItemStack template, int price, int purchaseLimit, boolean enabled) {
            this.template = template;
            this.price = price;
            this.purchaseLimit = purchaseLimit;
            this.enabled = enabled;
        }
    }
}
