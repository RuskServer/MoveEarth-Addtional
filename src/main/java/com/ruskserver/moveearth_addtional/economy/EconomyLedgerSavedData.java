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
import java.util.ArrayDeque;
import java.util.HashMap;

/** The server-owned source of truth for MoveEarth balances and idempotent payments. */
public final class EconomyLedgerSavedData extends SavedData {
    private static final int RECENT_INDEX_LIMIT = 100;
    private static final int JOURNAL_LIMIT = 10_000;
    private final Map<Account, Long> balances = new LinkedHashMap<>();
    private final Map<UUID, String> knownPlayerNames = new LinkedHashMap<>();
    private final Map<UUID, Transaction> transactions = new LinkedHashMap<>();
    /** One durable receipt per nation keeps the current upkeep retry idempotent after journal trimming. */
    private final Map<UUID, Transaction> upkeepReceipts = new LinkedHashMap<>();
    private final Map<Account, ArrayDeque<Transaction>> recentByPlayer = new HashMap<>();
    private final Map<UUID, JobIncomeState> jobIncome = new LinkedHashMap<>();
    private final Map<UUID, EventReward> eventRewards = new LinkedHashMap<>();
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
    private final Map<UUID, MarketOrder> marketOrders = new LinkedHashMap<>();
    private final Map<UUID, MarketClaim> marketClaims = new LinkedHashMap<>();
    private final Map<WreckageKey, MarketWreckage> marketWreckage = new LinkedHashMap<>();

    public long balance(Account account) {
        return account == null ? 0L : balances.getOrDefault(account, 0L);
    }

    /** Called only after disband validation; never discard a non-empty national treasury. */
    public boolean removeEmptyNationAccount(UUID nationId) {
        if (nationId == null) return false;
        Account account = Account.nation(nationId);
        if (balance(account) != 0L) return false;
        if (balances.remove(account) != null) setDirty();
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

    public List<MarketOrder> marketOrders() { return List.copyOf(marketOrders.values()); }
    public EventReward eventReward(UUID eventId, UUID playerId) {
        EventReward reward = eventRewards.get(eventRewardKey(eventId, playerId));
        return reward != null && reward.playerId().equals(playerId) ? reward : null;
    }

    public UUID harvestId() { return harvestId; }
    public long harvestEndTick() { return harvestEndTick; }
    public boolean harvestSettled() { return harvestSettled; }
    public Map<UUID, HarvestScore> harvestScores() { return Map.copyOf(harvestScores); }
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
        harvestScores.put(playerId, new HarvestScore(name, HarvestFestivalRules.addHarvest(points, bonus), bonus));
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
        if (eventId == null || playerId == null || eventRewards.containsKey(eventRewardKey(eventId, playerId))
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
        eventRewards.put(eventRewardKey(eventId, playerId), new EventReward(eventId, playerId, currency,
                items.stream().map(ItemStack::copy).toList()));
        setDirty();
        return true;
    }

    public List<EventReward> pendingEventRewards(UUID playerId) {
        return eventRewards.values().stream().filter(reward -> reward.playerId().equals(playerId)
                && !reward.items().isEmpty()).toList();
    }

    public boolean updateEventItems(UUID eventId, UUID playerId, List<ItemStack> remaining) {
        EventReward reward = eventReward(eventId, playerId);
        if (reward == null || remaining == null) return false;
        eventRewards.put(eventRewardKey(eventId, playerId), new EventReward(eventId, playerId, reward.currency(),
                remaining.stream().map(ItemStack::copy).toList()));
        setDirty();
        return true;
    }

    private static UUID eventRewardKey(UUID eventId, UUID playerId) {
        return UUID.nameUUIDFromBytes((eventId.toString() + ":" + playerId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public List<MarketClaim> marketClaims(UUID owner) {
        return marketClaims.values().stream().filter(claim -> claim.owner().equals(owner)).toList();
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
        marketOrders.put(id, new MarketOrder(id, MarketOrder.Side.BUY, owner, stationId,
                item, quantity, unitPrice, expiresAt));
        setDirty();
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
        marketOrders.put(id, new MarketOrder(id, MarketOrder.Side.SELL, owner, stationId,
                item, quantity, unitPrice, expiresAt));
        setDirty();
        return new MarketResult(MarketStatus.APPLIED, id);
    }

    /** Remote purchase: money moves now, stock becomes a station-bound personal claim. */
    public MarketStatus purchase(UUID buyer, UUID orderId, int quantity, long nowMillis) {
        MarketOrder order = marketOrders.get(orderId);
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
        MarketOrder order = marketOrders.get(orderId);
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
        MarketOrder order = marketOrders.get(orderId);
        if (order == null || actor == null || !admin && !order.owner().equals(actor)) return MarketStatus.INVALID;
        if (order.side() == MarketOrder.Side.BUY) {
            long refund = (long) order.remaining() * order.unitPrice();
            if (refund > 0 && transfer(UUID.randomUUID(), Account.escrow(order.id()),
                    Account.player(order.owner()), refund, "market_buy_refund", order.id()) != Result.APPLIED)
                return MarketStatus.PAYMENT_FAILED;
        } else if (order.remaining() > 0) {
            addClaim(order.owner(), order.stationId(), order.item(), order.remaining());
        }
        marketOrders.remove(orderId);
        setDirty();
        return MarketStatus.APPLIED;
    }

    public int expireMarketOrders(long nowMillis) {
        int expired = 0;
        for (MarketOrder order : List.copyOf(marketOrders.values())) {
            if (order.expiresAt() > nowMillis) continue;
            if (cancelMarketOrder(order.owner(), order.id(), false) == MarketStatus.APPLIED) expired++;
        }
        return expired;
    }

    public List<MarketClaim> claimsAt(UUID owner, UUID stationId) {
        return marketClaims.values().stream()
                .filter(claim -> claim.owner().equals(owner) && claim.stationId().equals(stationId)).toList();
    }

    public boolean reduceClaim(UUID owner, UUID claimId, int quantity) {
        MarketClaim claim = marketClaims.get(claimId);
        if (claim == null || !claim.owner().equals(owner) || quantity < 1 || quantity > claim.quantity()) return false;
        if (quantity == claim.quantity()) marketClaims.remove(claimId);
        else marketClaims.put(claimId, new MarketClaim(claim.id(), owner, claim.stationId(),
                claim.item(), claim.quantity() - quantity));
        setDirty();
        return true;
    }

    public int outstanding(UUID stationId) {
        long count = marketClaims.values().stream().filter(c -> c.stationId().equals(stationId))
                .mapToLong(MarketClaim::quantity).sum();
        count += marketOrders.values().stream().filter(o -> o.stationId().equals(stationId)
                && o.side() == MarketOrder.Side.SELL).mapToLong(MarketOrder::remaining).sum();
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    /** Cancel demand and turn all physical stock/claims into a policy-gated local wreckage. */
    public MarketStatus wreckMarketStation(UUID stationId, UUID nationId,
                                           ResourceLocation dimension, BlockPos pos) {
        if (stationId == null || dimension == null || pos == null) return MarketStatus.INVALID;
        WreckageKey key = new WreckageKey(dimension, pos.asLong());
        if (marketWreckage.containsKey(key)) return MarketStatus.INVALID;
        for (MarketOrder order : List.copyOf(marketOrders.values())) {
            if (!order.stationId().equals(stationId)) continue;
            if (cancelMarketOrder(order.owner(), order.id(), false) != MarketStatus.APPLIED)
                return MarketStatus.PAYMENT_FAILED;
        }
        List<MarketClaim> contents = marketClaims.values().stream()
                .filter(claim -> claim.stationId().equals(stationId)).toList();
        if (contents.isEmpty()) return MarketStatus.APPLIED;
        marketWreckage.put(key, new MarketWreckage(nationId, List.copyOf(contents)));
        for (MarketClaim claim : contents) marketClaims.remove(claim.id());
        setDirty();
        return MarketStatus.APPLIED;
    }

    public MarketWreckage marketWreckage(ResourceLocation dimension, BlockPos pos) {
        return marketWreckage.get(new WreckageKey(dimension, pos.asLong()));
    }

    public boolean reduceWreckageClaim(ResourceLocation dimension, BlockPos pos,
                                       UUID claimId, int quantity) {
        WreckageKey key = new WreckageKey(dimension, pos.asLong());
        MarketWreckage wreckage = marketWreckage.get(key);
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
        if (next.isEmpty()) marketWreckage.remove(key);
        else marketWreckage.put(key, new MarketWreckage(wreckage.nationId(), List.copyOf(next)));
        setDirty();
        return true;
    }

    private int openOrders(UUID owner) {
        return (int) marketOrders.values().stream().filter(o -> o.owner().equals(owner)).count();
    }

    private void updateOrder(MarketOrder order, int filled) {
        int remainder = order.remaining() - filled;
        if (remainder == 0) marketOrders.remove(order.id());
        else marketOrders.put(order.id(), order.withRemaining(remainder));
        setDirty();
    }

    private void addClaim(UUID owner, UUID stationId, ItemStack item, int quantity) {
        UUID id = UUID.randomUUID();
        marketClaims.put(id, new MarketClaim(id, owner, stationId, item, quantity));
        setDirty();
    }

    public List<Transaction> recent(Account account, int limit) {
        if (account == null || limit <= 0) return List.of();
        if (account.kind() == Kind.PLAYER) {
            ArrayDeque<Transaction> indexed = recentByPlayer.get(account);
            return indexed == null ? List.of() : indexed.stream().limit(Math.min(limit, RECENT_INDEX_LIMIT)).toList();
        }
        return transactions.values().stream()
                .filter(tx -> account.equals(tx.from()) || account.equals(tx.to()))
                .sorted((left, right) -> Long.compare(right.occurredAt(), left.occurredAt()))
                .limit(Math.min(limit, 100)).toList();
    }

    private void indexRecent(Transaction transaction) {
        indexRecent(transaction.from(), transaction);
        if (!java.util.Objects.equals(transaction.from(), transaction.to()))
            indexRecent(transaction.to(), transaction);
    }

    private void indexRecent(Account account, Transaction transaction) {
        if (account == null || account.kind() != Kind.PLAYER) return;
        ArrayDeque<Transaction> recent = recentByPlayer.computeIfAbsent(account, ignored -> new ArrayDeque<>());
        recent.addFirst(transaction);
        if (recent.size() > RECENT_INDEX_LIMIT) recent.removeLast();
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
        ListTag eventRewardList = new ListTag();
        eventRewards.values().forEach(reward -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", reward.eventId());
            entry.putUUID("Player", reward.playerId());
            entry.putInt("Currency", reward.currency());
            ListTag items = new ListTag();
            reward.items().forEach(item -> items.add(item.save(registries)));
            entry.put("Items", items);
            eventRewardList.add(entry);
        });
        tag.put("EventRewards", eventRewardList);
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
            harvestList.add(entry);
        });
        tag.put("HarvestScores", harvestList);
        ListTag orders = new ListTag();
        marketOrders.values().forEach(order -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", order.id());
            entry.putString("Side", order.side().name());
            entry.putUUID("Owner", order.owner());
            entry.putUUID("Station", order.stationId());
            entry.put("Item", order.item().save(registries));
            entry.putInt("Remaining", order.remaining());
            entry.putLong("UnitPrice", order.unitPrice());
            entry.putLong("ExpiresAt", order.expiresAt());
            orders.add(entry);
        });
        tag.put("MarketOrders", orders);
        ListTag claims = new ListTag();
        marketClaims.values().forEach(claim -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", claim.id());
            entry.putUUID("Owner", claim.owner());
            entry.putUUID("Station", claim.stationId());
            entry.put("Item", claim.item().save(registries));
            entry.putInt("Quantity", claim.quantity());
            claims.add(entry);
        });
        tag.put("MarketClaims", claims);
        ListTag wreckages = new ListTag();
        marketWreckage.forEach((key, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", key.dimension().toString());
            entry.putLong("Pos", key.pos());
            if (value.nationId() != null) entry.putUUID("Nation", value.nationId());
            ListTag items = new ListTag();
            value.claims().forEach(claim -> {
                CompoundTag stored = new CompoundTag();
                stored.putUUID("Id", claim.id());
                stored.putUUID("Owner", claim.owner());
                stored.putUUID("Station", claim.stationId());
                stored.put("Item", claim.item().save(registries));
                stored.putInt("Quantity", claim.quantity());
                items.add(stored);
            });
            entry.put("Claims", items);
            wreckages.add(entry);
        });
        tag.put("MarketWreckage", wreckages);
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
        ListTag orders = tag.getList("MarketOrders", Tag.TAG_COMPOUND);
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
                            entry.getBoolean("Farmer")));
        }
        ListTag eventRewardList = tag.getList("EventRewards", Tag.TAG_COMPOUND);
        for (int i = 0; i < eventRewardList.size(); i++) {
            CompoundTag entry = eventRewardList.getCompound(i);
            if (!entry.hasUUID("Id") || !entry.hasUUID("Player")) continue;
            List<ItemStack> items = new java.util.ArrayList<>();
            ListTag itemList = entry.getList("Items", Tag.TAG_COMPOUND);
            for (int j = 0; j < itemList.size(); j++)
                ItemStack.parse(registries, itemList.getCompound(j)).ifPresent(items::add);
            UUID id = entry.getUUID("Id");
            UUID playerId = entry.getUUID("Player");
            data.eventRewards.put(eventRewardKey(id, playerId), new EventReward(id, playerId,
                    Math.max(0, Math.min(5, entry.getInt("Currency"))), List.copyOf(items)));
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
        for (int i = 0; i < orders.size(); i++) {
            CompoundTag entry = orders.getCompound(i);
            if (!entry.hasUUID("Id") || !entry.hasUUID("Owner") || !entry.hasUUID("Station")) continue;
            try {
                MarketOrder.Side side = MarketOrder.Side.valueOf(entry.getString("Side"));
                ItemStack.parse(registries, entry.getCompound("Item")).ifPresent(item -> {
                    int remaining = entry.getInt("Remaining");
                    long price = entry.getLong("UnitPrice");
                    if (MarketOrderRules.totalPrice(remaining, price) < 0) return;
                    UUID id = entry.getUUID("Id");
                    data.marketOrders.put(id, new MarketOrder(id, side, entry.getUUID("Owner"),
                            entry.getUUID("Station"), item, remaining, price, entry.getLong("ExpiresAt")));
                });
            } catch (IllegalArgumentException ignored) { }
        }
        ListTag claims = tag.getList("MarketClaims", Tag.TAG_COMPOUND);
        for (int i = 0; i < claims.size(); i++) {
            CompoundTag entry = claims.getCompound(i);
            if (!entry.hasUUID("Id") || !entry.hasUUID("Owner") || !entry.hasUUID("Station")) continue;
            ItemStack.parse(registries, entry.getCompound("Item")).ifPresent(item -> {
                int quantity = entry.getInt("Quantity");
                if (quantity < 1 || quantity > MarketOrderRules.MAX_ORDER_QUANTITY) return;
                UUID id = entry.getUUID("Id");
                data.marketClaims.put(id, new MarketClaim(id, entry.getUUID("Owner"),
                        entry.getUUID("Station"), item, quantity));
            });
        }
        ListTag wreckages = tag.getList("MarketWreckage", Tag.TAG_COMPOUND);
        for (int i = 0; i < wreckages.size(); i++) {
            CompoundTag entry = wreckages.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) continue;
            List<MarketClaim> contents = new java.util.ArrayList<>();
            ListTag items = entry.getList("Claims", Tag.TAG_COMPOUND);
            for (int j = 0; j < items.size(); j++) {
                CompoundTag stored = items.getCompound(j);
                if (!stored.hasUUID("Id") || !stored.hasUUID("Owner") || !stored.hasUUID("Station")) continue;
                ItemStack.parse(registries, stored.getCompound("Item")).ifPresent(item -> {
                    int quantity = stored.getInt("Quantity");
                    if (quantity > 0 && quantity <= MarketOrderRules.MAX_ORDER_QUANTITY)
                        contents.add(new MarketClaim(stored.getUUID("Id"), stored.getUUID("Owner"),
                                stored.getUUID("Station"), item, quantity));
                });
            }
            if (!contents.isEmpty()) data.marketWreckage.put(new WreckageKey(dimension, entry.getLong("Pos")),
                    new MarketWreckage(entry.hasUUID("Nation") ? entry.getUUID("Nation") : null,
                            List.copyOf(contents)));
        }
        return data;
    }

    public static EconomyLedgerSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EconomyLedgerSavedData::new, EconomyLedgerSavedData::load, null),
                "moveearth_economy_ledger");
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
    public enum MarketStatus { APPLIED, INVALID, PAYMENT_FAILED }
    public record MarketResult(MarketStatus status, UUID orderId) { }
    public record EventReward(UUID eventId, UUID playerId, int currency, List<ItemStack> items) { }
    public record HarvestScore(String name, int points, boolean farmer) { }
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
    private record WreckageKey(ResourceLocation dimension, long pos) { }
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
