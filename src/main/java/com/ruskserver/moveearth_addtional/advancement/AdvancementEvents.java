package com.ruskserver.moveearth_addtional.advancement;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.economy.EconomyLedgerSavedData;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bridges successful gameplay that vanilla criteria cannot describe cleanly. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AdvancementEvents {
    private static final String MIGRATED = "MoveEarthVanillaAdvancementsV1";
    private static final String PENDING_REINFORCEMENTS = "MoveEarthPendingReinforcementAdvancements";
    private static final Map<UUID, Travel> FREIGHT = new HashMap<>();
    private static final Set<UUID> RESTORING = new HashSet<>();

    private AdvancementEvents() { }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        RESTORING.add(player.getUUID());
        try {
            EconomyLedgerSavedData ledger = EconomyLedgerSavedData.get(player.server);
            if (NationSavedData.get(player.server).nationIdFor(player.getUUID()).isPresent()) {
                ModCriteria.trigger(player, ModCriteria.NATION_CITIZEN);
            }
            if (ledger.recent(EconomyLedgerSavedData.Account.player(player.getUUID()), 100).stream()
                    .anyMatch(transaction -> transaction.reason().equals("market_sell_filled")
                            || transaction.reason().equals("market_buy_filled"))) {
                ModCriteria.trigger(player, ModCriteria.MARKET_TRADE_COMPLETED);
            }
            var harvestScore = ledger.harvestScores().get(player.getUUID());
            if ("HARVEST".equals(ledger.eventKind()) && harvestScore != null && harvestScore.points() > 0)
                ModCriteria.trigger(player, ModCriteria.HARVEST_EVENT_PARTICIPATED);
            migrate(player);
        } finally {
            RESTORING.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || RESTORING.contains(player.getUUID())
                || !Moveearth_addtional.MODID.equals(event.getAdvancement().id().getNamespace())) return;
        var display = event.getAdvancement().value().display().orElse(null);
        if (display == null || !display.shouldShowToast() || display.getType() == AdvancementType.CHALLENGE) return;
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.65F, 1.15F);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock());
        if (id != null && id.toString().equals("electroenergetics:converter")) {
            ModCriteria.trigger(player, ModCriteria.ELECTRICITY_BUILT);
        }
    }

    @SubscribeEvent
    public static void onMachineInspected(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            MekanismAdvancementBridge.inspect(player, level, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 9) return;
        boolean protectedFromCold = player.getArmorSlots().iterator().hasNext()
                && java.util.stream.StreamSupport.stream(player.getArmorSlots().spliterator(), false).anyMatch(stack -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return stack.is(Items.LEATHER_CHESTPLATE)
                    || id != null && id.getNamespace().equals("cold_sweat");
        });
        if (protectedFromCold) ModCriteria.trigger(player, ModCriteria.COLD_PROTECTION);

        checkReinforcements(player);

        if (isFreightVehicle(player)) {
            Travel travel = FREIGHT.computeIfAbsent(player.getUUID(), ignored -> new Travel(player.getX(), player.getZ()));
            if (travel.distanceSquared(player.getX(), player.getZ()) >= 128.0D * 128.0D) {
                ModCriteria.trigger(player, ModCriteria.FREIGHT_COMPLETED);
                FREIGHT.remove(player.getUUID());
            }
        } else {
            FREIGHT.remove(player.getUUID());
        }
    }

    private static boolean isFreightVehicle(ServerPlayer player) {
        var vehicle = player.getRootVehicle();
        if (vehicle == player) return false;
        if (vehicle instanceof AbstractMinecart) return true;
        return vehicle.getClass().getName().equals(
                "com.simibubi.create.content.trains.entity.CarriageContraptionEntity");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FREIGHT.remove(event.getEntity().getUUID());
        RESTORING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FREIGHT.clear();
        RESTORING.clear();
    }

    /** Track accepted welding work and award only after its delayed activation actually succeeds. */
    public static void trackReinforcement(ServerPlayer player, ResourceKey<Level> dimension,
                                          BlockPos pos, long activatesAt) {
        CompoundTag persisted = persisted(player);
        ListTag pending = persisted.getList(PENDING_REINFORCEMENTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag existing = pending.getCompound(i);
            if (existing.getLong("Pos") == pos.asLong()
                    && existing.getString("Dimension").equals(dimension.location().toString())) return;
        }
        CompoundTag value = new CompoundTag();
        value.putString("Dimension", dimension.location().toString());
        value.putLong("Pos", pos.asLong());
        value.putLong("ActivatesAt", activatesAt);
        pending.add(value);
        persisted.put(PENDING_REINFORCEMENTS, pending);
    }

    private static void checkReinforcements(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        ListTag pending = persisted.getList(PENDING_REINFORCEMENTS, Tag.TAG_COMPOUND);
        if (pending.isEmpty()) return;
        ListTag retained = new ListTag();
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag value = pending.getCompound(i);
            ResourceLocation dimensionId = ResourceLocation.tryParse(value.getString("Dimension"));
            if (dimensionId == null) continue;
            var level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
            if (level == null) continue;
            long now = level.getGameTime();
            long activatesAt = value.getLong("ActivatesAt");
            if (now < activatesAt) {
                retained.add(value.copy());
                continue;
            }
            BlockPos pos = BlockPos.of(value.getLong("Pos"));
            ReinforcementEntry entry = ReinforcementSavedData.get(level).get(pos).orElse(null);
            if (entry != null && entry.enabled()) {
                ModCriteria.trigger(player, ModCriteria.REINFORCEMENT_ACTIVATED);
            } else if (entry != null && now <= activatesAt + ReinforcementEntry.HP_FILL_TICKS + 1200L) {
                retained.add(value.copy());
            }
        }
        if (retained.isEmpty()) persisted.remove(PENDING_REINFORCEMENTS);
        else persisted.put(PENDING_REINFORCEMENTS, retained);
    }

    private static void migrate(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        if (persisted.getBoolean(MIGRATED)) return;
        UUID nation = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        Set<ResourceLocation> completed = NationTechnologySavedData.get(player.server)
                .completedFor(player.getUUID(), nation);
        migrate(player, completed, "personal/territory_basics", "nation/view_territory", "done");
        migrate(player, completed, "personal/nation_membership", "nation/citizen", "done");
        migrate(player, completed, "personal/create_introduction", "industry/andesite_alloy", "obtained");
        migrate(player, completed, "personal/rotation", "industry/rotation", "placed");
        migrate(player, completed, "personal/basic_processing", "industry/iron_sheet", "obtained");
        migrate(player, completed, "personal/temperature_readiness", "getting_started/cold_protection", "done");
        persisted.putBoolean(MIGRATED, true);
    }

    private static void migrate(ServerPlayer player, Set<ResourceLocation> completed, String oldPath,
                                String newPath, String criterion) {
        ResourceLocation oldId = ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, oldPath);
        if (!completed.contains(oldId)) return;
        var advancement = player.server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, newPath));
        if (advancement != null) player.getAdvancements().award(advancement, criterion);
    }

    private static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    private record Travel(double x, double z) {
        double distanceSquared(double nextX, double nextZ) {
            double dx = nextX - x;
            double dz = nextZ - z;
            return dx * dx + dz * dz;
        }
    }
}
