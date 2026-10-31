package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.s2.time.OpenTimeService;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chests a nation left outside every nation's land ({@link AbandonedStoragePolicy}): its own for four open hours
 * after the land went, then open to anyone to take from. Only chests, and only what is in them: nothing can be put
 * in, and the chest itself cannot be broken, so it stays where it stood.
 *
 * <p>Every five minutes the ownership records are compared with the land. A chest found outside it is marked, and
 * its nation is told how many it has out there and when they open; one found back inside is cleared. Storage on
 * a vehicle lies in Sable's plot grid, which no territory covers, and is never counted: it answers to the vehicle.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class AbandonedStorageService {
    private static final int SWEEP_INTERVAL_TICKS = 5 * 60 * 20;
    /**
     * Players who opened an abandoned chest, with the server tick of the click: only a chest menu opening in that
     * same tick is made take-only, so a click that opened nothing cannot carry over to the player's own chest.
     */
    private static final Map<UUID, Long> TAKE_ONLY = new HashMap<>();

    private AbandonedStorageService() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % SWEEP_INTERVAL_TICKS == 0) sweep(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TAKE_ONLY.remove(event.getEntity().getUUID());
    }

    static void sweep(MinecraftServer server) {
        AbandonedStorageSavedData marks = AbandonedStorageSavedData.get(server);
        TerritorySavedData territories = TerritorySavedData.get(server);
        NationSavedData nations = NationSavedData.get(server);
        long now = OpenTimeService.now(server);
        Set<AbandonedStorageSavedData.Key> owned = new HashSet<>();
        Map<UUID, List<BlockPos>> newlyLost = new LinkedHashMap<>();
        NationStorageOwnershipSavedData.get(server).forEach((dimension, pos, owner) -> {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (level == null || onVehicle(level, pos)) return;
            AbandonedStorageSavedData.Key key = AbandonedStorageSavedData.Key.of(dimension, pos);
            owned.add(key);
            if (territories.controllingNation(server, dimension, pos).isPresent()) {
                marks.clear(key);
            } else if (marks.mark(key, now) && nations.nation(owner).isPresent()) {
                newlyLost.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(pos);
            }
        });
        marks.retainOnly(owned);
        newlyLost.forEach((nation, positions) -> warn(server, nation, positions));
    }

    /** The owner hears how many of its storage blocks are out there, where one is, and when chests among them open. */
    private static void warn(MinecraftServer server, UUID nation, List<BlockPos> positions) {
        BlockPos first = positions.getFirst();
        NationNotificationService.publish(server, List.of(nation), NationNotificationSavedData.EventType.SYSTEM,
                null, first, Component.translatable("message.moveearth_addtional.abandoned_storage.warning",
                        positions.size(), first.getX(), first.getY(), first.getZ(), AbandonedStoragePolicy.GRACE_OPEN_HOURS),
                List.of("領土の外に残った収納が" + positions.size() + "個あります。開放時間で"
                        + AbandonedStoragePolicy.GRACE_OPEN_HOURS + "時間後から、そのうちのチェストは誰でも中身を持ち出せます"));
    }

    /**
     * Whether {@code player} may take from the chest at {@code pos} because it is abandoned. Its owner and the
     * owner's allies keep their own rules; this is only for everyone else.
     */
    public static boolean lootable(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.CHEST) && !state.is(Blocks.TRAPPED_CHEST)) return false;
        if (onVehicle(level, pos)) return false;
        var dimension = level.dimension().location();
        UUID owner = NationStorageOwnershipSavedData.get(player.server).owner(dimension, pos);
        if (owner == null) return false;
        if (owner.equals(NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null))) return false;
        if (TerritorySavedData.get(player.server).controllingNation(player.server, dimension, pos).isPresent()) return false;
        return AbandonedStoragePolicy.open(AbandonedStorageSavedData.get(player.server).lostAt(dimension, pos),
                OpenTimeService.now(player.server));
    }

    /** Whether {@code pos} lies in Sable's plot grid, where vehicles keep their blocks, loaded or not. */
    private static boolean onVehicle(ServerLevel level, BlockPos pos) {
        try {
            return dev.ryanhcode.sable.Sable.HELPER.isInPlotGrid(level, pos.getX() >> 4, pos.getZ() >> 4);
        } catch (LinkageError unavailable) {
            return false;
        }
    }

    /** {@code player} opens an abandoned chest: what opens next is take-only. */
    public static void opened(ServerPlayer player, BlockPos pos) {
        TAKE_ONLY.put(player.getUUID(), (long) player.server.getTickCount());
        player.displayClientMessage(Component.translatable("message.moveearth_addtional.abandoned_storage.opened"), true);
        Moveearth_addtional.LOGGER.info("{} opened an abandoned chest at {} {} (owner {})",
                player.getGameProfile().getName(), player.level().dimension().location(), pos.toShortString(),
                NationStorageOwnershipSavedData.get(player.server).owner(player.level().dimension().location(), pos));
    }

    @SubscribeEvent
    public static void onMenuOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Long clickedAt = TAKE_ONLY.remove(player.getUUID());
        if (clickedAt == null || clickedAt != player.server.getTickCount()) return;
        if (!(event.getContainer() instanceof ChestMenu menu)) return;
        int chestSlots = menu.getRowCount() * 9;
        for (int i = 0; i < chestSlots && i < menu.slots.size(); i++) {
            Slot original = menu.slots.get(i);
            Slot takeOnly = new TakeOnlySlot(original.container, original.getContainerSlot(), original.x, original.y);
            takeOnly.index = original.index;
            menu.slots.set(i, takeOnly);
        }
    }

    /** A chest slot of an abandoned chest: what is there can be taken, nothing can be put in. */
    private static final class TakeOnlySlot extends Slot {
        private TakeOnlySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return true;
        }
    }
}
