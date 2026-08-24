package com.ruskserver.moveearth_addtional.jobs;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.network.C2S_JobShopActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_JobShopPacket;
import com.ruskserver.moveearth_addtional.pvp.PvpMatchManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Server-authoritative shop actions. Client packets never provide item data. */
public final class JobShopService {
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private JobShopService() {
    }

    public static void handle(ServerPlayer player, C2S_JobShopActionPacket packet) {
        Action action;
        try {
            action = Action.valueOf(packet.action().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return;
        }
        boolean canAdmin = player.createCommandSourceStack().hasPermission(ADMIN_PERMISSION_LEVEL);
        if (action.admin && !canAdmin) {
            player.sendSystemMessage(Component.literal("[Jobs Shop] この操作を行う権限がありません。"));
            send(player);
            return;
        }
        if (action.admin && PvpMatchManager.INSTANCE.isActive(player)) {
            player.sendSystemMessage(Component.literal(
                    "[Jobs Shop] PvPの一時装備を商品化しないため、参加中は商品管理できません。"));
            send(player);
            return;
        }

        JobShopSavedData shop = JobShopSavedData.get(player.getServer());
        UUID productId = parseProductId(packet.productId()).orElse(null);
        switch (action) {
            case REQUEST -> send(player);
            case BUY -> {
                if (productId != null) purchase(player, shop, productId);
                send(player);
            }
            case ADD_HELD -> {
                ItemStack held = player.getMainHandItem();
                Optional<UUID> added = shop.addProduct(held, packet.price(), packet.purchaseLimit());
                if (added.isEmpty()) {
                    player.sendSystemMessage(Component.literal(
                            "[Jobs Shop] 手持ちアイテム、価格、購入上限、または商品数を確認してください。"));
                } else {
                    audit(player, "商品追加 " + added.get() + " " + held.getHoverName().getString()
                            + " x" + held.getCount() + " / " + packet.price() + " PT / limit="
                            + packet.purchaseLimit());
                }
                send(player);
            }
            case UPDATE -> {
                if (productId != null && shop.updateProduct(productId, packet.price(), packet.purchaseLimit())) {
                    audit(player, "商品更新 " + productId + " / " + packet.price() + " PT / limit="
                            + packet.purchaseLimit());
                }
                send(player);
            }
            case TOGGLE -> {
                if (productId != null && shop.toggleProduct(productId)) {
                    audit(player, "商品公開状態切替 " + productId);
                }
                send(player);
            }
            case REMOVE -> {
                if (productId != null && shop.removeProduct(productId)) {
                    audit(player, "商品削除 " + productId);
                }
                send(player);
            }
        }
    }

    public static void send(ServerPlayer player) {
        boolean canAdmin = player.createCommandSourceStack().hasPermission(ADMIN_PERMISSION_LEVEL);
        JobShopSavedData shop = JobShopSavedData.get(player.getServer());
        List<S2C_JobShopPacket.ProductEntry> entries = shop.products(player.getUUID(), canAdmin).stream()
                .map(product -> new S2C_JobShopPacket.ProductEntry(product.id(), product.template(),
                        product.price(), product.purchaseLimit(), product.purchased(), product.enabled()))
                .toList();
        int points = JobProgressSavedData.get(player.getServer()).snapshot(player.getUUID()).points();
        PacketDistributor.sendToPlayer(player, new S2C_JobShopPacket(points, canAdmin, entries));
    }

    private static void purchase(ServerPlayer player, JobShopSavedData shop, UUID productId) {
        if (PvpMatchManager.INSTANCE.isActive(player)) {
            player.sendSystemMessage(Component.literal("[Jobs Shop] PvP参加中は購入できません。"));
            return;
        }
        JobShopSavedData.ProductSnapshot product = shop.product(productId, player.getUUID()).orElse(null);
        if (product == null) return;
        JobProgressSavedData progress = JobProgressSavedData.get(player.getServer());
        int points = progress.snapshot(player.getUUID()).points();
        JobShopRules.PurchaseCheck check = JobShopRules.checkPurchase(points, product.price(),
                product.purchased(), product.purchaseLimit(), product.enabled());
        if (check != JobShopRules.PurchaseCheck.ALLOWED) {
            player.sendSystemMessage(Component.literal(purchaseFailure(check)));
            return;
        }
        if (!canFit(player.getInventory(), product.template())) {
            player.sendSystemMessage(Component.literal("[Jobs Shop] インベントリに商品の空きがありません。"));
            return;
        }
        if (!progress.trySpendPoints(player.getUUID(), product.price())) {
            player.sendSystemMessage(Component.literal("[Jobs Shop] ポイントが不足しています。"));
            return;
        }

        ItemStack reward = product.template().copy();
        if (!player.getInventory().add(reward) && !reward.isEmpty()) {
            player.drop(reward, false);
        }
        shop.recordPurchase(productId, player.getUUID());
        player.sendSystemMessage(Component.literal("[Jobs Shop] " + product.template().getHoverName().getString()
                + " x" + product.template().getCount() + " を " + product.price() + " PTで購入しました。"));
        Moveearth_addtional.LOGGER.info("[Jobs shop purchase] {} bought {} ({}) for {} PT",
                player.getScoreboardName(), product.template().getHoverName().getString(), productId, product.price());
    }

    static boolean canFit(Inventory inventory, ItemStack template) {
        int capacity = 0;
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                capacity += template.getMaxStackSize();
            } else if (existing.isStackable() && ItemStack.isSameItemSameComponents(existing, template)) {
                capacity += Math.max(0, existing.getMaxStackSize() - existing.getCount());
            }
            if (capacity >= template.getCount()) return true;
        }
        return false;
    }

    private static Optional<UUID> parseProductId(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(input));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String purchaseFailure(JobShopRules.PurchaseCheck check) {
        return switch (check) {
            case DISABLED -> "[Jobs Shop] この商品は現在販売停止中です。";
            case INVALID_PRODUCT -> "[Jobs Shop] 商品設定が不正です。";
            case LIMIT_REACHED -> "[Jobs Shop] この商品の購入上限に達しています。";
            case NOT_ENOUGH_POINTS -> "[Jobs Shop] ポイントが不足しています。";
            case ALLOWED -> "";
        };
    }

    private static void audit(ServerPlayer player, String operation) {
        Moveearth_addtional.LOGGER.info("[Jobs shop admin] {}: {}", player.getScoreboardName(), operation);
        player.sendSystemMessage(Component.literal("[Jobs Shop 管理] " + operation));
    }

    private enum Action {
        REQUEST(false),
        BUY(false),
        ADD_HELD(true),
        UPDATE(true),
        TOGGLE(true),
        REMOVE(true);

        private final boolean admin;

        Action(boolean admin) {
            this.admin = admin;
        }
    }
}
