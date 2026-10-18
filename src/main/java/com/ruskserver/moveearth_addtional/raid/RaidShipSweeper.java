package com.ruskserver.moveearth_addtional.raid;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.OptionalInt;

/**
 * Removes raid airships, and the pieces shot off them, once their raid is over.
 *
 * <p>A raid used to remove its airship once, by id, when it ended, and that missed three ways: running
 * raids are kept only in memory, so a restart left every airship in the sky with nothing to remove it;
 * an airship stored away because nobody was near could not be found to remove; and the pieces that broke
 * off under fire became bodies of their own that nothing tracked. Each raid airship now carries its raid
 * number on the body, saved with it, and pieces that split off are given the same number. Every few
 * seconds the loaded bodies are checked, and those of a raid that is not running go. One that is not
 * loaded goes the moment it is, when someone comes near. A body a player has made their own vehicle is
 * kept.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class RaidShipSweeper {
    static final String RAID_ID = "MoveEarthRaidShip";
    private static final int CHECK_INTERVAL_TICKS = 100;

    private RaidShipSweeper() { }

    /** Marks a new raid airship with its raid number. */
    public static void tag(ServerSubLevel ship, int raidId) {
        CompoundTag current = ship.getUserDataTag();
        CompoundTag tag = current == null ? new CompoundTag() : current.copy();
        tag.putInt(RAID_ID, raidId);
        ship.setUserDataTag(tag);
    }

    /** Gives a body assembled from inside a raid airship that airship's raid number. */
    public static void inherit(ServerLevel level, net.minecraft.core.BlockPos origin, ServerSubLevel piece) {
        try {
            if (!(dev.ryanhcode.sable.Sable.HELPER.getContaining(level, origin) instanceof ServerSubLevel parent)
                    || parent == piece) return;
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) return;
            OptionalInt raidId = raidId(container, parent);
            if (raidId.isPresent()) tag(piece, raidId.getAsInt());
        } catch (RuntimeException | LinkageError failure) {
            Moveearth_addtional.LOGGER.warn("Could not mark a piece split off a raid airship", failure);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL_TICKS != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            // Cleanup must never cost the server tick (the event bus rethrows).
            try {
                sweep(level);
            } catch (RuntimeException | LinkageError failure) {
                Moveearth_addtional.LOGGER.error("Raid airship cleanup failed in {}", level.dimension().location(), failure);
            }
        }
    }

    private static void sweep(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        for (ServerSubLevel body : List.copyOf(container.getAllSubLevels())) {
            if (body.isRemoved()) continue;
            OptionalInt raidId = raidId(container, body);
            if (raidId.isEmpty()) continue;
            boolean running = AirshipRaidManager.isActiveRaid(raidId.getAsInt());
            if (!RaidShipCleanupPolicy.remove(running, SableVehicleTopology.boundToLiveVehicle(level, body))) continue;
            Moveearth_addtional.LOGGER.info("Removing left-over airship body {} of raid #{} in {}",
                    body.getUniqueId(), raidId.getAsInt(), level.dimension().location());
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
    }

    /** The raid a body belongs to: its own mark, its name, or the body it split off from. */
    private static OptionalInt raidId(ServerSubLevelContainer container, ServerSubLevel body) {
        CompoundTag userData = body.getUserDataTag();
        if (userData != null && userData.contains(RAID_ID)) return OptionalInt.of(userData.getInt(RAID_ID));
        OptionalInt named = RaidShipCleanupPolicy.raidIdFromName(body.getName());
        if (named.isEmpty() && body.getSplitFromSubLevel() != null
                && container.getSubLevel(body.getSplitFromSubLevel()) instanceof ServerSubLevel parent) {
            CompoundTag parentData = parent.getUserDataTag();
            if (parentData != null && parentData.contains(RAID_ID)) named = OptionalInt.of(parentData.getInt(RAID_ID));
            else named = RaidShipCleanupPolicy.raidIdFromName(parent.getName());
        }
        // Record it on the body itself, so a piece keeps its raid after its parent is gone or a restart.
        if (named.isPresent()) tag(body, named.getAsInt());
        return named;
    }
}
