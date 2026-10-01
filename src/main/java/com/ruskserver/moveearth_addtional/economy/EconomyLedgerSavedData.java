package com.ruskserver.moveearth_addtional.economy;

import com.ruskserver.moveearth_addtional.jobs.JobIncomePolicy;
import com.ruskserver.moveearth_addtional.event.HarvestFestivalRules;
import com.ruskserver.moveearth_addtional.event.EventScheduleRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server-owned source of truth for MoveEarth balances and idempotent payments. Market orders,
 * claims, wreckage and event rewards live in {@link EconomyGoodsSavedData}, a small file that
 * hand-offs persist at once; this ledger is saved with the world. See that class for why the two
 * files may diverge after a crash without duplicating anything.
 */
public final class EconomyLedgerSavedData extends SavedData {
    private static final int RECENT_INDEX_LIMIT = 100;
    private static final int JOURNAL_LIMIT = 10_000;
    private final Map<Account, Long> balances = new LinkedHashMap<>();
    private final Map<UUID, String> knownPlayerNames = new LinkedHashMap<>();
    private final Map<UUID, Transaction> transactions = new LinkedHashMap<>();
    /** One durable receipt per nation keeps the current upkeep retry idempotent after journal trimming. */
    private final Map<UUID, Transaction> upkeepReceipts = new LinkedHashMap<>();
    /** Newest transactions per player and nation account; the only source of account history. */
    private final RecentIndex<Account, Transaction> recentByAccount = new RecentIndex<>(RECENT_INDEX_LIMIT);
    private final Map<UUID, JobIncomeState> jobIncome = new LinkedHashMap<>();
    private final Map<UUID, EventIncome> eventIncome = new LinkedHashMap<>();
    private UUID harvestId;
    private long harvestEndTick;
    private boolean harvestSettled = true;
    private String eventKind = "HARVEST";
    private int targetRegion;
    private String targetMaterial = "";
    private long nextAutoEventTick = -1L;
    private int eventSequence;
    private final Map<UUID, HarvestScore> harvestScores = new LinkedHashMap<>();
    /** Attached by {@link #get}; every goods read and write goes through it. */
    private EconomyGoodsSavedData goods;
    /** Goods read from a ledger file written before the split, until the goods file holds them. */
    private EconomyGoodsSavedData legacyGoods;

    public long balance(Account account) {
        return account == null ? 0L : balances.getOrDefault(account, 0L);
    }

    /** Called only after disband validation; never discard a non-empty national treasury. */
    /** Every account balance, for the operators' money supply figures. */
    public Map<Account, Long> balances() { return Map.copyOf(balances); }

    public boolean removeEmptyNationAccount(UUID nationId) {
        if (nationId == null) return false;
        Account account = Account.nation(nationId);
        if (balance(account) != 0L) return false;
        if (balances.remove(account) != null) setDirty();
        recentByAccount.remove(account);
        return true;
    }

    public void rememberPlayer(UUID id, String name) {
        if (id == null || name == null || name.isBlank()) return;
        String normalized = name.length() > 32 ? name.substring(0, 32) : name;
        if (!normalized.equals(knownPlayerNames.get(id))) {
            knownPlayerNames.put(id, normalized);
            setDirty();
        }
    }

    public String knownPlayerName(UUID id) { return knownPlayerNames.get(id); }

    public Result transfer(UUID id, Account from, Account to, long amount, String reason) {
        return transfer(id, from, to, amount, reason, null);
    }

    public Result transfer(UUID id, Account from, Account to, long amount, String reason, UUID relatedId) {
        if (id == null || (from == null && to == null) || from != null && from.equals(to)
                || amount <= 0L || reason == null || reason.isBlank() || reason.length() > 80) {
            return Result.INVALID;
        }
        Transaction prior = transaction(id);
        if (prior != null) return prior.matches(from, to, amount, reason, relatedId)
                ? Result.ALREADY_APPLIED : Result.CONFLICT;
        long sourceBalance = from == null ? 0L : balance(from);
        long targetBalance = to == null ? 0L : balance(to);
        switch (LedgerRules.check(sourceBalance, targetBalance, amount, from != null, to != null)) {
            case INVALID -> { return Result.INVALID; }
            case INSUFFICIENT_FUNDS -> { return Result.INSUFFICIENT_FUNDS; }
            case OVERFLOW -> { return Result.OVERFLOW; }
            case ALLOWED -> { }
        }
        if (from != null) balances.put(from, sourceBalance - amount);
        if (to != null) balances.put(to, targetBalance + amount);
        Transaction transaction = new Transaction(id, from, to, amount, reason, relatedId,
                System.currentTimeMillis());
        transactions.put(id, transaction);
        if ("nation_upkeep".equals(reason) && from != null && from.kind() == Kind.NATION)
            upkeepReceipts.put(from.id(), transaction);
        trimJournal();
        indexRecent(transaction);
        setDirty();
        com.ruskserver.moveearth_addtional.analytics.event.GameEvents.ledger(from, to, amount, reason);
        return Result.APPLIED;
    }

    public Map<UUID, Transaction> transactions() { return Map.copyOf(transactions); }
    public Transaction transaction(UUID id) {
        Transaction transaction = transactions.get(id);
        if (transaction != null) return transaction;
        return upkeepReceipts.values().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    private void trimJournal() {
        while (transactions.size() > JOURNAL_LIMIT)
            transactions.remove(transactions.keySet().iterator().next());
    }

    public List<MarketOrder> marketOrders() { return List.copyOf(goods.orders()); }
    /** One order by id, without copying the order book. */
    public MarketOrder marketOrder(UUID orderId) { return goods.order(orderId); }
    /** Whether any order, buy or sell, is listed at the station. */
    public boolean hasOrdersAt(UUID stationId) { return goods.hasOrdersAt(stationId); }
    public EventReward eventReward(UUID eventId, UUID playerId) { return goods.reward(eventId, playerId); }

    public UUID harvestId() { return harvestId; }
    public long harvestEndTick() { return harvestEndTick; }
    public boolean harvestSettled() { return harvestSettled; }
    public Map<UUID, HarvestScore> harvestScores() { return Map.copyOf(harvestScores); }
    public HarvestScore harvestScore(UUID playerId) { return playerId == null ? null : harvestScores.get(playerId); }
    public String eventKind() { return eventKind; }
    public int targetRegion() { return targetRegion; }
    public String targetMaterial() { return targetMaterial; }
    public long nextAutoEventTick() { return nextAutoEventTick; }
    public int eventSequence() { return eventSequence; }

    public boolean startHarvest(long nowTick, long durationTicks) {
        return startEvent("HARVEST", 0, "", nowTick, durationTicks);
    }

    public boolean startResource(int region, String material, long nowTick, long durationTicks) {
        if (region <= 0 || !List.of("coal", "iron", "copper").contains(material)) return false;
        return startEvent("RESOURCE", region, material, nowTick, durationTicks);
    }

    private boolean startEvent(String kind, int region, String material, long nowTick, long durationTicks) {
        if (!harvestSettled || durationTicks <= 0 || nowTick > Long.MAX_VALUE - durationTicks) return false;
        harvestId = UUID.randomUUID();
        harvestEndTick = nowTick + durationTicks;
        harvestSettled = false;
        eventKind = kind;
        targetRegion = region;
        targetMaterial = material;
        nextAutoEventTick = EventScheduleRules.nextStart(nowTick);
        eventSequence++;
        harvestScores.clear();
        setDirty();
        return true;
    }

    public boolean scoreHarvest(UUID playerId, String name, boolean farmer, long nowTick) {
        if (!"HARVEST".equals(eventKind)) return false;
        return scoreEvent(playerId, name, farmer, nowTick);
    }

    public boolean scoreResource(UUID playerId, String name, boolean miner, int region, String material, long nowTick) {
        if (!"RESOURCE".equals(eventKind) || targetRegion != region || !targetMaterial.equals(material)) return false;
        return scoreEvent(playerId, name, miner, nowTick);
    }

    private boolean scoreEvent(UUID playerId, String name, boolean matchingJob, long nowTick) {
        if (harvestSettled || harvestId == null || nowTick >= harvestEndTick || playerId == null) return false;
        HarvestScore previous = harvestScores.get(playerId);
        int points = previous == null ? 0 : previous.points();
        if (points >= HarvestFestivalRules.MAX_POINTS) return false;
        boolean bonus = previous == null ? matchingJob : previous.farmer();
        // Record when the score was reached: equal scores rank by who got there first.
        harvestScores.put(playerId, new HarvestScore(name, HarvestFestivalRules.addHarvest(points, bonus), bonus, nowTick));
        setDirty();
        return true;
    }

    public void finishHarvest() {
        if (!harvestSettled) {
            harvestSettled = true;
            setDirty();
        }
    }

    public boolean awardEvent(UUID eventId, UUID playerId, int requestedCurrency,
                              List<ItemStack> items, long nowMillis) {
        if (eventId == null || playerId == null || goods.reward(eventId, playerId) != null
                || requestedCurrency < 0 || requestedCurrency > 5 || items == null
                || items.stream().anyMatch(item -> item == null || item.isEmpty())) return false;
        EventIncome income = eventIncome.computeIfAbsent(playerId, ignored -> new EventIncome());
        long day = Math.floorDiv(nowMillis, 86_400_000L);
        if (income.day != day) {
            income.day = day;
            income.paid = 0;
        }
        int currency = Math.min(requestedCurrency, Math.max(0, 10 - income.paid));
        if (currency > 0 && transfer(UUID.randomUUID(), null, Account.player(playerId), currency,
                "event_reward", eventId) != Result.APPLIED) return false;
        income.paid += currency;
        goods.putReward(new EventReward(eventId, playerId, currency,
                items.stream().map(ItemStack::copy).toList(), nowMillis));
        setDirty();
        return true;
    }

    public List<EventReward> pendingEventRewards(UUID playerId) { return goods.pendingRewards(playerId); }

    /** Unclaimed rewards of one player, kept as a running count for the once-a-second HUD sync. */
    public int pendingEventRewardCount(UUID playerId) { return goods.pendingRewardCount(playerId); }

    public boolean updateEventItems(UUID eventId, UUID playerId, List<ItemStack> remaining) {
        EventReward reward = eventReward(eventId, playerId);
        if (reward == null || remaining == null) return false;
        if (remaining.isEmpty() && !HarvestFestivalRules.keepReward(eventId.equals(harvestId), true,
                reward.awardedAt(), System.currentTimeMillis())) {
            // A fully claimed reward of an older event no longer guards anything: settlement only
            // ever re-checks the current event.
            goods.removeReward(eventId, playerId);
        } else {
            goods.putReward(new EventReward(eventId, playerId, reward.currency(),
                    remaining.stream().map(ItemStack::copy).toList(), reward.awardedAt()));
        }
        return true;
    }

    /**
     * Drops fully claimed rewards of past events and unclaimed ones past the retention window, so
     * the reward table stays bounded over a season. Returns how many were removed.
     */
    public int pruneEventRewards(long nowMillis) {
        return goods.removeRewardsIf(reward -> !HarvestFestivalRules.keepReward(
                reward.eventId().equals(harvestId), reward.items().isEmpty(), reward.awardedAt(), nowMillis));
    }

    public List<MarketClaim> marketClaims(UUID owner) { return goods.claimsOf(owner); }

    /** One claim by id, or null when it does not exist or belongs to someone else. */
    public MarketClaim marketClaim(UUID owner, UUID claimId) {
        MarketClaim claim = goods.claim(claimId);
        return claim != null && claim.owner().equals(owner) ? claim : null;
    }

    public MarketResult createBuyOrder(UUID owner, UUID stationId, ItemStack item,
                                       int quantity, long unitPrice, long expiresAt, long nowMillis) {
        long cost = MarketOrderRules.totalPrice(quantity, unitPrice);
        if (owner == null || stationId == null || item == null || item.isEmpty() || cost < 0
                || expiresAt <= nowMillis || expiresAt - nowMillis > MarketOrderRules.MAX_LIFETIME_MILLIS
                || openOrders(owner) >= MarketOrderRules.MAX_OPEN_ORDERS_PER_PLAYER)
            return new MarketResult(MarketStatus.INVALID, null);
        UUID id = UUID.randomUUID();
        Result paid = transfer(UUID.randomUUID(), Account.player(owner), Account.escrow(id), cost,
                "market_buy_escrow", id);
        if (paid != Result.APPLIED) return new MarketResult(MarketStatus.PAYMENT_FAILED, null);
        goods.putOrder(new MarketOrder(id, MarketOrder.Side.BUY, owner, stationId,
                item, quantity, unitPrice, expiresAt));
        return new MarketResult(MarketStatus.APPLIED, id);
    }

    /** Caller must remove exactly this many matching units from inventory after a successful listing. */
    public MarketResult createSellOrder(UUID owner, UUID stationId, ItemStack item,
                                        int quantity, long unitPrice, long expiresAt, long nowMillis) {
        if (owner == null || stationId == null || item == null || item.isEmpty()
                || MarketOrderRules.totalPrice(quantity, unitPrice) < 0
                || expiresAt <= nowMillis || expiresAt - nowMillis > MarketOrderRules.MAX_LIFETIME_MILLIS
                || outstanding(stationId) + (long) quantity > MarketOrderRules.MAX_OUTSTANDING_ITEMS_PER_STATION
                || openOrders(owner) >= MarketOrderRules.MAX_OPEN_ORDERS_PER_PLAYER)
            return new MarketResult(MarketStatus.INVALID, null);
        UUID id = UUID.randomUUID();
        goods.putOrder(new MarketOrder(id, MarketOrder.Side.SELL, owner, stationId,
                item, quantity, unitPrice, expiresAt));
        return new MarketResult(MarketStatus.APPLIED, id);
    }

    /** Remote purchase: money moves now, stock becomes a station-bound personal claim. */
    public MarketStatus purchase(UUID buyer, UUID orderId, int quantity, long nowMillis) {
        MarketOrder order = goods.order(orderId);
        if (order == null || order.side() != MarketOrder.Side.SELL || buyer == null
                || buyer.equals(order.owner()) || !MarketOrderRules.stillOpen(nowMillis, order.expiresAt())
                || !MarketOrderRules.canFill(quantity, order.remaining(), order.remaining(),
                order.remaining()))
            return MarketStatus.INVALID;
        long amount = MarketOrderRules.totalPrice(quantity, order.unitPrice());
        if (amount < 0) return MarketStatus.INVALID;
        Result paid = transfer(UUID.randomUUID(), Account.player(buyer), Account.player(order.owner()),
                amount, "market_sell_filled", order.id());
        if (paid != Result.APPLIED) return MarketStatus.PAYMENT_FAILED;
        addClaim(buyer, order.stationId(), order.item(), quantity);
        updateOrder(order, quantity);
        return MarketStatus.APPLIED;
    }

    /** Physical delivery: caller verifies and removes matching items only after this succeeds. */
    public MarketStatus deliver(UUID seller, UUID orderId, ItemStack delivered,
                                int quantity, long nowMillis) {
        MarketOrder order = goods.order(orderId);
        if (order == null || order.side() != MarketOrder.Side.BUY || seller == null
                || seller.equals(order.owner()) || delivered == null
                || !ItemStack.isSameItemSameComponents(order.item(), delivered)
                || !MarketOrderRules.stillOpen(nowMillis, order.expiresAt())
                || !MarketOrderRules.canFill(quantity, order.remaining(), delivered.getCount(),
                MarketOrderRules.MAX_OUTSTANDING_ITEMS_PER_STATION - outstanding(order.stationId())))
            return MarketStatus.INVALID;
        long amount = MarketOrderRules.totalPrice(quantity, order.unitPrice());
        if (amount < 0) return MarketStatus.INVALID;
        Result paid = transfer(UUID.randomUUID(), Account.escrow(order.id()), Account.player(seller),
                amount, "market_buy_filled", order.id());
        if (paid != Result.APPLIED) return MarketStatus.PAYMENT_FAILED;
        addClaim(order.owner(), order.stationId(), order.item(), quantity);
        updateOrder(order, quantity);
        return MarketStatus.APPLIED;
    }

    /** Cancelling a sell order keeps the unsold stock at its station for owner pickup. */
    public MarketStatus cancelMarketOrder(UUID actor, UUID orderId, boolean admin) {
        MarketOrder order = goods.order(orderId);
        if (order == null || actor == null || !admin && !order.owner().equals(actor)) return MarketStatus.INVALID;
        if (order.side() == MarketOrder.Side.BUY) {
            // Refund what the escrow holds. It holds exactly the remaining demand, unless a crash
            // left the order (goods file) without its escrow (ledger file): then the order still
            // has to go, or it would retry its refund forever.
            long refund = Math.min((long) order.remaining() * order.unitPrice(), balance(Account.escrow(order.id())));
            if (refund > 0 && transfer(UUID.randomUUID(), Account.escrow(order.id()),
                    Account.player(order.owner()), refund, "market_buy_refund", order.id()) != Result.APPLIED)
                return MarketStatus.PAYMENT_FAILED;
        } else if (order.remaining() > 0) {
            addClaim(order.owner(), order.stationId(), order.item(), order.remaining());
        }
        goods.removeOrder(orderId);
        return MarketStatus.APPLIED;
    }

    /** Cheap when nothing is due: the goods store tracks the earliest expiry. */
    public int expireMarketOrders(long nowMillis) {
        if (goods.nextExpiry() > nowMillis) return 0;
        int expired = 0;
        for (MarketOrder order : List.copyOf(goods.orders())) {
            if (order.expiresAt() > nowMillis) continue;
            if (cancelMarketOrder(order.owner(), order.id(), false) == MarketStatus.APPLIED) expired++;
        }
        goods.resetNextExpiry();
        return expired;
    }

    public List<MarketClaim> claimsAt(UUID owner, UUID stationId) {
        return goods.claimsOf(owner).stream().filter(claim -> claim.stationId().equals(stationId)).toList();
    }

    /** The goods store hand-offs persist; see {@link PlayerHandOff}. */
    EconomyGoodsSavedData goods() { return goods; }

    public boolean reduceClaim(UUID owner, UUID claimId, int quantity) {
        MarketClaim claim = goods.claim(claimId);
        if (claim == null || !claim.owner().equals(owner) || quantity < 1 || quantity > claim.quantity()) return false;
        if (quantity == claim.quantity()) goods.removeClaim(claimId);
        else goods.putClaim(new MarketClaim(claim.id(), owner, claim.stationId(),
                claim.item(), claim.quantity() - quantity));
        return true;
    }

    /** Units held at the station (claims plus unsold stock), from a running per-station total. */
    public int outstanding(UUID stationId) { return goods.outstanding(stationId); }

    /** Cancel demand and turn all physical stock/claims into a policy-gated local wreckage. */
    public MarketStatus wreckMarketStation(UUID stationId, UUID nationId,
                                           ResourceLocation dimension, BlockPos pos) {
        if (stationId == null || dimension == null || pos == null) return MarketStatus.INVALID;
        if (goods.wreckage(dimension, pos.asLong()) != null) return MarketStatus.INVALID;
        for (MarketOrder order : List.copyOf(goods.orders())) {
            if (!order.stationId().equals(stationId)) continue;
            if (cancelMarketOrder(order.owner(), order.id(), false) != MarketStatus.APPLIED)
                return MarketStatus.PAYMENT_FAILED;
        }
        List<MarketClaim> contents = goods.claimsAtStation(stationId);
        if (contents.isEmpty()) return MarketStatus.APPLIED;
        goods.putWreckage(dimension, pos.asLong(), new MarketWreckage(nationId, List.copyOf(contents)));
        for (MarketClaim claim : contents) goods.removeClaim(claim.id());
        return MarketStatus.APPLIED;
    }

    public MarketWreckage marketWreckage(ResourceLocation dimension, BlockPos pos) {
        return goods.wreckage(dimension, pos.asLong());
    }

    public boolean reduceWreckageClaim(ResourceLocation dimension, BlockPos pos,
                                       UUID claimId, int quantity) {
        MarketWreckage wreckage = goods.wreckage(dimension, pos.asLong());
        if (wreckage == null || quantity < 1) return false;
        List<MarketClaim> next = new java.util.ArrayList<>();
        boolean changed = false;
        for (MarketClaim claim : wreckage.claims()) {
            if (!claim.id().equals(claimId)) { next.add(claim); continue; }
            if (quantity > claim.quantity()) return false;
            changed = true;
            if (quantity < claim.quantity()) next.add(new MarketClaim(claim.id(), claim.owner(),
                    claim.stationId(), claim.item(), claim.quantity() - quantity));
        }
        if (!changed) return false;
        if (next.isEmpty()) goods.removeWreckage(dimension, pos.asLong());
        else goods.putWreckage(dimension, pos.asLong(), new MarketWreckage(wreckage.nationId(), List.copyOf(next)));
        return true;
    }

    private int openOrders(UUID owner) { return goods.openOrders(owner); }

    private void updateOrder(MarketOrder order, int filled) {
        int remainder = order.remaining() - filled;
        if (remainder == 0) goods.removeOrder(order.id());
        else goods.putOrder(order.withRemaining(remainder));
    }

    private void addClaim(UUID owner, UUID stationId, ItemStack item, int quantity) {
        UUID id = UUID.randomUUID();
        goods.putClaim(new MarketClaim(id, owner, stationId, item, quantity));
    }

    /**
     * Newest transactions of a player or nation account, newest first, at most 100. Read from the
     * bounded per-account index, never the journal; escrow accounts are not indexed.
     */
    public List<Transaction> recent(Account account, int limit) {
        if (account == null || limit <= 0) return List.of();
        return recentByAccount.recent(account, Math.min(limit, RECENT_INDEX_LIMIT));
    }

    private void indexRecent(Transaction transaction) {
        indexRecent(transaction.from(), transaction);
        if (!java.util.Objects.equals(transaction.from(), transaction.to()))
            indexRecent(transaction.to(), transaction);
    }

    private void indexRecent(Account account, Transaction transaction) {
        // Escrow accounts are one per order and never shown, so they would only grow the index.
        if (account == null || account.kind() == Kind.ESCROW) return;
        recentByAccount.add(account, transaction);
    }

    /** Income counters and minted balance live in the same SavedData for crash-safe persistence. */
    public JobIncomeSnapshot awardJobIncome(UUID playerId, double xp, long nowMillis) {
        if (playerId == null || !Double.isFinite(xp) || xp <= 0.0D) return jobIncome(playerId, nowMillis);
        JobIncomeState state = jobIncome.computeIfAbsent(playerId, ignored -> new JobIncomeState());
        advanceIncomeWindow(state, nowMillis);
        JobIncomePolicy.Award award = JobIncomePolicy.award(xp, state.carriedXp,
                state.paidThisHour, state.paidToday);
        if (award.currency() > 0) {
            Result result = transfer(UUID.randomUUID(), null, Account.player(playerId),
                    award.currency(), "jobs_action");
            if (result != Result.APPLIED) return new JobIncomeSnapshot(0, state.paidThisHour,
                    state.paidToday, state.carriedXp);
            state.paidThisHour += award.currency();
            state.paidToday += award.currency();
        }
        state.carriedXp = award.carriedXp();
        setDirty();
        return new JobIncomeSnapshot(award.currency(), state.paidThisHour,
                state.paidToday, state.carriedXp);
    }

    public JobIncomeSnapshot jobIncome(UUID playerId, long nowMillis) {
        JobIncomeState state = playerId == null ? null : jobIncome.get(playerId);
        if (state == null) return new JobIncomeSnapshot(0, 0, 0, 0.0D);
        long hour = Math.floorDiv(nowMillis, 3_600_000L);
        long day = Math.floorDiv(nowMillis, 86_400_000L);
        return new JobIncomeSnapshot(0, hour > state.hour ? 0 : state.paidThisHour,
                day > state.day ? 0 : state.paidToday, state.carriedXp);
    }

    private void advanceIncomeWindow(JobIncomeState state, long nowMillis) {
        long hour = Math.floorDiv(nowMillis, 3_600_000L);
        long day = Math.floorDiv(nowMillis, 86_400_000L);
        if (hour > state.hour) {
            state.hour = hour;
            state.paidThisHour = 0;
        }
        if (day > state.day) {
            state.day = day;
            state.paidToday = 0;
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag accountList = new ListTag();
        balances.forEach((account, balance) -> {
            CompoundTag entry = new CompoundTag();
            account.save(entry, "Account");
            entry.putLong("Balance", balance);
            accountList.add(entry);
        });
        tag.put("Accounts", accountList);
        ListTag names = new ListTag();
        knownPlayerNames.forEach((id, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putString("Name", name);
            names.add(entry);
        });
        tag.put("KnownPlayerNames", names);
        ListTag journal = new ListTag();
        transactions.values().forEach(transaction -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", transaction.id());
            if (transaction.from() != null) transaction.from().save(entry, "From");
            if (transaction.to() != null) transaction.to().save(entry, "To");
            entry.putLong("Amount", transaction.amount());
            entry.putString("Reason", transaction.reason());
            if (transaction.relatedId() != null) entry.putUUID("RelatedId", transaction.relatedId());
            entry.putLong("OccurredAt", transaction.occurredAt());
            journal.add(entry);
        });
        tag.put("Transactions", journal);
        ListTag receipts = new ListTag();
        upkeepReceipts.values().forEach(transaction -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", transaction.id());
            transaction.from().save(entry, "From");
            entry.putLong("Amount", transaction.amount());
            entry.putLong("OccurredAt", transaction.occurredAt());
            receipts.add(entry);
        });
        tag.put("UpkeepReceipts", receipts);
        ListTag jobIncomeList = new ListTag();
        jobIncome.forEach((playerId, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", playerId);
            entry.putLong("Hour", state.hour);
            entry.putLong("Day", state.day);
            entry.putInt("PaidHour", state.paidThisHour);
            entry.putInt("PaidDay", state.paidToday);
            entry.putDouble("CarriedXp", state.carriedXp);
            jobIncomeList.add(entry);
        });
        tag.put("JobIncome", jobIncomeList);
        ListTag eventIncomeList = new ListTag();
        eventIncome.forEach((playerId, income) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", playerId);
            entry.putLong("Day", income.day);
            entry.putInt("Paid", income.paid);
            eventIncomeList.add(entry);
        });
        tag.put("EventIncome", eventIncomeList);
        if (harvestId != null) tag.putUUID("HarvestId", harvestId);
        tag.putLong("HarvestEndTick", harvestEndTick);
        tag.putBoolean("HarvestSettled", harvestSettled);
        tag.putString("EventKind", eventKind);
        tag.putInt("TargetRegion", targetRegion);
        tag.putString("TargetMaterial", targetMaterial);
        tag.putLong("NextAutoEventTick", nextAutoEventTick);
        tag.putInt("EventSequence", eventSequence);
        ListTag harvestList = new ListTag();
        harvestScores.forEach((playerId, score) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", playerId);
            entry.putString("Name", score.name());
            entry.putInt("Points", score.points());
            entry.putBoolean("Farmer", score.farmer());
            entry.putLong("ReachedTick", score.reachedTick());
            harvestList.add(entry);
        });
        tag.put("HarvestScores", harvestList);
        // Goods read from an older file stay here until the goods file holds them, so a crash
        // before that first write still finds them in one of the two files.
        if (legacyGoods != null && goods != null && goods.written()) legacyGoods = null;
        if (legacyGoods != null) legacyGoods.writeLists(tag, registries);
        return tag;
    }

    public static EconomyLedgerSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        EconomyLedgerSavedData data = new EconomyLedgerSavedData();
        ListTag accountList = tag.getList("Accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accountList.size(); i++) {
            CompoundTag entry = accountList.getCompound(i);
            Account.load(entry, "Account").ifPresent(account ->
                    data.balances.put(account, Math.max(0L, entry.getLong("Balance"))));
        }
        ListTag names = tag.getList("KnownPlayerNames", Tag.TAG_COMPOUND);
        for (int i = 0; i < names.size(); i++) {
            CompoundTag entry = names.getCompound(i);
            if (entry.hasUUID("Id") && !entry.getString("Name").isBlank()) {
                String name = entry.getString("Name");
                data.knownPlayerNames.put(entry.getUUID("Id"), name.length() > 32 ? name.substring(0, 32) : name);
            }
        }
        ListTag journal = tag.getList("Transactions", Tag.TAG_COMPOUND);
        for (int i = 0; i < journal.size(); i++) {
            CompoundTag entry = journal.getCompound(i);
            if (!entry.hasUUID("Id")) continue;
            Account from = Account.load(entry, "From").orElse(null);
            Account to = Account.load(entry, "To").orElse(null);
            long amount = entry.getLong("Amount");
            if ((from == null && to == null) || amount <= 0L) continue;
            UUID id = entry.getUUID("Id");
            Transaction transaction = new Transaction(id, from, to, amount,
                    entry.getString("Reason"), entry.hasUUID("RelatedId") ? entry.getUUID("RelatedId") : null,
                    entry.getLong("OccurredAt"));
            data.transactions.put(id, transaction);
            if ("nation_upkeep".equals(transaction.reason()) && from != null && from.kind() == Kind.NATION)
                data.upkeepReceipts.put(from.id(), transaction);
            data.indexRecent(transaction);
            data.trimJournal();
        }
        if (journal.size() > JOURNAL_LIMIT) data.setDirty();
        ListTag receipts = tag.getList("UpkeepReceipts", Tag.TAG_COMPOUND);
        for (int i = 0; i < receipts.size(); i++) {
            CompoundTag entry = receipts.getCompound(i);
            Account from = Account.load(entry, "From").orElse(null);
            if (!entry.hasUUID("Id") || from == null || from.kind() != Kind.NATION
                    || entry.getLong("Amount") <= 0L) continue;
            Transaction receipt = new Transaction(entry.getUUID("Id"), from, null,
                    entry.getLong("Amount"), "nation_upkeep", null, entry.getLong("OccurredAt"));
            data.upkeepReceipts.put(from.id(), receipt);
        }
        ListTag incomeList = tag.getList("JobIncome", Tag.TAG_COMPOUND);
        for (int i = 0; i < incomeList.size(); i++) {
            CompoundTag entry = incomeList.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            JobIncomeState state = new JobIncomeState();
            state.hour = entry.getLong("Hour");
            state.day = entry.getLong("Day");
            state.paidThisHour = Math.max(0, Math.min(JobIncomePolicy.HOURLY_LIMIT, entry.getInt("PaidHour")));
            state.paidToday = Math.max(0, Math.min(JobIncomePolicy.DAILY_LIMIT, entry.getInt("PaidDay")));
            double carriedXp = entry.getDouble("CarriedXp");
            state.carriedXp = Double.isFinite(carriedXp) && carriedXp >= 0.0D
                    && carriedXp < JobIncomePolicy.XP_PER_CURRENCY ? carriedXp : 0.0D;
            data.jobIncome.put(entry.getUUID("Player"), state);
        }
        if (tag.hasUUID("HarvestId")) {
            data.harvestId = tag.getUUID("HarvestId");
            data.harvestEndTick = Math.max(0L, tag.getLong("HarvestEndTick"));
            data.harvestSettled = tag.getBoolean("HarvestSettled");
        }
        data.eventKind = "RESOURCE".equals(tag.getString("EventKind")) ? "RESOURCE" : "HARVEST";
        data.targetRegion = Math.max(0, tag.getInt("TargetRegion"));
        data.targetMaterial = tag.getString("TargetMaterial");
        data.nextAutoEventTick = tag.contains("NextAutoEventTick", Tag.TAG_LONG)
                ? Math.max(0L, tag.getLong("NextAutoEventTick")) : -1L;
        data.eventSequence = Math.max(0, tag.getInt("EventSequence"));
        ListTag harvestList = tag.getList("HarvestScores", Tag.TAG_COMPOUND);
        for (int i = 0; i < harvestList.size(); i++) {
            CompoundTag entry = harvestList.getCompound(i);
            if (entry.hasUUID("Player")) data.harvestScores.put(entry.getUUID("Player"),
                    new HarvestScore(entry.getString("Name"), Math.max(0, Math.min(HarvestFestivalRules.MAX_POINTS, entry.getInt("Points"))),
                            entry.getBoolean("Farmer"), entry.getLong("ReachedTick")));
        }
        ListTag eventIncomeList = tag.getList("EventIncome", Tag.TAG_COMPOUND);
        for (int i = 0; i < eventIncomeList.size(); i++) {
            CompoundTag entry = eventIncomeList.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            EventIncome income = new EventIncome();
            income.day = entry.getLong("Day");
            income.paid = Math.max(0, Math.min(10, entry.getInt("Paid")));
            data.eventIncome.put(entry.getUUID("Player"), income);
        }
        if (EconomyGoodsSavedData.hasLists(tag)) data.legacyGoods = EconomyGoodsSavedData.load(tag, registries);
        return data;
    }

    private static final String DATA_NAME = "moveearth_economy_ledger";

    public static EconomyLedgerSavedData get(MinecraftServer server) {
        EconomyLedgerSavedData ledger = server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EconomyLedgerSavedData::new, EconomyLedgerSavedData::load, null),
                DATA_NAME);
        if (ledger.goods == null) ledger.attachGoods(server);
        return ledger;
    }

    /** Loads the goods store and moves goods out of a ledger file written before the split. */
    private void attachGoods(MinecraftServer server) {
        EconomyGoodsSavedData store = EconomyGoodsSavedData.get(server);
        goods = store;
        switch (LegacyGoodsMigration.decide(legacyGoods != null && !legacyGoods.isEmpty(), !store.createdFresh())) {
            case NONE -> legacyGoods = null;
            case DISCARD -> {
                legacyGoods = null;
                setDirty();
            }
            case IMPORT -> {
                store.absorb(legacyGoods);
                // Written at once so the next ledger save may drop its copy; if this fails the
                // ledger keeps writing the copy until the goods file reaches disk.
                if (store.persistNow(server)) legacyGoods = null;
                setDirty();
            }
        }
    }

    public record Account(Kind kind, UUID id) {
        public Account {
            if (kind == null || id == null) throw new IllegalArgumentException("Account needs kind and id");
        }
        public static Account player(UUID id) { return new Account(Kind.PLAYER, id); }
        public static Account nation(UUID id) { return new Account(Kind.NATION, id); }
        public static Account escrow(UUID id) { return new Account(Kind.ESCROW, id); }

        private void save(CompoundTag tag, String prefix) {
            tag.putString(prefix + "Kind", kind.name());
            tag.putUUID(prefix + "Id", id);
        }

        private static java.util.Optional<Account> load(CompoundTag tag, String prefix) {
            if (!tag.hasUUID(prefix + "Id")) return java.util.Optional.empty();
            try {
                return java.util.Optional.of(new Account(Kind.valueOf(tag.getString(prefix + "Kind")),
                        tag.getUUID(prefix + "Id")));
            } catch (IllegalArgumentException exception) {
                return java.util.Optional.empty();
            }
        }
    }

    public enum Kind { PLAYER, NATION, ESCROW }
    public enum Result { APPLIED, ALREADY_APPLIED, INVALID, INSUFFICIENT_FUNDS, OVERFLOW, CONFLICT }
    /** NEW_ACCOUNT_LIMIT: refused before the ledger was touched, by the new-account daily send limit. */
    public enum MarketStatus { APPLIED, INVALID, PAYMENT_FAILED, NEW_ACCOUNT_LIMIT }
    public record MarketResult(MarketStatus status, UUID orderId) { }
    /** awardedAt: wall-clock millis of settlement, for the unclaimed-reward retention window. */
    public record EventReward(UUID eventId, UUID playerId, int currency, List<ItemStack> items, long awardedAt) { }
    /** reachedTick: open-time tick of the last scoring harvest (0 for scores saved before it existed). */
    public record HarvestScore(String name, int points, boolean farmer, long reachedTick) { }
    private static final class EventIncome {
        private long day;
        private int paid;
    }

    public record MarketClaim(UUID id, UUID owner, UUID stationId, ItemStack item, int quantity) {
        public MarketClaim {
            if (id == null || owner == null || stationId == null || item == null || item.isEmpty() || quantity < 1)
                throw new IllegalArgumentException("Invalid market claim");
            item = item.copyWithCount(1);
        }
    }
    public record MarketWreckage(UUID nationId, List<MarketClaim> claims) { }
    public record JobIncomeSnapshot(int justEarned, int paidThisHour, int paidToday, double carriedXp) { }
    private static final class JobIncomeState {
        private long hour = -1L;
        private long day = -1L;
        private int paidThisHour;
        private int paidToday;
        private double carriedXp;
    }
    public record Transaction(UUID id, Account from, Account to, long amount, String reason,
                              UUID relatedId, long occurredAt) {
        public boolean matches(Account from, Account to, long amount, String reason, UUID relatedId) {
            return java.util.Objects.equals(this.from, from) && java.util.Objects.equals(this.to, to)
                    && this.amount == amount && this.reason.equals(reason)
                    && java.util.Objects.equals(this.relatedId, relatedId);
        }
    }
}
