package com.ruskserver.moveearth_addtional.handler;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationOnboardingService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.combat.CombatTagService;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerService;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeSavedData;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.terrain.TerrainTileStore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class RandomSpawnHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RandomSpawnHandler.class);
    private static final String NBT_KEY_SPAWNED = "MoveEarthRandomSpawned";
    private static final String NBT_KEY_LAST_POS = "MoveEarthLastRandomSpawn";
    private static final String NBT_KEY_LAST_DIMENSION = "MoveEarthLastRandomSpawnDimension";
    private static final String NBT_KEY_RETRY_AFTER = "MoveEarthRandomSpawnRetryAfter";

    private static final int MIN_WORLD_SPAWN_RADIUS = 750;
    private static final int FALLBACK_MAX_WORLD_SPAWN_RADIUS = 4_000;
    private static final double SPAWN_POOL_SPACING_SQR = 96.0D * 96.0D;
    private static final int MIN_PLAYER_DISTANCE = 384;
    /** What a timed-out or exhausted search still requires from hostile players: relaxed, never zero. */
    private static final int FALLBACK_MIN_PLAYER_DISTANCE =
            RandomSpawnScheduler.fallbackPlayerDistance(MIN_PLAYER_DISTANCE);
    /** A search that found nothing clear of hostile players keeps the player at the vanilla spawn and tries again. */
    private static final int MAX_AUTO_RETRIES = 2;
    private static final int AUTO_RETRY_DELAY_TICKS = 10 * 20;
    private static final int MIN_LAST_SPAWN_DISTANCE = 768;
    private static final int MAX_CANDIDATES = 24;
    /**
     * Chunk reads in flight across all searches. One made 40 players joining at once, or a
     * mass death, wait 30-90 s at world spawn; each request is still one saved FULL chunk.
     */
    private static final int MAX_CONCURRENT_CHUNK_LOADS = 6;
    private static final int SEARCH_TIMEOUT_TICKS = 20 * 20;
    /** A search still waiting for its first slot after this long uses an already loaded candidate. */
    private static final int QUEUE_TIMEOUT_TICKS = 30 * 20;
    private static final int RETRY_COOLDOWN_TICKS = 2 * 60 * 20;
    private static final int SPAWN_POOL_LEASE_TICKS = 30 * 20;
    private static final int SPAWN_POOL_REUSE_COOLDOWN_TICKS = 10 * 60 * 20;
    // Distance 0 requests only a FULL target chunk. Distance 1 would promote the
    // target to BLOCK_TICKING and needlessly increase the generated region.
    private static final int RANDOM_SPAWN_TICKET_DISTANCE = 0;
    private static final int SPAWN_PROTECTION_TICKS = 10 * 20;
    private static final TicketType<UUID> RANDOM_SPAWN_TICKET = TicketType.create(
            "moveearth_random_spawn", Comparator.comparing(UUID::toString), SEARCH_TIMEOUT_TICKS + 40);
    private static final Map<UUID, SpawnSearch> PENDING_SEARCHES = new HashMap<>();
    private static final RandomSpawnScheduler<UUID> SCHEDULER = new RandomSpawnScheduler<>();
    /** Players waiting at the vanilla spawn for an automatic retry of a failed search. */
    private static final Map<UUID, PendingRetry> PENDING_RETRIES = new HashMap<>();

    private RandomSpawnHandler() {
    }

    @SubscribeEvent
    public static void onChunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level != level.getServer().overworld()
                || !(event.getChunk() instanceof LevelChunk chunk)) return;

        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        BlockPos center = level.getSharedSpawnPos();
        double minimum = effectiveMinRadius(level, center);
        double maximum = effectiveMaxRadius(level, center);
        RandomSpawnSavedData pool = RandomSpawnSavedData.get(level.getServer());
        int collectionTarget = recommendedPoolTarget(level);
        if (pool.mapping() != null) collectionTarget = Math.max(collectionTarget, pool.mapping().target());
        if (collectionTarget <= 0 || pool.size() >= collectionTarget) return;
        if (pool.hasNearby(new BlockPos(baseX + 8, level.getSeaLevel(), baseZ + 8),
                90.0D * 90.0D)) return;
        int[][] offsets = {{4, 4}, {12, 12}, {4, 12}, {12, 4}};
        for (int[] offset : offsets) {
            int x = baseX + offset[0];
            int z = baseZ + offset[1];
            // Column-only checks first: the surface search below reads blocks and structures.
            BlockPos column = new BlockPos(x, center.getY(), z);
            if (!level.getWorldBorder().isWithinBounds(column) || !insideTerrainFootprint(column)) continue;
            double distance = horizontalDistanceSqr(column, center);
            if (distance < square(minimum) || distance > square(maximum)) continue;
            BlockPos safe = findSafeSurface(level, chunk, x, z);
            if (safe == null || !level.canSeeSky(safe)) continue;
            if (pool.hasNearby(safe, SPAWN_POOL_SPACING_SQR)) continue;
            if (!isAllowedTerritory(level, safe, null)) continue;
            pool.remember(safe, RandomSpawnSavedData.Source.PASSIVE, level.getGameTime());
            break;
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = persistedData(player);
        if (data.getBoolean(NBT_KEY_SPAWNED)) return;
        if (com.ruskserver.moveearth_addtional.TimeRestrictionHandler.rejectsLogin(player)) {
            // Closed hours: the player is about to be disconnected, which saves their
            // file. Mark a first join as still pending so the next login starts the
            // flow instead of reading that file as a returning player.
            if (!NationOnboardingService.pending(player) && !hasPriorPlayerHistory(player)) {
                NationOnboardingService.markPending(player);
            }
            return;
        }
        if (NationOnboardingService.pending(player)) {
            NationOnboardingService.begin(player);
            return;
        }
        if (hasPriorPlayerHistory(player)) {
            data.putBoolean(NBT_KEY_SPAWNED, true);
            LOGGER.info("Player {} predates random-spawn tracking. Marking random spawn as initialized without teleporting.",
                    player.getName().getString());
            return;
        }

        LOGGER.info("Player {} logged in for the first time. Opening the spawn and nation onboarding flow.",
                player.getName().getString());
        NationOnboardingService.begin(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getRespawnPosition() != null) return;

        LOGGER.info("Player {} respawned without a bed or anchor. Starting a non-blocking random-spawn search.",
                player.getName().getString());
        player.sendSystemMessage(com.ruskserver.moveearth_addtional.ui.MoveEarthMessage.tip(
                Component.translatable("message.moveearth_addtional.spawn.bed_hint")));
        beginRandomSpawnSearch(player, false);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SpawnSearch search = removeSearch(player.getUUID());
        if (search != null) releaseTicket(player.server.overworld(), player.getUUID(), search);
        PENDING_RETRIES.remove(player.getUUID());
    }

    /**
     * A player waiting for a spawn search stands at the vanilla spawn, often near others
     * who respawned there: they take no damage, so they also pick up no combat tag, and
     * cannot hurt anyone until they have been placed. {@code /kill} and the void still apply.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (PENDING_SEARCHES.isEmpty() && PENDING_RETRIES.isEmpty()) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if ((event.getEntity() instanceof ServerPlayer victim && awaitingSpawn(victim.getUUID()))
                || (event.getSource().getEntity() instanceof ServerPlayer attacker
                && awaitingSpawn(attacker.getUUID()))) {
            event.setCanceled(true);
        }
    }

    private static boolean awaitingSpawn(UUID playerId) {
        return PENDING_SEARCHES.containsKey(playerId) || PENDING_RETRIES.containsKey(playerId);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING_SEARCHES.isEmpty() && PENDING_RETRIES.isEmpty()) return;

        ServerLevel level = event.getServer().overworld();
        int currentTick = event.getServer().getTickCount();
        if (!PENDING_RETRIES.isEmpty()) startDueRetries(event.getServer(), currentTick);
        if (PENDING_SEARCHES.isEmpty()) return;
        Iterator<Map.Entry<UUID, SpawnSearch>> iterator = PENDING_SEARCHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SpawnSearch> entry = iterator.next();
            UUID playerId = entry.getKey();
            SpawnSearch search = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                releaseTicket(level, playerId, search);
                iterator.remove();
                SCHEDULER.remove(playerId);
                continue;
            }
            if (player.isRemoved()) continue;

            if (search.storageProbe != null && search.storageProbe.isDone()) {
                Optional<CompoundTag> stored = Optional.empty();
                try {
                    stored = search.storageProbe.join();
                } catch (CompletionException exception) {
                    LOGGER.debug("Could not inspect stored random-spawn chunk {}", search.requestedChunk,
                            exception.getCause());
                }
                search.storageProbe = null;
                if (stored.filter(tag -> RandomSpawnPolicy.isStoredFullChunk(tag.getString("Status"))).isEmpty()) {
                    search.rejectedByStorage++;
                    search.requestedChunk = null;
                    search.requestedColumn = null;
                } else {
                    search.ticketActive = true;
                    level.getChunkSource().addRegionTicket(
                            RANDOM_SPAWN_TICKET, search.requestedChunk,
                            RANDOM_SPAWN_TICKET_DISTANCE, playerId);
                }
            }

            if (search.ticketActive) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                        search.requestedChunk.x, search.requestedChunk.z);
                if (chunk != null && evaluateLoadedChunk(player, level, playerId, search, chunk)) {
                    iterator.remove();
                    SCHEDULER.remove(playerId);
                    continue;
                }
            }

            boolean timedOut = search.startedTick >= 0 && currentTick >= search.startedTick + SEARCH_TIMEOUT_TICKS;
            boolean queueTimedOut = search.startedTick < 0
                    && RandomSpawnScheduler.queueTimedOut(search.queuedTick, currentTick, QUEUE_TIMEOUT_TICKS);
            if (timedOut || queueTimedOut) {
                String reason = timedOut ? "timed out" : "waited too long for a chunk-load slot";
                if (!finishWithLoadedFallback(player, level, playerId, search)) {
                    failSearch(player, level, playerId, search, reason);
                } else {
                    LOGGER.info("Random-spawn search for {} {}; settled on an already loaded candidate.",
                            player.getName().getString(), reason);
                }
                iterator.remove();
                SCHEDULER.remove(playerId);
            }
        }

        int availableSlots = RandomSpawnScheduler.availableSlots(MAX_CONCURRENT_CHUNK_LOADS, activeRequestCount());
        if (availableSlots <= 0 || PENDING_SEARCHES.isEmpty()) return;

        // Least recently served first, so concurrent searches share the slots in turn.
        for (UUID playerId : SCHEDULER.serviceOrder()) {
            if (availableSlots <= 0) break;
            SpawnSearch search = PENDING_SEARCHES.get(playerId);
            if (search == null) {
                SCHEDULER.remove(playerId);
                continue;
            }
            if (search.ticketActive || search.storageProbe != null) continue;

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                removeSearch(playerId);
                continue;
            }
            if (player.isRemoved()) continue;

            SpawnColumn next = search.nextRequest();
            if (next == null) {
                failSearch(player, level, playerId, search, "exhausted all candidates");
                removeSearch(playerId);
                continue;
            }

            if (next.pooled && !search.loadingFallback
                    && !RandomSpawnSavedData.get(event.getServer()).reserve(
                    new BlockPos(next.x, level.getMinBuildHeight(), next.z),
                    level.getGameTime(), SPAWN_POOL_LEASE_TICKS)) {
                continue;
            }

            search.requestedColumn = next;
            search.requestedChunk = new ChunkPos(next.x >> 4, next.z >> 4);
            if (search.startedTick < 0) search.startedTick = currentTick;
            LevelChunk alreadyLoaded = level.getChunkSource().getChunkNow(
                    search.requestedChunk.x, search.requestedChunk.z);
            if (alreadyLoaded != null) {
                if (evaluateLoadedChunk(player, level, playerId, search, alreadyLoaded)) {
                    removeSearch(playerId);
                } else {
                    SCHEDULER.served(playerId);
                }
                continue;
            }
            // ChunkStorage.read is asynchronous and returns Optional.empty for an
            // ungenerated coordinate. A FULL ticket is issued only after a saved
            // full chunk is confirmed, so this search cannot trigger worldgen.
            search.storageProbe = level.getChunkSource().chunkMap.read(search.requestedChunk);
            SCHEDULER.served(playerId);
            availableSlots--;
        }
    }

    private static SpawnSearch removeSearch(UUID playerId) {
        SCHEDULER.remove(playerId);
        return PENDING_SEARCHES.remove(playerId);
    }

    private static void enqueueSearch(ServerPlayer player, SpawnSearch search, int attempt) {
        search.attempt = attempt;
        search.queuedTick = player.server.getTickCount();
        PENDING_SEARCHES.put(player.getUUID(), search);
        SCHEDULER.add(player.getUUID());
    }

    /**
     * Ends a search that ran out of time on a candidate whose chunk is already loaded,
     * best-scored first, so it still never generates or waits for a chunk. The distance
     * from the player's last spawn is dropped and the one from hostile players relaxed to
     * {@link #FALLBACK_MIN_PLAYER_DISTANCE}, measured where they stand now, since the
     * best-scored one was measured earlier and they may have walked up to it. Loaded
     * chunks are mostly around players, so this check is what keeps the fallback from
     * landing next to them. False when no candidate qualifies; the caller then keeps the
     * player at the vanilla spawn and retries.
     */
    private static boolean finishWithLoadedFallback(ServerPlayer player, ServerLevel level, UUID playerId,
                                                    SpawnSearch search) {
        releaseTicket(level, playerId, search);
        List<ServerPlayer> threats = eligibleOtherPlayers(player, level, search.friendlyNationId);
        SpawnColumn column = null;
        LevelChunk chunk = null;
        if (search.safestFallback != null) {
            SpawnColumn best = search.safestFallback.column;
            LevelChunk loaded = level.getChunkSource().getChunkNow(best.x >> 4, best.z >> 4);
            BlockPos spawn = loaded == null ? null : findSafeSurface(level, loaded, best.x, best.z);
            if (spawn != null && isAllowedTerritory(level, spawn, search.friendlyNationId)
                    && clearOfThreats(spawn, threats)) {
                column = best;
                chunk = loaded;
            }
        }
        RandomSpawnSavedData pool = RandomSpawnSavedData.get(player.server);
        for (int index = search.nextColumnIndex; column == null && index < search.columns.size(); index++) {
            SpawnColumn candidate = search.columns.get(index);
            LevelChunk loaded = level.getChunkSource().getChunkNow(candidate.x >> 4, candidate.z >> 4);
            if (loaded == null) continue;
            // Never evaluated by this search: the territory rule still applies to it.
            BlockPos spawn = findSafeSurface(level, loaded, candidate.x, candidate.z);
            if (spawn == null || !isAllowedTerritory(level, spawn, search.friendlyNationId)
                    || !clearOfThreats(spawn, threats)) continue;
            if (candidate.pooled && !pool.reserve(new BlockPos(candidate.x, level.getMinBuildHeight(), candidate.z),
                    level.getGameTime(), SPAWN_POOL_LEASE_TICKS)) continue;
            column = candidate;
            chunk = loaded;
        }
        if (column == null) return false;
        search.fallbackRequested = true;
        search.loadingFallback = true;
        search.requestedColumn = column;
        search.requestedChunk = new ChunkPos(column.x >> 4, column.z >> 4);
        return evaluateLoadedChunk(player, level, playerId, search, chunk);
    }

    private static void startDueRetries(MinecraftServer server, int currentTick) {
        List<Map.Entry<UUID, PendingRetry>> due = new ArrayList<>();
        Iterator<Map.Entry<UUID, PendingRetry>> iterator = PENDING_RETRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingRetry> entry = iterator.next();
            if (!RandomSpawnScheduler.retryDue(entry.getValue().dueTick(), currentTick)) continue;
            due.add(entry);
            iterator.remove();
        }
        for (Map.Entry<UUID, PendingRetry> entry : due) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            PendingRetry retry = entry.getValue();
            if (retry.nationId() != null) {
                beginNationSpawnSearch(player, retry.nationId(), retry.attempt());
            } else {
                beginRandomSpawnSearch(player, retry.markInitializedOnSuccess(), retry.attempt());
            }
        }
    }

    /**
     * Nothing was clear of hostile players (or safe) in time: the player stays where the
     * vanilla flow put them, protected, and the search runs again shortly, a bounded
     * number of times, before the vanilla spawn is kept for good.
     */
    private static void retryOrFinish(ServerPlayer player, SpawnSearch search, String reason) {
        if (RandomSpawnScheduler.mayRetry(search.attempt, MAX_AUTO_RETRIES)) {
            PENDING_RETRIES.put(player.getUUID(), new PendingRetry(
                    player.server.getTickCount() + AUTO_RETRY_DELAY_TICKS, search.attempt + 1,
                    search.markInitializedOnSuccess, search.friendlyNationId));
            player.displayClientMessage(Component.translatable(
                    "message.moveearth_addtional.spawn.retrying", AUTO_RETRY_DELAY_TICKS / 20), true);
            LOGGER.info("Random-spawn search for {} {}; retry {} of {} in {} s at the vanilla spawn.",
                    player.getName().getString(), reason, search.attempt + 1, MAX_AUTO_RETRIES,
                    AUTO_RETRY_DELAY_TICKS / 20);
            return;
        }
        finishWithoutRandomSpawn(player, search.markInitializedOnSuccess, reason);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel level = event.getServer().overworld();
        PENDING_SEARCHES.forEach((playerId, search) -> releaseTicket(level, playerId, search));
        PENDING_SEARCHES.clear();
        SCHEDULER.clear();
        PENDING_RETRIES.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING_SEARCHES.clear();
        SCHEDULER.clear();
        PENDING_RETRIES.clear();
    }

    private static void beginRandomSpawnSearch(ServerPlayer player, boolean markInitializedOnSuccess) {
        beginRandomSpawnSearch(player, markInitializedOnSuccess, 0);
    }

    /** {@code attempt} is above 0 for an automatic retry, which skips the manual retry cooldown. */
    private static void beginRandomSpawnSearch(ServerPlayer player, boolean markInitializedOnSuccess, int attempt) {
        if (PENDING_SEARCHES.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.literal("安全な出現地点を探索中です…"), true);
            return;
        }
        // A new search replaces a retry still waiting.
        PENDING_RETRIES.remove(player.getUUID());
        if (!canRelocate(player)) {
            player.displayClientMessage(Component.literal(
                    "戦闘中または拘束中はランダムスポーンを利用できません。"), true);
            if (markInitializedOnSuccess) NationOnboardingService.searchFailed(player);
            return;
        }
        ServerLevel level = player.server.overworld();
        CompoundTag data = persistedData(player);
        long currentTick = level.getGameTime();
        long retryAfter = data.getLong(NBT_KEY_RETRY_AFTER);
        if (attempt == 0 && !RandomSpawnPolicy.retryAllowed(currentTick, retryAfter)) {
            long seconds = Math.max(1L, (retryAfter - currentTick + 19L) / 20L);
            player.displayClientMessage(Component.literal(
                    "ランダムスポーンの再試行はあと" + seconds + "秒後に利用できます。"), true);
            if (markInitializedOnSuccess) NationOnboardingService.searchFailed(player);
            return;
        }
        BlockPos worldSpawn = level.getSharedSpawnPos();
        BlockPos lastSpawn = null;
        if (data.contains(NBT_KEY_LAST_POS)
                && level.dimension().location().toString().equals(data.getString(NBT_KEY_LAST_DIMENSION))) {
            lastSpawn = BlockPos.of(data.getLong(NBT_KEY_LAST_POS));
        }

        RandomSource random = player.getRandom();
        double minRadius = effectiveMinRadius(level, worldSpawn);
        double maxRadius = effectiveMaxRadius(level, worldSpawn);
        List<SpawnColumn> columns = cachedColumns(level, random, position -> {
            double distance = horizontalDistanceSqr(position, worldSpawn);
            return distance >= square(minRadius) && distance <= square(maxRadius);
        }, MAX_CANDIDATES);

        if (columns.isEmpty()) {
            finishWithoutRandomSpawn(player, markInitializedOnSuccess,
                    "no generated safe positions were available");
            return;
        }

        enqueueSearch(player, new SpawnSearch(
                columns, lastSpawn, random.nextFloat() * 360.0F - 180.0F,
                markInitializedOnSuccess, null, worldSpawn, minRadius, maxRadius, 0), attempt);
        player.displayClientMessage(Component.literal("安全なランダムスポーン地点を探索しています…"), true);
    }

    public static void beginInitialRandomSpawn(ServerPlayer player) {
        beginRandomSpawnSearch(player, true);
    }

    public static void beginNationSpawnSearch(ServerPlayer player, UUID nationId) {
        beginNationSpawnSearch(player, nationId, 0);
    }

    private static void beginNationSpawnSearch(ServerPlayer player, UUID nationId, int attempt) {
        if (PENDING_SEARCHES.containsKey(player.getUUID())) {
            player.displayClientMessage(Component.literal("安全な出現地点を探索中です…"), true);
            return;
        }
        PENDING_RETRIES.remove(player.getUUID());
        if (!canRelocate(player)) {
            player.displayClientMessage(Component.literal(
                    "戦闘中または拘束中は国家周辺へ移動できません。"), true);
            NationOnboardingService.searchFailed(player);
            return;
        }
        ServerLevel level = player.server.overworld();
        NationSavedData nations = NationSavedData.get(player.server);
        if (nations.nationIdFor(player.getUUID()).filter(nationId::equals).isEmpty()) {
            NationOnboardingService.searchFailed(player);
            return;
        }
        if (SiegeSavedData.get(player.server).isNationLocked(nationId)) {
            player.sendSystemMessage(com.ruskserver.moveearth_addtional.ui.MoveEarthMessage.warning("選択した国家がSiege中のため、国家周辺へは出現できません。"));
            beginRandomSpawnSearch(player, true, attempt);
            return;
        }

        RandomSource random = player.getRandom();
        List<SpawnColumn> columns = new ArrayList<>(MAX_CANDIDATES);
        TerritorySavedData territories = TerritorySavedData.get(player.server);
        var cores = territories.cores().stream()
                .filter(core -> core.nationId().equals(nationId))
                .filter(core -> core.dimension().equals(level.dimension().location()))
                .filter(core -> core.state() == TerritorySavedData.CoreState.ACTIVE)
                .toList();
        int coreSourceIndex = 0;
        while (!cores.isEmpty() && columns.size() < 16) {
            TerritorySavedData.CoreRecord core = cores.get(coreSourceIndex++ % cores.size());
            int radius = NationUpkeepService.effectiveTerritoryRadius(
                    player.server, nationId, core.radius());
            int minX = ((core.pos().getX() >> 4) - radius) << 4;
            int maxX = ((((core.pos().getX() >> 4) + radius) + 1) << 4) - 1;
            int minZ = ((core.pos().getZ() >> 4) - radius) << 4;
            int maxZ = ((((core.pos().getZ() >> 4) + radius) + 1) << 4) - 1;
            columns.add(new SpawnColumn(random.nextIntBetweenInclusive(minX, maxX),
                    random.nextIntBetweenInclusive(minZ, maxZ), random.nextDouble() * 10_000.0D, false));
        }
        NationSavedData.Nation nation = nations.nation(nationId).orElse(null);
        List<ServerPlayer> members = List.of();
        if (nation != null) {
            members = nation.members().keySet().stream()
                    .map(id -> player.server.getPlayerList().getPlayer(id))
                    .filter(java.util.Objects::nonNull)
                    .filter(other -> other != player && other.level().dimension().equals(level.dimension()))
                    .filter(other -> !other.isSpectator())
                    .toList();
            int sourceIndex = 0;
            while (!members.isEmpty() && columns.size() < 22) {
                ServerPlayer member = members.get(sourceIndex++ % members.size());
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = 48.0D + random.nextDouble() * 80.0D;
                int x = MthFloor(member.getX() + Math.cos(angle) * distance);
                int z = MthFloor(member.getZ() + Math.sin(angle) * distance);
                if (level.hasChunk(x >> 4, z >> 4)) {
                    columns.add(new SpawnColumn(x, z, random.nextDouble() * 10_000.0D, false));
                }
            }
        }
        List<ServerPlayer> nationMembers = members;
        columns.addAll(cachedColumns(level, random, position -> {
            int chunkX = position.getX() >> 4;
            int chunkZ = position.getZ() >> 4;
            for (TerritorySavedData.CoreRecord core : cores) {
                int radius = NationUpkeepService.effectiveTerritoryRadius(
                        player.server, nationId, core.radius());
                if (Math.abs(chunkX - (core.pos().getX() >> 4)) <= radius
                        && Math.abs(chunkZ - (core.pos().getZ() >> 4)) <= radius) return true;
            }
            for (ServerPlayer member : nationMembers) {
                if (horizontalDistanceSqr(position, member.blockPosition()) <= square(160.0D)) return true;
            }
            return false;
        }, Math.max(0, MAX_CANDIDATES - columns.size())));
        appendWildernessFallbacks(level, random, columns);
        if (columns.isEmpty()) {
            finishWithoutRandomSpawn(player, true, "no generated nation or wilderness positions were available");
            return;
        }
        BlockPos worldSpawn = level.getSharedSpawnPos();
        enqueueSearch(player, new SpawnSearch(columns, null,
                random.nextFloat() * 360.0F - 180.0F, true, nationId, worldSpawn,
                0.0D, effectiveMaxRadius(level, worldSpawn), 0), attempt);
        player.displayClientMessage(Component.literal("国家周辺の安全な出現地点を探索しています…"), true);
    }

    private static void appendWildernessFallbacks(ServerLevel level, RandomSource random,
                                                   List<SpawnColumn> columns) {
        BlockPos center = level.getSharedSpawnPos();
        double minimum = effectiveMinRadius(level, center);
        double maximum = effectiveMaxRadius(level, center);
        if (maximum <= minimum || columns.size() >= MAX_CANDIDATES) return;
        columns.addAll(cachedColumns(level, random, position -> {
            double distance = horizontalDistanceSqr(position, center);
            return distance >= square(minimum) && distance <= square(maximum);
        }, MAX_CANDIDATES - columns.size()));
    }

    private static boolean evaluateLoadedChunk(ServerPlayer player, ServerLevel level, UUID playerId,
                                               SpawnSearch search, LevelChunk chunk) {
        SpawnColumn column = search.requestedColumn;
        BlockPos spawn = findSafeSurface(level, chunk, column.x, column.z);

        if (search.loadingFallback) {
            // The best-scored candidate may have been below the normal player distance when
            // scored, and players move: the relaxed rule is checked again on where they are now.
            boolean usable = spawn != null && isAllowedTerritory(level, spawn, search.friendlyNationId);
            boolean clear = usable && clearOfThreats(
                    spawn, eligibleOtherPlayers(player, level, search.friendlyNationId));
            boolean teleported = clear && applyRandomTeleport(
                    player, level, spawn, search.yaw, search.friendlyNationId);
            releaseTicket(level, playerId, search);
            if (teleported) {
                RandomSpawnSavedData.get(player.server).markUsed(
                        spawn, level.getGameTime(), SPAWN_POOL_REUSE_COOLDOWN_TICKS);
                finishSuccessfulSearch(player, search);
            } else {
                // A spot that is only crowded right now stays in the pool.
                if (!usable) RandomSpawnSavedData.get(player.server).forgetColumn(column.x, column.z);
                retryOrFinish(player, search, usable
                        ? "the selected fallback was too close to other players"
                        : "the selected fallback was no longer safe");
            }
            return true;
        }

        if (spawn == null) {
            search.rejectedBySurface++;
            RandomSpawnSavedData.get(player.server).forgetColumn(column.x, column.z);
            releaseTicket(level, playerId, search);
            return false;
        }
        if (!isAllowedTerritory(level, spawn, search.friendlyNationId)) {
            RandomSpawnSavedData.get(player.server).forgetColumn(column.x, column.z);
            releaseTicket(level, playerId, search);
            return false;
        }

        List<ServerPlayer> nearbyThreats = eligibleOtherPlayers(player, level, search.friendlyNationId);
        double playerDistance = minimumDistanceSqr(spawn, nearbyThreats);
        double lastDistance = search.lastSpawn == null
                ? Double.POSITIVE_INFINITY : horizontalDistanceSqr(spawn, search.lastSpawn);
        double score = RandomSpawnPolicy.score(
                playerDistance, lastDistance, column.tieBreaker, square(search.maxRadius));
        if (search.safestFallback == null || score > search.safestFallback.score) {
            search.safestFallback = new SpawnCandidate(spawn, score, column);
        }

        if (RandomSpawnPolicy.meetsDistanceRequirements(
                playerDistance, lastDistance,
                square(MIN_PLAYER_DISTANCE), square(MIN_LAST_SPAWN_DISTANCE))) {
            boolean teleported = applyRandomTeleport(player, level, spawn, search.yaw, search.friendlyNationId);
            releaseTicket(level, playerId, search);
            if (teleported) {
                RandomSpawnSavedData.get(player.server).markUsed(
                        spawn, level.getGameTime(), SPAWN_POOL_REUSE_COOLDOWN_TICKS);
                finishSuccessfulSearch(player, search);
                return true;
            }
        } else {
            releaseTicket(level, playerId, search);
        }
        return false;
    }

    private static void finishSuccessfulSearch(ServerPlayer player, SpawnSearch search) {
        if (search.markInitializedOnSuccess) {
            persistedData(player).putBoolean(NBT_KEY_SPAWNED, true);
            NationOnboardingService.complete(player);
        }
    }

    private static void failSearch(ServerPlayer player, ServerLevel level, UUID playerId,
                                   SpawnSearch search, String reason) {
        releaseTicket(level, playerId, search);
        LOGGER.warn("Random-spawn search for {} {} after evaluating {} of {} queued candidates in {} "
                        + "(worldSpawn={}, radius={}..{}, borderRejected={}, surfaceRejected={}, "
                        + "storageRejected={}, border=[{}..{}, {}..{}]); keeping vanilla spawn.",
                player.getName().getString(), reason, search.nextColumnIndex, search.columns.size(),
                level.dimension().location(), search.worldSpawn,
                Math.round(search.minRadius), Math.round(search.maxRadius),
                search.rejectedByBorder, search.rejectedBySurface,
                search.rejectedByStorage,
                Math.round(level.getWorldBorder().getMinX()), Math.round(level.getWorldBorder().getMaxX()),
                Math.round(level.getWorldBorder().getMinZ()), Math.round(level.getWorldBorder().getMaxZ()));
        retryOrFinish(player, search, reason);
    }

    private static void finishWithoutRandomSpawn(ServerPlayer player, boolean finishOnboarding, String reason) {
        CompoundTag data = persistedData(player);
        data.putLong(NBT_KEY_RETRY_AFTER, player.server.overworld().getGameTime() + RETRY_COOLDOWN_TICKS);
        player.displayClientMessage(Component.literal(
                "生成済みの安全地点を確保できなかったため、通常のスポーン地点を使用します。"), true);
        if (finishOnboarding) {
            data.putBoolean(NBT_KEY_SPAWNED, true);
            NationOnboardingService.complete(player);
        }
        LOGGER.info("Random-spawn fallback completed for {}: {}.", player.getName().getString(), reason);
    }

    private static List<SpawnColumn> cachedColumns(ServerLevel level, RandomSource random,
                                                    Predicate<BlockPos> filter, int limit) {
        if (limit <= 0) return List.of();
        List<BlockPos> positions = new ArrayList<>(RandomSpawnSavedData.get(level.getServer())
                .availablePositions(level.getGameTime()));
        Collections.shuffle(positions, new Random(random.nextLong()));
        List<SpawnColumn> result = new ArrayList<>(Math.min(limit, positions.size()));
        for (BlockPos position : positions) {
            if (result.size() >= limit) break;
            if (!level.getWorldBorder().isWithinBounds(position) || !filter.test(position)) continue;
            result.add(new SpawnColumn(position.getX(), position.getZ(),
                    random.nextDouble() * 10_000.0D, true));
        }
        return result;
    }

    private static void releaseTicket(ServerLevel level, UUID playerId, SpawnSearch search) {
        if (search.ticketActive && search.requestedChunk != null) {
            level.getChunkSource().removeRegionTicket(
                    RANDOM_SPAWN_TICKET, search.requestedChunk, RANDOM_SPAWN_TICKET_DISTANCE, playerId);
        }
        search.ticketActive = false;
        search.storageProbe = null;
        search.requestedChunk = null;
        search.requestedColumn = null;
    }

    private static int activeRequestCount() {
        int count = 0;
        for (SpawnSearch search : PENDING_SEARCHES.values()) {
            if (search.ticketActive || search.storageProbe != null) count++;
        }
        return count;
    }

    static boolean hasPendingSearches() {
        return !PENDING_SEARCHES.isEmpty();
    }

    private static boolean applyRandomTeleport(ServerPlayer player, ServerLevel level, BlockPos spawn, float yaw,
                                               UUID friendlyNationId) {
        if (!canRelocate(player)) return false;
        double targetX = spawn.getX() + 0.5D;
        double targetY = spawn.getY();
        double targetZ = spawn.getZ() + 0.5D;
        player.teleportTo(level, targetX, targetY, targetZ, yaw, 0.0F);

        double dx = player.getX() - targetX;
        double dy = player.getY() - targetY;
        double dz = player.getZ() - targetZ;
        if (!player.serverLevel().dimension().equals(level.dimension())
                || dx * dx + dy * dy + dz * dz > 1.0D) return false;

        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
                SPAWN_PROTECTION_TICKS, 4, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
                SPAWN_PROTECTION_TICKS, 0, false, true, true));
        // Also used for the first spawn, so it must not say "respawned".
        player.displayClientMessage(Component.translatable("message.moveearth_addtional.spawn.arrived"), true);

        CompoundTag data = persistedData(player);
        data.putLong(NBT_KEY_LAST_POS, spawn.asLong());
        data.putString(NBT_KEY_LAST_DIMENSION, level.dimension().location().toString());
        List<ServerPlayer> nearbyThreats = eligibleOtherPlayers(player, level, friendlyNationId);
        LOGGER.info("Random-spawned {} at {}, {}, {}; nearest player distance={} blocks.",
                player.getName().getString(), spawn.getX(), spawn.getY(), spawn.getZ(),
                nearbyThreats.isEmpty() ? "none" : Math.round(Math.sqrt(minimumDistanceSqr(spawn, nearbyThreats))));
        return true;
    }

    private static boolean canRelocate(ServerPlayer player) {
        return !CombatTagService.isTagged(player) && !PrisonerService.isMovementRestricted(player);
    }

    private static CompoundTag persistedData(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }

    private static boolean hasPriorPlayerHistory(ServerPlayer player) {
        Path playerDataDirectory = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        String fileName = player.getStringUUID() + ".dat";
        if (Files.isRegularFile(playerDataDirectory.resolve(fileName))
                || Files.isRegularFile(playerDataDirectory.resolve(fileName + "_old"))) return true;
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0
                || player.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) > 0;
    }

    private static boolean isSafe(ServerLevel level, BlockPos spawn) {
        BlockPos floorPos = spawn.below();
        BlockState floor = level.getBlockState(floorPos);
        BlockState feet = level.getBlockState(spawn);
        BlockState head = level.getBlockState(spawn.above());
        if (!level.getFluidState(spawn).isEmpty() || !level.getFluidState(spawn.above()).isEmpty()) return false;
        if (floor.getCollisionShape(level, floorPos).isEmpty()) return false;
        if (!feet.getCollisionShape(level, spawn).isEmpty()
                || !head.getCollisionShape(level, spawn.above()).isEmpty()) return false;
        return !isDangerous(floor) && !isDangerous(feet) && !isDangerous(head);
    }

    private static BlockPos findSafeSurface(ServerLevel level, LevelChunk chunk, int x, int z) {
        int localX = x & 15;
        int localZ = z & 15;
        int motionBlockingY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ) + 1;
        int worldSurfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) + 1;
        BlockPos result = findSafeNear(level, x, z, motionBlockingY);
        if (result == null && worldSurfaceY != motionBlockingY) result = findSafeNear(level, x, z, worldSurfaceY);
        return result == null || onStructure(level, chunk, result) ? null : result;
    }

    /**
     * Whether a spot stands on or inside a generated structure. The heightmap's top
     * block is a building's roof wherever one stands, so a player could arrive on
     * a ruined tower with no way down but the fall, or inside a Warehouse; such
     * columns are treated as having no safe surface. Both the feet and the block
     * underfoot are checked, since a roof's top layer is the last row inside the
     * building's piece and the feet stand just above it.
     *
     * <p>Never loads a chunk: this also runs while chunks load, and a structure's
     * start can sit several chunks away. Only the column's own chunk references
     * are read, and a start chunk that is not loaded yet counts as covered, so an
     * unknown spot is passed over rather than risked.
     */
    private static boolean onStructure(ServerLevel level, LevelChunk chunk, BlockPos feet) {
        var references = chunk.getAllReferences();
        if (references.isEmpty()) return false;
        BlockPos below = feet.below();
        var structures = level.structureManager();
        for (var entry : references.entrySet()) {
            for (long packed : entry.getValue()) {
                LevelChunk startChunk = level.getChunkSource().getChunkNow(
                        ChunkPos.getX(packed), ChunkPos.getZ(packed));
                if (startChunk == null) return true;
                var start = startChunk.getStartForStructure(entry.getKey());
                if (start != null && start.isValid() && (structures.structureHasPieceAt(feet, start)
                        || structures.structureHasPieceAt(below, start))) return true;
            }
        }
        return false;
    }

    static List<BlockPos> mappingSurfaces(ServerLevel level, LevelChunk chunk) {
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int[][] offsets = {{8, 8}, {4, 4}, {12, 12}, {4, 12}, {12, 4}};
        List<BlockPos> result = new ArrayList<>(offsets.length);
        for (int[] offset : offsets) {
            BlockPos safe = findSafeSurface(level, chunk, baseX + offset[0], baseZ + offset[1]);
            if (safe != null && level.getWorldBorder().isWithinBounds(safe)
                    && level.canSeeSky(safe) && insideTerrainFootprint(safe)
                    && isAllowedTerritory(level, safe, null)) {
                result.add(safe);
            }
        }
        return result;
    }

    static boolean isAllowedTerritory(ServerLevel level, BlockPos position, UUID friendlyNationId) {
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                .controllingCore(level.getServer(), level.dimension().location(), position).orElse(null);
        if (core == null) return true;
        if (friendlyNationId == null) return false;
        return core.nationId().equals(friendlyNationId)
                || NationSavedData.get(level.getServer()).isAllied(friendlyNationId, core.nationId());
    }

    static double mappingMinRadius(ServerLevel level, BlockPos center) {
        return effectiveMinRadius(level, center);
    }

    static double mappingMaxRadius(ServerLevel level, BlockPos center) {
        return effectiveMaxRadius(level, center);
    }

    static int recommendedPoolTarget(ServerLevel level) {
        TerrainTileStore terrain = TerrainTileStore.active();
        double minX = level.getWorldBorder().getMinX() + 16.0D;
        double maxX = level.getWorldBorder().getMaxX() - 16.0D;
        double minZ = level.getWorldBorder().getMinZ() + 16.0D;
        double maxZ = level.getWorldBorder().getMaxZ() - 16.0D;
        double landArea;
        if (terrain != null) {
            landArea = terrain.estimatedLandAreaWithin(minX, maxX, minZ, maxZ);
        } else {
            double maximum = effectiveMaxRadius(level, level.getSharedSpawnPos());
            double minimum = effectiveMinRadius(level, level.getSharedSpawnPos());
            landArea = Math.PI * Math.max(0.0D, maximum * maximum - minimum * minimum) * 0.30D;
        }
        return RandomSpawnCapacityPolicy.targetForLandArea(landArea);
    }

    static boolean insideTerrainFootprint(BlockPos position) {
        TerrainTileStore terrain = TerrainTileStore.active();
        return terrain == null || terrain.tileAt(position.getX(), position.getZ()) != null;
    }

    private static BlockPos findSafeNear(ServerLevel level, int x, int z, int startY) {
        int minimumY = level.getMinBuildHeight() + 1;
        int maximumY = level.getMaxBuildHeight() - 2;
        int clampedY = Math.max(minimumY, Math.min(maximumY, startY));
        BlockPos direct = new BlockPos(x, clampedY, z);
        if (isSafe(level, direct)) return direct;

        for (int offset = 1; offset <= 12; offset++) {
            int belowY = clampedY - offset;
            if (belowY >= minimumY) {
                BlockPos below = new BlockPos(x, belowY, z);
                if (isSafe(level, below)) return below;
            }
            if (offset <= 4) {
                int aboveY = clampedY + offset;
                if (aboveY <= maximumY) {
                    BlockPos above = new BlockPos(x, aboveY, z);
                    if (isSafe(level, above)) return above;
                }
            }
        }
        return null;
    }

    private static boolean isDangerous(BlockState state) {
        return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE);
    }

    private static double effectiveMinRadius(ServerLevel level, BlockPos center) {
        double maximum = effectiveMaxRadius(level, center);
        if (maximum < 64.0D) return 0.0D;
        return Math.min(MIN_WORLD_SPAWN_RADIUS, maximum * 0.4D);
    }

    private static double effectiveMaxRadius(ServerLevel level, BlockPos center) {
        double minX = level.getWorldBorder().getMinX() + 16.0D;
        double maxX = level.getWorldBorder().getMaxX() - 16.0D;
        double minZ = level.getWorldBorder().getMinZ() + 16.0D;
        double maxZ = level.getWorldBorder().getMaxZ() - 16.0D;
        TerrainTileStore terrain = TerrainTileStore.active();
        if (terrain != null) {
            return terrain.maximumDistanceWithin(center.getX(), center.getZ(), minX, maxX, minZ, maxZ);
        }
        double dx = Math.max(Math.abs(minX - center.getX()), Math.abs(maxX - center.getX()));
        double dz = Math.max(Math.abs(minZ - center.getZ()), Math.abs(maxZ - center.getZ()));
        return Math.max(0.0D, Math.min(FALLBACK_MAX_WORLD_SPAWN_RADIUS, Math.sqrt(dx * dx + dz * dz)));
    }

    private static List<ServerPlayer> eligibleOtherPlayers(ServerPlayer player, ServerLevel level,
                                                           UUID friendlyNationId) {
        NationSavedData nations = NationSavedData.get(player.server);
        return player.server.getPlayerList().getPlayers().stream()
                .filter(other -> other != player)
                .filter(other -> other.level().dimension().equals(level.dimension()))
                .filter(other -> !other.isSpectator())
                .filter(other -> friendlyNationId == null || nations.nationIdFor(other.getUUID())
                        .filter(otherNation -> otherNation.equals(friendlyNationId)
                                || nations.isAllied(friendlyNationId, otherNation)).isEmpty())
                .toList();
    }

    private static boolean clearOfThreats(BlockPos spawn, List<ServerPlayer> threats) {
        return RandomSpawnPolicy.fallbackAllowed(minimumDistanceSqr(spawn, threats),
                square(FALLBACK_MIN_PLAYER_DISTANCE));
    }

    private static double minimumDistanceSqr(BlockPos spawn, List<ServerPlayer> players) {
        double minimum = Double.POSITIVE_INFINITY;
        for (ServerPlayer other : players) {
            double dx = spawn.getX() + 0.5D - other.getX();
            double dz = spawn.getZ() + 0.5D - other.getZ();
            minimum = Math.min(minimum, dx * dx + dz * dz);
        }
        return minimum;
    }

    private static double horizontalDistanceSqr(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static double square(double value) {
        return value * value;
    }

    private static int MthFloor(double value) {
        return (int) Math.floor(value);
    }

    private record SpawnColumn(int x, int z, double tieBreaker, boolean pooled) {
    }

    private record SpawnCandidate(BlockPos position, double score, SpawnColumn column) {
    }

    private record PendingRetry(int dueTick, int attempt, boolean markInitializedOnSuccess, UUID nationId) {
    }

    private static final class SpawnSearch {
        private final List<SpawnColumn> columns;
        private final BlockPos lastSpawn;
        private final float yaw;
        private final boolean markInitializedOnSuccess;
        private final UUID friendlyNationId;
        private final BlockPos worldSpawn;
        private final double minRadius;
        private final double maxRadius;
        private final int rejectedByBorder;
        private int rejectedBySurface;
        private int rejectedByStorage;
        private int nextColumnIndex;
        private int startedTick = -1;
        private int queuedTick;
        /** Automatic retries made before this search; 0 for the first. */
        private int attempt;
        private SpawnCandidate safestFallback;
        private boolean fallbackRequested;
        private boolean loadingFallback;
        private boolean ticketActive;
        private CompletableFuture<Optional<CompoundTag>> storageProbe;
        private SpawnColumn requestedColumn;
        private ChunkPos requestedChunk;

        private SpawnSearch(List<SpawnColumn> columns, BlockPos lastSpawn, float yaw,
                            boolean markInitializedOnSuccess, UUID friendlyNationId, BlockPos worldSpawn,
                            double minRadius, double maxRadius, int rejectedByBorder) {
            this.columns = columns;
            this.lastSpawn = lastSpawn;
            this.yaw = yaw;
            this.markInitializedOnSuccess = markInitializedOnSuccess;
            this.friendlyNationId = friendlyNationId;
            this.worldSpawn = worldSpawn;
            this.minRadius = minRadius;
            this.maxRadius = maxRadius;
            this.rejectedByBorder = rejectedByBorder;
        }

        private SpawnColumn nextRequest() {
            loadingFallback = false;
            if (nextColumnIndex < columns.size()) return columns.get(nextColumnIndex++);
            if (!fallbackRequested && safestFallback != null) {
                fallbackRequested = true;
                loadingFallback = true;
                return safestFallback.column;
            }
            return null;
        }
    }
}
