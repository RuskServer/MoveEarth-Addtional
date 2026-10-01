package com.ruskserver.moveearth_addtional.oxygen;

import com.ruskserver.moveearth_addtional.ModSounds;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_SyncOxygenPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerOxygenManager {
    private static final String TAG_OXYGEN_TICKS = "moveearth_addtional.OxygenTicks";
    private static final Map<UUID, PlayerOxygenState> PLAYER_STATES = new ConcurrentHashMap<>();
    /** Last chat warning per player: the O2 bar alone never told anyone this depth kills in seconds. */
    private static final Map<UUID, Long> LAST_DEPTH_WARNING = new ConcurrentHashMap<>();
    private static final long DEPTH_WARNING_INTERVAL_MILLIS = 300_000L;
    /** The worn mask's filter is written to the item this often (and on unequip, logout and death). */
    static final int FILTER_FLUSH_INTERVAL_TICKS = 100;
    /** The HUD gauges are resent at most this often while they move; flag changes go at once. */
    static final int SYNC_GAUGE_INTERVAL_TICKS = 10;
    private static final double LOST_MASK_SEARCH_RADIUS = 8.0D;
    private static final PendingFilterDebts PENDING_DEBTS = new PendingFilterDebts(4096, 6L * 60L * 60L * 1000L);

    public static class PlayerOxygenState {
        public int oxygenTicks;
        public int lastMiningTick = -1000;
        public int lastCombatTick = -1000;
        public int damageCooldown = 0;
        public int breathSoundCooldown = 0;
        public boolean wasFilterZeroNotified = false;
        final FilterConsumptionAccumulator filterConsumption = new FilterConsumptionAccumulator();
        final MaskFilterLedger filter = new MaskFilterLedger(FILTER_FLUSH_INTERVAL_TICKS);
        final OxygenSyncThrottle sync = new OxygenSyncThrottle(SYNC_GAUGE_INTERVAL_TICKS);
        /** The worn mask stack being tracked, compared by identity. */
        ItemStack trackedMask;
        UUID trackedMaskId;
        /** The custom data this code last left on the tracked mask; anything else is an outside write. */
        CustomData trackedMaskData;

        public PlayerOxygenState(int maxOxygenTicks) {
            this.oxygenTicks = maxOxygenTicks;
        }
    }

    public static PlayerOxygenState getOrCreate(ServerPlayer player) {
        return PLAYER_STATES.computeIfAbsent(player.getUUID(), uuid -> {
            int maxOxygenTicks = OxygenConfig.OXYGEN_DEPLETION_TICKS.get();
            int oxygenTicks = player.getPersistentData().contains(TAG_OXYGEN_TICKS, Tag.TAG_INT)
                    ? Mth.clamp(player.getPersistentData().getInt(TAG_OXYGEN_TICKS), 0, maxOxygenTicks)
                    : maxOxygenTicks;
            return new PlayerOxygenState(oxygenTicks);
        });
    }

    /** Logout: the worn mask's filter is written before the player is saved. */
    public static void remove(ServerPlayer player) {
        PlayerOxygenState state = PLAYER_STATES.remove(player.getUUID());
        if (state != null) releaseTrackedMask(player, state);
        LAST_DEPTH_WARNING.remove(player.getUUID());
    }

    public static void remove(UUID uuid) {
        PLAYER_STATES.remove(uuid);
        LAST_DEPTH_WARNING.remove(uuid);
    }

    /** Death: written while the mask is still on the head, before it drops or is kept. */
    public static void onDeath(ServerPlayer player) {
        PlayerOxygenState state = PLAYER_STATES.get(player.getUUID());
        if (state != null) releaseTrackedMask(player, state);
    }

    public static void reset(ServerPlayer player) {
        PlayerOxygenState previous = PLAYER_STATES.get(player.getUUID());
        // Leaving the End keeps the same stacks on the new player, so settle the old ledger first.
        if (previous != null) releaseTrackedMask(player, previous);
        PlayerOxygenState state = new PlayerOxygenState(OxygenConfig.OXYGEN_DEPLETION_TICKS.get());
        PLAYER_STATES.put(player.getUUID(), state);
        save(player, state);
    }

    public static void clearAll() {
        PLAYER_STATES.clear();
        LAST_DEPTH_WARNING.clear();
        PENDING_DEBTS.clear();
    }

    /**
     * Keeps the ledger on the stack actually worn. A different stack (or none) means the
     * tracked mask left the head; an outside write to the tracked one, such as a filter
     * replacement, is adopted.
     */
    private static void followWornMask(ServerPlayer player, PlayerOxygenState state, ItemStack head) {
        boolean wornMask = !head.isEmpty() && head.getItem() instanceof GasMaskItem;
        if (state.trackedMask != null && (!wornMask || state.trackedMask != head)) {
            releaseTrackedMask(player, state);
        }
        if (!wornMask) return;
        if (state.trackedMask == null) {
            UUID id = GasMaskItem.ensureMaskId(head);
            int debt = PENDING_DEBTS.take(id, System.currentTimeMillis());
            if (debt > 0) GasMaskItem.setFilterTicks(head, GasMaskItem.getFilterTicks(head) - debt);
            state.trackedMask = head;
            state.trackedMaskId = id;
            state.trackedMaskData = head.get(DataComponents.CUSTOM_DATA);
            state.filter.track(Math.min(GasMaskItem.getFilterTicks(head), GasMaskItem.getMaxFilterTicks()));
            return;
        }
        CustomData data = head.get(DataComponents.CUSTOM_DATA);
        if (data != state.trackedMaskData) {
            int stored = GasMaskItem.getFilterTicks(head);
            // Another write that left the filter alone keeps the unwritten consumption.
            if (stored != state.filter.written()) state.filter.track(stored);
            state.trackedMaskData = data;
        }
    }

    /** Writes the worn mask's pending consumption now, for code about to read or replace it. */
    public static void settleWornMask(ServerPlayer player) {
        PlayerOxygenState state = PLAYER_STATES.get(player.getUUID());
        if (state != null && state.trackedMask == player.getItemBySlot(EquipmentSlot.HEAD)) {
            flushTrackedMask(state);
        }
    }

    private static void flushTrackedMask(PlayerOxygenState state) {
        if (state.trackedMask == null || !state.filter.tracking()) return;
        if (state.filter.current() == state.filter.written()) return;
        GasMaskItem.setFilterTicks(state.trackedMask, state.filter.markFlushed());
        state.trackedMaskData = state.trackedMask.get(DataComponents.CUSTOM_DATA);
    }

    /**
     * Settles the tracked mask and stops tracking it. Moving a stack between slots copies
     * it, leaving the tracked object empty, so the copy is looked up by the mask's id in
     * the places it can reach within a tick; failing that, the consumption is kept as a
     * debt charged when the mask is next worn.
     */
    private static void releaseTrackedMask(ServerPlayer player, PlayerOxygenState state) {
        ItemStack tracked = state.trackedMask;
        int debt = state.filter.unflushed();
        if (tracked != null && debt > 0) {
            if (!tracked.isEmpty() && tracked.getItem() instanceof GasMaskItem) {
                flushTrackedMask(state);
            } else {
                ItemStack copy = findMask(player, state.trackedMaskId);
                if (copy != null) {
                    GasMaskItem.setFilterTicks(copy, GasMaskItem.getFilterTicks(copy) - debt);
                } else {
                    PENDING_DEBTS.add(state.trackedMaskId, debt, System.currentTimeMillis());
                }
            }
        }
        state.trackedMask = null;
        state.trackedMaskId = null;
        state.trackedMaskData = null;
        state.filter.untrack();
        state.filterConsumption.reset();
    }

    private static ItemStack findMask(ServerPlayer player, UUID id) {
        if (id == null) return null;
        ItemStack found = matchingMask(player.containerMenu.getCarried(), id);
        if (found == null) found = matchingMask(player.inventoryMenu.getCarried(), id);
        if (found == null) found = findIn(player.getInventory(), id);
        if (found == null && player.containerMenu != player.inventoryMenu) {
            for (Slot slot : player.containerMenu.slots) {
                found = matchingMask(slot.getItem(), id);
                if (found != null) break;
            }
        }
        if (found == null) {
            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(LOST_MASK_SEARCH_RADIUS))) {
                found = matchingMask(item.getItem(), id);
                if (found != null) break;
            }
        }
        return found;
    }

    private static ItemStack findIn(Container container, UUID id) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack found = matchingMask(container.getItem(slot), id);
            if (found != null) return found;
        }
        return null;
    }

    private static ItemStack matchingMask(ItemStack stack, UUID id) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GasMaskItem)) return null;
        return id.equals(GasMaskItem.getMaskId(stack)) ? stack : null;
    }

    private static void save(ServerPlayer player, PlayerOxygenState state) {
        player.getPersistentData().putInt(TAG_OXYGEN_TICKS, state.oxygenTicks);
    }

    public static void markMining(ServerPlayer player) {
        PlayerOxygenState state = getOrCreate(player);
        state.lastMiningTick = player.tickCount;
    }

    public static void markCombat(ServerPlayer player) {
        PlayerOxygenState state = getOrCreate(player);
        state.lastCombatTick = player.tickCount;
    }

    public static void tick(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            // No consumption here, and creative inventory moves would outrun the ledger.
            PlayerOxygenState idle = PLAYER_STATES.get(player.getUUID());
            if (idle != null && idle.trackedMask != null) releaseTrackedMask(player, idle);
            return;
        }

        PlayerOxygenState state = getOrCreate(player);
        int maxOxygenTicks = OxygenConfig.OXYGEN_DEPLETION_TICKS.get();

        double y = player.getY();
        boolean isDanger = y <= OxygenConfig.DEPTH_DANGER_Y.get();
        boolean isExtreme = isDanger && y <= OxygenConfig.DEPTH_EXTREME_Y.get();

        ItemStack headItem = player.getItemBySlot(EquipmentSlot.HEAD);
        followWornMask(player, state, headItem);
        boolean hasGasMask = state.trackedMask != null;
        int filterTicks = hasGasMask ? state.filter.current() : 0;
        int maxFilterTicks = GasMaskItem.getMaxFilterTicks();

        float consumptionRate = 1.0f;
        boolean isSprinting = false;
        boolean isMining = false;
        boolean isCombat = false;

        if (isDanger) {
            // 消費倍率の計算
            if (isExtreme) {
                consumptionRate *= (float) OxygenConfig.EXTREME_DEPTH_MULTIPLIER.get().doubleValue();
            }
            isSprinting = player.isSprinting();
            isMining = player.tickCount - state.lastMiningTick < 40;
            isCombat = player.tickCount - state.lastCombatTick < 60;
            if (isSprinting) {
                consumptionRate *= (float) OxygenConfig.SPRINTING_MULTIPLIER.get().doubleValue();
            }
            if (isMining) {
                consumptionRate *= (float) OxygenConfig.MINING_MULTIPLIER.get().doubleValue();
            }
            if (isCombat) {
                consumptionRate *= (float) OxygenConfig.COMBAT_MULTIPLIER.get().doubleValue();
            }

            if (hasGasMask && filterTicks > 0) {
                // ガスマスクで呼吸中: フィルターを消費
                state.wasFilterZeroNotified = false;
                int consumeAmount = state.filterConsumption.consume(consumptionRate);
                state.filter.consume(consumeAmount);
                int newFilterTicks = state.filter.current();

                // マスクの耐久値も一定間隔（400ticksごと）で微量消費
                if (player.tickCount % 400 == 0 && !headItem.isEmpty()) {
                    // Written first: a mask that breaks here must not leave filter behind.
                    flushTrackedMask(state);
                    headItem.hurtAndBreak(1, player, EquipmentSlot.HEAD);
                }

                // 酸素レベルは回復・満タン維持
                state.oxygenTicks = Math.min(maxOxygenTicks, state.oxygenTicks + 2);

                // 呼吸音の定期再生（160ticks = 8秒ごと）
                state.breathSoundCooldown--;
                if (state.breathSoundCooldown <= 0) {
                    state.breathSoundCooldown = 160;
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            ModSounds.GAS_MASK_BREATHE.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
                }

                // フィルター残量低下警告（20%以下で時折アラート）
                if (newFilterTicks > 0 && (float) newFilterTicks / maxFilterTicks <= 0.2f && player.tickCount % 200 == 0) {
                    player.displayClientMessage(Component.translatable("message.moveearth_addtional.filter_low")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            ModSounds.FILTER_WARNING.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
                }
            } else {
                state.filterConsumption.reset();
                // マスクなし or フィルター切れ: 酸素減少
                if (hasGasMask && filterTicks <= 0 && !state.wasFilterZeroNotified) {
                    state.wasFilterZeroNotified = true;
                    player.displayClientMessage(Component.translatable("message.moveearth_addtional.filter_depleted")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            ModSounds.FILTER_WARNING.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
                }

                if (!hasGasMask) warnDangerDepth(player);
                state.oxygenTicks = Math.max(0, state.oxygenTicks - 1);

                // 酸素ゼロ時の窒息ダメージ
                if (state.oxygenTicks <= 0) {
                    state.damageCooldown--;
                    if (state.damageCooldown <= 0) {
                        state.damageCooldown = OxygenConfig.DAMAGE_INTERVAL_TICKS.get();
                        float dmg = (float) (player.getMaxHealth() * OxygenConfig.SUFFOCATION_DAMAGE_PERCENT.get());
                        // 窒息ダメージ
                        player.hurt(player.damageSources().drown(), Math.max(1.0f, dmg));
                    }
                }
            }
        } else {
            state.filterConsumption.reset();
            // 安全圏（Y > depthDangerY）: 酸素急速回復
            state.wasFilterZeroNotified = false;
            state.oxygenTicks = Math.min(maxOxygenTicks, state.oxygenTicks + 4);
        }

        save(player, state);

        // The worn mask's filter reaches the item every few seconds, not every tick.
        if (state.trackedMask != null && state.trackedMask.isEmpty()) {
            releaseTrackedMask(player, state); // broke this tick
        } else {
            state.filter.tick();
            if (state.filter.flushDue()) flushTrackedMask(state);
        }

        // クライアント同期: 状態が変わった時だけ送る（ゲージの変化は最短10ticks間隔）
        float oxygenPercent = maxOxygenTicks > 0 ? (float) state.oxygenTicks / maxOxygenTicks : 1.0f;
        boolean syncedHasGasMask = state.trackedMask != null;
        int syncedFilterTicks = syncedHasGasMask ? state.filter.current() : 0;
        float filterPercent = maxFilterTicks > 0 ? (float) syncedFilterTicks / maxFilterTicks : 0.0f;
        OxygenSyncThrottle.Snapshot snapshot = new OxygenSyncThrottle.Snapshot(
                OxygenSyncThrottle.gauge(oxygenPercent), OxygenSyncThrottle.gauge(filterPercent),
                syncedHasGasMask, isDanger, isExtreme, consumptionRate, isSprinting, isMining, isCombat);
        if (state.sync.shouldSend(snapshot, player.server.getTickCount())) {
            PacketDistributor.sendToPlayer(player, new S2C_SyncOxygenPacket(
                    oxygenPercent,
                    filterPercent,
                    syncedHasGasMask,
                    isDanger,
                    isExtreme,
                    consumptionRate,
                    isSprinting,
                    isMining,
                    isCombat
            ));
        }
    }

    private static void warnDangerDepth(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_DEPTH_WARNING.get(player.getUUID());
        if (last != null && now - last < DEPTH_WARNING_INTERVAL_MILLIS) return;
        LAST_DEPTH_WARNING.put(player.getUUID(), now);
        player.sendSystemMessage(com.ruskserver.moveearth_addtional.ui.MoveEarthMessage.warning(
                Component.translatable("message.moveearth_addtional.oxygen.danger_depth")));
    }
}
