package com.ruskserver.moveearth_addtional.economy;

import com.mojang.logging.LogUtils;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.EventReward;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.MarketClaim;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData.MarketWreckage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Everything a {@link PlayerHandOff} takes goods out of: market orders, market claims, station
 * wreckage and event rewards. It lives in its own small file next to the ledger so a hand-off can
 * persist it at once without writing the ledger's transaction journal.
 *
 * <h2>Consistency</h2>
 * Hand-offs only ever reduce this store. They book what fits, reduce the store, write this file
 * synchronously (temp file, fsync, atomic move, on the server thread), and only then move the
 * items. Every write of this file happens on the server thread, at a hand-off or when the world
 * saves, and never through NeoForge's asynchronous IO queue, so no older snapshot can land after a
 * newer one. The player file is written later still (autosave or logout), so it can only hold
 * handed-off goods if a store file without them is already on disk: a crash loses a hand-off at
 * worst and never duplicates one. A failed write rolls the reduction back and moves nothing.
 *
 * <p>The ledger (balances, journal, escrow) keeps its normal autosave and may now be older or
 * newer than this file after a crash. Each store change that also moves TC (purchase, delivery,
 * buy-order escrow and refund, event currency) is checked for both orders and never creates a
 * unit twice:
 * <ul>
 * <li>store newer: a purchase keeps the buyer's TC (the seller goes unpaid), a delivery or
 *     cancelled buy order leaves its TC in escrow, event currency is not minted. Goods move once.</li>
 * <li>ledger newer: the TC moved but the claim, order or reward is missing. A buy order whose
 *     escrow is missing cannot pay out, since deliveries draw on that escrow.</li>
 * </ul>
 * Orders sit here with the claims because cancelling a sell order turns its stock into a claim:
 * splitting the two across files could resurrect the stock next to the claim.
 *
 * <p>Taking goods <em>in</em> (listing, delivering) is not covered by this ordering: the player
 * file of the depositor may be older than a store file written by someone else's hand-off. That
 * was already true of the ledger file and is unchanged here.
 */
public final class EconomyGoodsSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String DATA_NAME = "moveearth_economy_goods";

    private final Map<UUID, MarketOrder> orders = new LinkedHashMap<>();
    private final Map<UUID, MarketClaim> claims = new LinkedHashMap<>();
    private final Map<WreckageKey, MarketWreckage> wreckage = new LinkedHashMap<>();
    private final Map<UUID, EventReward> rewards = new LinkedHashMap<>();

    // Indexes, rebuilt through the put/remove primitives only.
    private final Map<UUID, Map<UUID, MarketClaim>> claimsByOwner = new HashMap<>();
    private final Map<UUID, Long> outstandingByStation = new HashMap<>();
    private final Map<UUID, Integer> ordersByStation = new HashMap<>();
    private final Map<UUID, Integer> ordersByOwner = new HashMap<>();
    private final Map<UUID, Map<UUID, EventReward>> rewardsByPlayer = new HashMap<>();
    private final Map<UUID, Integer> pendingRewardsByPlayer = new HashMap<>();
    /** No order expires before this; may be early after removals, never late. */
    private long nextExpiry = Long.MAX_VALUE;

    private final HandOffJournal handOff = new HandOffJournal();
    /** Created because no goods file existed, rather than loaded from one. */
    private final boolean createdFresh;
    private boolean written;

    private EconomyGoodsSavedData(boolean createdFresh) {
        this.createdFresh = createdFresh;
    }

    boolean createdFresh() { return createdFresh; }
    /** Whether this store reached disk at least once since this server started. */
    boolean written() { return written; }

    boolean isEmpty() {
        return orders.isEmpty() && claims.isEmpty() && wreckage.isEmpty() && rewards.isEmpty();
    }

    // ---- orders ----

    MarketOrder order(UUID id) { return id == null ? null : orders.get(id); }
    Collection<MarketOrder> orders() { return orders.values(); }
    int openOrders(UUID owner) { return ordersByOwner.getOrDefault(owner, 0); }
    boolean hasOrdersAt(UUID stationId) { return ordersByStation.containsKey(stationId); }
    long nextExpiry() { return nextExpiry; }

    void putOrder(MarketOrder order) {
        MarketOrder previous = orders.put(order.id(), order);
        if (previous != null) unindexOrder(previous);
        indexOrder(order);
        handOff.record(() -> {
            if (previous == null) removeOrder(order.id());
            else putOrder(previous);
        });
        setDirty();
    }

    void removeOrder(UUID id) {
        MarketOrder previous = orders.remove(id);
        if (previous == null) return;
        unindexOrder(previous);
        handOff.record(() -> putOrder(previous));
        setDirty();
    }

    /** Recomputes the earliest expiry after a full expiry pass. */
    void resetNextExpiry() {
        long next = Long.MAX_VALUE;
        for (MarketOrder order : orders.values()) next = Math.min(next, order.expiresAt());
        nextExpiry = next;
    }

    private void indexOrder(MarketOrder order) {
        ordersByStation.merge(order.stationId(), 1, Integer::sum);
        ordersByOwner.merge(order.owner(), 1, Integer::sum);
        if (order.side() == MarketOrder.Side.SELL) addOutstanding(order.stationId(), order.remaining());
        nextExpiry = Math.min(nextExpiry, order.expiresAt());
    }

    private void unindexOrder(MarketOrder order) {
        decrement(ordersByStation, order.stationId());
        decrement(ordersByOwner, order.owner());
        if (order.side() == MarketOrder.Side.SELL) addOutstanding(order.stationId(), -order.remaining());
    }

    // ---- claims ----

    MarketClaim claim(UUID id) { return id == null ? null : claims.get(id); }

    List<MarketClaim> claimsOf(UUID owner) {
        Map<UUID, MarketClaim> owned = claimsByOwner.get(owner);
        return owned == null ? List.of() : List.copyOf(owned.values());
    }

    /** Every claim held at the station; a scan, for the rare station wreck. */
    List<MarketClaim> claimsAtStation(UUID stationId) {
        if (!outstandingByStation.containsKey(stationId)) return List.of();
        return claims.values().stream().filter(claim -> claim.stationId().equals(stationId)).toList();
    }

    /** Units held at the station: unclaimed purchases plus unsold sell-order stock. */
    int outstanding(UUID stationId) {
        return (int) Math.min(Integer.MAX_VALUE, outstandingByStation.getOrDefault(stationId, 0L));
    }

    void putClaim(MarketClaim claim) {
        MarketClaim previous = claims.put(claim.id(), claim);
        if (previous != null) unindexClaim(previous);
        indexClaim(claim);
        handOff.record(() -> {
            if (previous == null) removeClaim(claim.id());
            else putClaim(previous);
        });
        setDirty();
    }

    void removeClaim(UUID id) {
        MarketClaim previous = claims.remove(id);
        if (previous == null) return;
        unindexClaim(previous);
        handOff.record(() -> putClaim(previous));
        setDirty();
    }

    private void indexClaim(MarketClaim claim) {
        claimsByOwner.computeIfAbsent(claim.owner(), ignored -> new LinkedHashMap<>()).put(claim.id(), claim);
        addOutstanding(claim.stationId(), claim.quantity());
    }

    private void unindexClaim(MarketClaim claim) {
        Map<UUID, MarketClaim> owned = claimsByOwner.get(claim.owner());
        if (owned != null) {
            owned.remove(claim.id());
            if (owned.isEmpty()) claimsByOwner.remove(claim.owner());
        }
        addOutstanding(claim.stationId(), -claim.quantity());
    }

    private void addOutstanding(UUID stationId, long delta) {
        long next = outstandingByStation.getOrDefault(stationId, 0L) + delta;
        if (next <= 0L) outstandingByStation.remove(stationId);
        else outstandingByStation.put(stationId, next);
    }

    // ---- wreckage ----

    MarketWreckage wreckage(ResourceLocation dimension, long pos) {
        return wreckage.get(new WreckageKey(dimension, pos));
    }

    void putWreckage(ResourceLocation dimension, long pos, MarketWreckage value) {
        WreckageKey key = new WreckageKey(dimension, pos);
        MarketWreckage previous = wreckage.put(key, value);
        handOff.record(() -> {
            if (previous == null) removeWreckage(dimension, pos);
            else putWreckage(dimension, pos, previous);
        });
        setDirty();
    }

    void removeWreckage(ResourceLocation dimension, long pos) {
        MarketWreckage previous = wreckage.remove(new WreckageKey(dimension, pos));
        if (previous == null) return;
        handOff.record(() -> putWreckage(dimension, pos, previous));
        setDirty();
    }

    // ---- event rewards ----

    static UUID rewardKey(UUID eventId, UUID playerId) {
        return UUID.nameUUIDFromBytes((eventId.toString() + ":" + playerId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    EventReward reward(UUID eventId, UUID playerId) {
        EventReward reward = rewards.get(rewardKey(eventId, playerId));
        return reward != null && reward.playerId().equals(playerId) ? reward : null;
    }

    List<EventReward> pendingRewards(UUID playerId) {
        Map<UUID, EventReward> own = rewardsByPlayer.get(playerId);
        if (own == null) return List.of();
        return own.values().stream().filter(reward -> !reward.items().isEmpty()).toList();
    }

    int pendingRewardCount(UUID playerId) { return pendingRewardsByPlayer.getOrDefault(playerId, 0); }

    void putReward(EventReward reward) {
        UUID key = rewardKey(reward.eventId(), reward.playerId());
        EventReward previous = rewards.put(key, reward);
        if (previous != null) unindexReward(key, previous);
        indexReward(key, reward);
        handOff.record(() -> {
            if (previous == null) removeReward(reward.eventId(), reward.playerId());
            else putReward(previous);
        });
        setDirty();
    }

    void removeReward(UUID eventId, UUID playerId) {
        UUID key = rewardKey(eventId, playerId);
        EventReward previous = rewards.remove(key);
        if (previous == null) return;
        unindexReward(key, previous);
        handOff.record(() -> putReward(previous));
        setDirty();
    }

    int removeRewardsIf(Predicate<EventReward> drop) {
        List<EventReward> doomed = rewards.values().stream().filter(drop).toList();
        doomed.forEach(reward -> removeReward(reward.eventId(), reward.playerId()));
        return doomed.size();
    }

    private void indexReward(UUID key, EventReward reward) {
        rewardsByPlayer.computeIfAbsent(reward.playerId(), ignored -> new LinkedHashMap<>()).put(key, reward);
        if (!reward.items().isEmpty()) pendingRewardsByPlayer.merge(reward.playerId(), 1, Integer::sum);
    }

    private void unindexReward(UUID key, EventReward reward) {
        Map<UUID, EventReward> own = rewardsByPlayer.get(reward.playerId());
        if (own != null) {
            own.remove(key);
            if (own.isEmpty()) rewardsByPlayer.remove(reward.playerId());
        }
        if (!reward.items().isEmpty()) decrement(pendingRewardsByPlayer, reward.playerId());
    }

    private static void decrement(Map<UUID, Integer> counts, UUID key) {
        counts.computeIfPresent(key, (ignored, count) -> count > 1 ? count - 1 : null);
    }

    // ---- hand-off ----

    void beginHandOff() { handOff.begin(); }

    /** Writes the store now; on failure rolls back what the open hand-off took. */
    boolean commitHandOff(MinecraftServer server) {
        return handOff.commit(() -> persistNow(server));
    }

    void abortHandOff() { handOff.abort(); }

    boolean handOffOpen() { return handOff.open(); }

    /** Writes this store to its file at once, on the calling thread. */
    boolean persistNow(MinecraftServer server) {
        if (!isDirty()) return true;
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(DATA_NAME + ".dat").normalize();
        return write(path, server.registryAccess());
    }

    /**
     * World saves come through here too. Unlike {@link SavedData#save(File, HolderLookup.Provider)}
     * this writes synchronously rather than queueing on the IO worker, so the file on disk is always
     * the latest state handed to it.
     */
    @Override
    public void save(File file, HolderLookup.Provider registries) {
        if (isDirty()) write(file.toPath(), registries);
    }

    private boolean write(Path path, HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        root.put("data", save(new CompoundTag(), registries));
        NbtUtils.addCurrentDataVersion(root);
        try {
            Files.createDirectories(path.getParent());
            IOUtilities.writeNbtCompressed(root, path);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not save MoveEarth economy goods to {}", path, exception);
            return false;
        }
        setDirty(false);
        written = true;
        return true;
    }

    // ---- persistence ----

    /** Adds every entry of {@code legacy}, read from an older ledger file. */
    void absorb(EconomyGoodsSavedData legacy) {
        legacy.orders.values().forEach(this::putOrder);
        legacy.claims.values().forEach(this::putClaim);
        legacy.wreckage.forEach((key, value) -> putWreckage(key.dimension(), key.pos(), value));
        legacy.rewards.values().forEach(this::putReward);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        writeLists(tag, registries);
        return tag;
    }

    /** The same keys the ledger used, so older ledger files load through {@link #readLists}. */
    void writeLists(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rewardList = new ListTag();
        rewards.values().forEach(reward -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", reward.eventId());
            entry.putUUID("Player", reward.playerId());
            entry.putInt("Currency", reward.currency());
            ListTag items = new ListTag();
            reward.items().forEach(item -> items.add(item.save(registries)));
            entry.put("Items", items);
            entry.putLong("AwardedAt", reward.awardedAt());
            rewardList.add(entry);
        });
        tag.put("EventRewards", rewardList);
        ListTag orderList = new ListTag();
        orders.values().forEach(order -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", order.id());
            entry.putString("Side", order.side().name());
            entry.putUUID("Owner", order.owner());
            entry.putUUID("Station", order.stationId());
            entry.put("Item", order.item().save(registries));
            entry.putInt("Remaining", order.remaining());
            entry.putLong("UnitPrice", order.unitPrice());
            entry.putLong("ExpiresAt", order.expiresAt());
            orderList.add(entry);
        });
        tag.put("MarketOrders", orderList);
        ListTag claimList = new ListTag();
        claims.values().forEach(claim -> claimList.add(saveClaim(claim, registries)));
        tag.put("MarketClaims", claimList);
        ListTag wreckList = new ListTag();
        wreckage.forEach((key, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", key.dimension().toString());
            entry.putLong("Pos", key.pos());
            if (value.nationId() != null) entry.putUUID("Nation", value.nationId());
            ListTag items = new ListTag();
            value.claims().forEach(claim -> items.add(saveClaim(claim, registries)));
            entry.put("Claims", items);
            wreckList.add(entry);
        });
        tag.put("MarketWreckage", wreckList);
    }

    private static CompoundTag saveClaim(MarketClaim claim, HolderLookup.Provider registries) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Id", claim.id());
        entry.putUUID("Owner", claim.owner());
        entry.putUUID("Station", claim.stationId());
        entry.put("Item", claim.item().save(registries));
        entry.putInt("Quantity", claim.quantity());
        return entry;
    }

    /** Whether a ledger tag still carries goods from before the split. */
    static boolean hasLists(CompoundTag tag) {
        for (String key : List.of("EventRewards", "MarketOrders", "MarketClaims", "MarketWreckage"))
            if (!tag.getList(key, Tag.TAG_COMPOUND).isEmpty()) return true;
        return false;
    }

    static EconomyGoodsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyGoodsSavedData data = new EconomyGoodsSavedData(false);
        data.readLists(tag, registries);
        data.setDirty(false);
        return data;
    }

    void readLists(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rewardList = tag.getList("EventRewards", Tag.TAG_COMPOUND);
        // Rewards saved before AwardedAt existed start their retention window at this load.
        long loadedAt = System.currentTimeMillis();
        for (int i = 0; i < rewardList.size(); i++) {
            CompoundTag entry = rewardList.getCompound(i);
            if (!entry.hasUUID("Id") || !entry.hasUUID("Player")) continue;
            List<ItemStack> items = new ArrayList<>();
            ListTag itemList = entry.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < itemList.size(); j++)
                ItemStack.parse(registries, itemList.getCompound(j)).ifPresent(items::add);
            putReward(new EventReward(entry.getUUID("Id"), entry.getUUID("Player"),
                    Math.max(0, Math.min(5, entry.getInt("Currency"))), List.copyOf(items),
                    entry.contains("AwardedAt", Tag.TAG_LONG) ? entry.getLong("AwardedAt") : loadedAt));
        }
        ListTag orderList = tag.getList("MarketOrders", Tag.TAG_COMPOUND);
        for (int i = 0; i < orderList.size(); i++) {
            CompoundTag entry = orderList.getCompound(i);
            if (!entry.hasUUID("Id") || !entry.hasUUID("Owner") || !entry.hasUUID("Station")) continue;
            try {
                MarketOrder.Side side = MarketOrder.Side.valueOf(entry.getString("Side"));
                ItemStack.parse(registries, entry.getCompound("Item")).ifPresent(item -> {
                    int remaining = entry.getInt("Remaining");
                    long price = entry.getLong("UnitPrice");
                    if (MarketOrderRules.totalPrice(remaining, price) < 0) return;
                    putOrder(new MarketOrder(entry.getUUID("Id"), side, entry.getUUID("Owner"),
                            entry.getUUID("Station"), item, remaining, price, entry.getLong("ExpiresAt")));
                });
            } catch (IllegalArgumentException ignored) { }
        }
        ListTag claimList = tag.getList("MarketClaims", Tag.TAG_COMPOUND);
        for (int i = 0; i < claimList.size(); i++) {
            MarketClaim claim = loadClaim(claimList.getCompound(i), registries);
            if (claim != null) putClaim(claim);
        }
        ListTag wreckList = tag.getList("MarketWreckage", Tag.TAG_COMPOUND);
        for (int i = 0; i < wreckList.size(); i++) {
            CompoundTag entry = wreckList.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) continue;
            List<MarketClaim> contents = new ArrayList<>();
            ListTag items = entry.getList("Claims", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                MarketClaim claim = loadClaim(items.getCompound(j), registries);
                if (claim != null) contents.add(claim);
            }
            if (!contents.isEmpty()) putWreckage(dimension, entry.getLong("Pos"), new MarketWreckage(
                    entry.hasUUID("Nation") ? entry.getUUID("Nation") : null, List.copyOf(contents)));
        }
    }

    private static MarketClaim loadClaim(CompoundTag entry, HolderLookup.Provider registries) {
        if (!entry.hasUUID("Id") || !entry.hasUUID("Owner") || !entry.hasUUID("Station")) return null;
        ItemStack item = ItemStack.parse(registries, entry.getCompound("Item")).orElse(null);
        int quantity = entry.getInt("Quantity");
        if (item == null || quantity < 1 || quantity > MarketOrderRules.MAX_ORDER_QUANTITY) return null;
        return new MarketClaim(entry.getUUID("Id"), entry.getUUID("Owner"), entry.getUUID("Station"), item, quantity);
    }

    static EconomyGoodsSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                () -> new EconomyGoodsSavedData(true), EconomyGoodsSavedData::load, null), DATA_NAME);
    }

    record WreckageKey(ResourceLocation dimension, long pos) { }
}
