package com.ruskserver.moveearth_addtional.oxygen;

import com.ruskserver.moveearth_addtional.ModSounds;
import com.ruskserver.moveearth_addtional.network.S2C_SyncOxygenPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerOxygenManager {
    private static final Map<UUID, PlayerOxygenState> PLAYER_STATES = new ConcurrentHashMap<>();

    public static class PlayerOxygenState {
        public int oxygenTicks;
        public int lastMiningTick = -1000;
        public int lastCombatTick = -1000;
        public int damageCooldown = 0;
        public int breathSoundCooldown = 0;
        public boolean wasFilterZeroNotified = false;

        public PlayerOxygenState(int maxOxygenTicks) {
            this.oxygenTicks = maxOxygenTicks;
        }
    }

    public static PlayerOxygenState getOrCreate(UUID uuid) {
        return PLAYER_STATES.computeIfAbsent(uuid, k -> new PlayerOxygenState(OxygenConfig.OXYGEN_DEPLETION_TICKS.get()));
    }

    public static void remove(UUID uuid) {
        PLAYER_STATES.remove(uuid);
    }

    public static void markMining(ServerPlayer player) {
        PlayerOxygenState state = getOrCreate(player.getUUID());
        state.lastMiningTick = player.tickCount;
    }

    public static void markCombat(ServerPlayer player) {
        PlayerOxygenState state = getOrCreate(player.getUUID());
        state.lastCombatTick = player.tickCount;
    }

    public static void tick(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        PlayerOxygenState state = getOrCreate(player.getUUID());
        int maxOxygenTicks = OxygenConfig.OXYGEN_DEPLETION_TICKS.get();

        double y = player.getY();
        boolean isDanger = y <= OxygenConfig.DEPTH_DANGER_Y.get();
        boolean isExtreme = y <= OxygenConfig.DEPTH_EXTREME_Y.get();

        ItemStack headItem = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean hasGasMask = headItem.getItem() instanceof GasMaskItem;
        int filterTicks = hasGasMask ? GasMaskItem.getFilterTicks(headItem) : 0;
        int maxFilterTicks = GasMaskItem.getMaxFilterTicks();

        float consumptionRate = 1.0f;

        if (isDanger) {
            // 消費倍率の計算
            if (isExtreme) {
                consumptionRate *= (float) OxygenConfig.EXTREME_DEPTH_MULTIPLIER.get().doubleValue();
            }
            if (player.isSprinting()) {
                consumptionRate *= (float) OxygenConfig.SPRINTING_MULTIPLIER.get().doubleValue();
            }
            if (player.tickCount - state.lastMiningTick < 40) {
                consumptionRate *= (float) OxygenConfig.MINING_MULTIPLIER.get().doubleValue();
            }
            if (player.tickCount - state.lastCombatTick < 60) {
                consumptionRate *= (float) OxygenConfig.COMBAT_MULTIPLIER.get().doubleValue();
            }

            if (hasGasMask && filterTicks > 0) {
                // ガスマスクで呼吸中: フィルターを消費
                state.wasFilterZeroNotified = false;
                int consumeAmount = Math.max(1, Math.round(consumptionRate));
                int newFilterTicks = Math.max(0, filterTicks - consumeAmount);
                GasMaskItem.setFilterTicks(headItem, newFilterTicks);

                // マスクの耐久値も一定間隔（400ticksごと）で微量消費
                if (player.tickCount % 400 == 0 && !headItem.isEmpty()) {
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
                // マスクなし or フィルター切れ: 酸素減少
                if (hasGasMask && filterTicks <= 0 && !state.wasFilterZeroNotified) {
                    state.wasFilterZeroNotified = true;
                    player.displayClientMessage(Component.translatable("message.moveearth_addtional.filter_depleted")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            ModSounds.FILTER_WARNING.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
                }

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
            // 安全圏（Y > depthDangerY）: 酸素急速回復
            state.wasFilterZeroNotified = false;
            state.oxygenTicks = Math.min(maxOxygenTicks, state.oxygenTicks + 4);
        }

        // クライアント同期パケットの送信（10ticks = 0.5秒ごと）
        if (player.tickCount % 10 == 0) {
            float oxygenPercent = maxOxygenTicks > 0 ? (float) state.oxygenTicks / maxOxygenTicks : 1.0f;
            float filterPercent = maxFilterTicks > 0 ? (float) filterTicks / maxFilterTicks : 0.0f;

            PacketDistributor.sendToPlayer(player, new S2C_SyncOxygenPacket(
                    oxygenPercent,
                    filterPercent,
                    hasGasMask,
                    isDanger,
                    isExtreme,
                    consumptionRate
            ));
        }
    }
}
