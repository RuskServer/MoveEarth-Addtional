package com.ruskserver.moveearth_addtional.handler.dcc;

import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * A chunk tracking view that treats recently departed chunks as still tracked.
 *
 * <p>Based on NotEnoughBandwidth's {@code CachedChunkTrackingView}, adapted for
 * Minecraft 1.21.1. Keeping the cache in the authoritative tracking view is
 * essential: block, light, entity, and NeoForge watch state must agree with
 * the chunks that the client still owns.</p>
 */
public final class DelayedChunkTrackingView implements ChunkTrackingView {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ChunkTrackingView.Positioned major;
    private final DelayedChunkCacheState cache = new DelayedChunkCacheState();
    private final Context context;
    private final LongConsumer stopTracking;
    private long lastTimeoutCheckTick;

    public DelayedChunkTrackingView(
            ChunkTrackingView.Positioned major,
            long currentGameTime,
            Context context
    ) {
        this.major = major;
        this.lastTimeoutCheckTick = currentGameTime;
        this.context = context;
        this.stopTracking = packedPos -> context.stopTracking(new ChunkPos(packedPos));
    }

    @Override
    public boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
        return this.major.contains(x, z, includeOuterChunksAdjacentToViewBorder)
                || this.cache.contains(ChunkPos.asLong(x, z));
    }

    @Override
    public void forEach(@NotNull Consumer<ChunkPos> action) {
        this.major.forEach(action);
        this.cache.forEach(packedPos -> action.accept(new ChunkPos(packedPos)));
    }

    public ChunkTrackingView.Positioned major() {
        return this.major;
    }

    int cachedChunkCount() {
        return this.cache.size();
    }

    /**
     * Installs or updates a delayed tracking view for one player.
     */
    public static void updatePlayer(
            ServerPlayer player,
            int playerViewDistance,
            Settings settings,
            Context context
    ) {
        ChunkTrackingView currentView = player.getChunkTrackingView();
        ChunkPos currentCenter = player.chunkPosition();
        long gameTime = player.serverLevel().getGameTime();

        ChunkTrackingView.Positioned previousMajor = switch (currentView) {
            case DelayedChunkTrackingView delayed -> delayed.major;
            case ChunkTrackingView.Positioned positioned -> positioned;
            default -> null;
        };

        ChunkTrackingView.Positioned nextMajor;
        if (previousMajor == null
                || !previousMajor.center().equals(currentCenter)
                || previousMajor.viewDistance() != playerViewDistance) {
            nextMajor = new ChunkTrackingView.Positioned(currentCenter, playerViewDistance);
            if (previousMajor == null || !previousMajor.center().equals(currentCenter)) {
                player.connection.send(new ClientboundSetChunkCacheCenterPacket(currentCenter.x, currentCenter.z));
            }
        } else {
            nextMajor = previousMajor;
        }

        if (currentView instanceof DelayedChunkTrackingView delayed) {
            delayed.update(nextMajor, gameTime, settings);
            return;
        }

        if (context == null) {
            throw new IllegalArgumentException("context is required when installing a delayed tracking view");
        }
        DelayedChunkTrackingView delayed = new DelayedChunkTrackingView(nextMajor, gameTime, context);
        ChunkTrackingView.difference(currentView, delayed, context::startTracking, context::stopTracking);
        player.setChunkTrackingView(delayed);
    }

    /**
     * Flushes a delayed view and restores its normal positioned view.
     */
    public static void disableForPlayer(ServerPlayer player) {
        if (player.getChunkTrackingView() instanceof DelayedChunkTrackingView delayed) {
            delayed.flush();
            player.setChunkTrackingView(delayed.major);
        }
    }

    void update(ChunkTrackingView.Positioned nextMajor, long gameTime, Settings settings) {
        if (!this.major.equals(nextMajor)) {
            ChunkTrackingView.difference(this.major, nextMajor, chunkPos -> {
                if (!this.cache.remove(chunkPos.toLong())) {
                    this.context.startTracking(chunkPos);
                    LOGGER.trace("DCC miss for chunk {}", chunkPos);
                } else {
                    LOGGER.trace("DCC hit for chunk {}", chunkPos);
                }
            }, chunkPos -> cacheOrDrop(chunkPos, nextMajor, gameTime, settings));

            evictTooFar(nextMajor, settings);
            this.major = nextMajor;
        }

        enforceCapacity(settings.sizeLimit());
        if (gameTime - this.lastTimeoutCheckTick >= settings.checkIntervalTicks()) {
            evictTimedOut(gameTime, settings.timeoutTicks());
            this.lastTimeoutCheckTick = gameTime;
        }
    }

    private void cacheOrDrop(
            ChunkPos chunkPos,
            ChunkTrackingView.Positioned nextMajor,
            long gameTime,
            Settings settings
    ) {
        int maximumDistance = nextMajor.viewDistance() + settings.extraDistance();
        if (nextMajor.center().getChessboardDistance(chunkPos) > maximumDistance) {
            this.context.stopTracking(chunkPos);
            return;
        }

        this.cache.put(
                chunkPos.toLong(),
                gameTime,
                settings.sizeLimit(),
                this.stopTracking
        );
    }

    private void evictTooFar(ChunkTrackingView.Positioned nextMajor, Settings settings) {
        int maximumDistance = nextMajor.viewDistance() + settings.extraDistance();
        this.cache.evictTooFar(
                nextMajor.center().x,
                nextMajor.center().z,
                maximumDistance,
                this.stopTracking
        );
    }

    private void evictTimedOut(long gameTime, long timeoutTicks) {
        this.cache.evictTimedOut(
                gameTime,
                timeoutTicks,
                this.stopTracking
        );
    }

    private void enforceCapacity(int sizeLimit) {
        if (this.cache.size() > sizeLimit) {
            this.cache.enforceCapacity(sizeLimit, this.stopTracking);
        }
    }

    private void flush() {
        this.cache.flush(this.stopTracking);
    }

    public record Settings(int sizeLimit, int extraDistance, long timeoutTicks, int checkIntervalTicks) {
        public Settings {
            if (sizeLimit < 1) {
                throw new IllegalArgumentException("sizeLimit must be positive");
            }
            if (extraDistance < 0) {
                throw new IllegalArgumentException("extraDistance must not be negative");
            }
            if (timeoutTicks < 1) {
                throw new IllegalArgumentException("timeoutTicks must be positive");
            }
            if (checkIntervalTicks < 1) {
                throw new IllegalArgumentException("checkIntervalTicks must be positive");
            }
        }
    }

    public interface Context {
        void startTracking(ChunkPos chunkPos);

        void stopTracking(ChunkPos chunkPos);
    }
}
