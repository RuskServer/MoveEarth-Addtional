package com.ruskserver.moveearth_addtional.compat.mekanism;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.compat.mekanism.MekaSuitHeavyShieldPolicy.Outcome;
import com.ruskserver.moveearth_addtional.compat.mekanism.MekaSuitHeavyShieldPolicy.Settings;
import com.ruskserver.moveearth_addtional.compat.mekanism.MekaSuitHeavyShieldPolicy.State;
import com.ruskserver.moveearth_addtional.config.MekanismBalanceConfig;
import com.ruskserver.moveearth_addtional.network.s2c.other.S2C_MekaSuitShieldPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The MekaSuit heavy-hit shield: an answer to snipers rather than general toughness.
 *
 * <p>With all four MekaSuit pieces worn, a single TaCZ bullet whose damage
 * before armor reaches the threshold loses a flat amount, paid for with suit
 * energy. A few such hits spend the charges, and they come back together once
 * no heavy hit has been stopped for the cooldown. Ordinary bullets are left to
 * the armor alone. Rules live in {@link MekaSuitHeavyShieldPolicy}.
 *
 * <p>State lives in the player's persistent data, so logging out does not
 * restore spent charges.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class MekaSuitHeavyShield {
    private static final String DATA_KEY = "MoveEarthMekaSuitShield";
    private static final String KEY = "message.moveearth_addtional.mekasuit.shield.";
    private static final TagKey<DamageType> TACZ_BULLETS = TagKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("tacz", "bullets"));
    /** Chest first: it holds the most energy, so a shot drains the others last. */
    private static final Map<EquipmentSlot, ResourceLocation> SUIT = Map.of(
            EquipmentSlot.CHEST, ResourceLocation.fromNamespaceAndPath("mekanism", "mekasuit_bodyarmor"),
            EquipmentSlot.HEAD, ResourceLocation.fromNamespaceAndPath("mekanism", "mekasuit_helmet"),
            EquipmentSlot.LEGS, ResourceLocation.fromNamespaceAndPath("mekanism", "mekasuit_pants"),
            EquipmentSlot.FEET, ResourceLocation.fromNamespaceAndPath("mekanism", "mekasuit_boots"));
    private static final List<EquipmentSlot> DRAIN_ORDER =
            List.of(EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.HEAD, EquipmentSlot.FEET);

    private MekaSuitHeavyShield() {
    }

    /** Runs early, so the threshold is judged on the damage before armor or other reductions. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !MekanismBalanceConfig.heavyHitShieldEnabled()
                || !event.getSource().is(TACZ_BULLETS)
                || event.getAmount() < MekanismBalanceConfig.heavyHitShieldThreshold()
                || !wearsFullSuit(player)) return;

        long now = player.serverLevel().getGameTime();
        Outcome outcome = MekaSuitHeavyShieldPolicy.onHit(event.getAmount(), read(player), now, settings());
        if (!outcome.blocked() || !drainEnergy(player, MekanismBalanceConfig.heavyHitShieldEnergyFE())) return;

        event.setAmount(outcome.damage());
        write(player, outcome.state());
        announceBlock(player, outcome);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        CompoundTag data = player.getPersistentData();
        if (!data.contains(DATA_KEY)) return;
        int maxCharges = MekanismBalanceConfig.heavyHitShieldCharges();
        if (!MekaSuitHeavyShieldPolicy.justRecharged(read(player), player.serverLevel().getGameTime(), maxCharges)) {
            return;
        }
        data.remove(DATA_KEY);
        player.displayClientMessage(Component.translatable(KEY + "recharged").withStyle(ChatFormatting.GREEN), true);
        player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    private static void announceBlock(ServerPlayer player, Outcome outcome) {
        int maxCharges = MekanismBalanceConfig.heavyHitShieldCharges();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.4F);
        if (outcome.depleted()) {
            player.displayClientMessage(Component.translatable(KEY + "depleted",
                    MekanismBalanceConfig.heavyHitShieldCooldownSeconds()).withStyle(ChatFormatting.GOLD), true);
            player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
        } else {
            player.displayClientMessage(Component.translatable(KEY + "blocked",
                    outcome.state().charges(), maxCharges).withStyle(ChatFormatting.GREEN), true);
        }
        PacketDistributor.sendToPlayer(player, new S2C_MekaSuitShieldPacket(outcome.depleted()));
    }

    private static boolean wearsFullSuit(ServerPlayer player) {
        for (Map.Entry<EquipmentSlot, ResourceLocation> piece : SUIT.entrySet()) {
            ItemStack stack = player.getItemBySlot(piece.getKey());
            if (stack.isEmpty() || !piece.getValue().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                return false;
            }
        }
        return true;
    }

    /** Takes the cost from the suit, or nothing when the whole suit cannot cover it. */
    private static boolean drainEnergy(ServerPlayer player, int cost) {
        if (cost <= 0) return true;
        List<IEnergyStorage> storages = new ArrayList<>();
        long available = 0L;
        for (EquipmentSlot slot : DRAIN_ORDER) {
            IEnergyStorage storage = player.getItemBySlot(slot).getCapability(Capabilities.EnergyStorage.ITEM);
            if (storage == null) continue;
            storages.add(storage);
            available += storage.extractEnergy(cost, true);
        }
        if (available < cost) return false;
        int remaining = cost;
        for (IEnergyStorage storage : storages) {
            if (remaining <= 0) break;
            remaining -= storage.extractEnergy(remaining, false);
        }
        return true;
    }

    private static Settings settings() {
        return new Settings(MekanismBalanceConfig.heavyHitShieldThreshold(),
                MekanismBalanceConfig.heavyHitShieldReduction(),
                MekanismBalanceConfig.heavyHitShieldCharges(),
                MekanismBalanceConfig.heavyHitShieldCooldownSeconds() * 20L);
    }

    private static State read(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(DATA_KEY)) return State.full(MekanismBalanceConfig.heavyHitShieldCharges());
        CompoundTag tag = data.getCompound(DATA_KEY);
        return new State(tag.getInt("Charges"), tag.getLong("ReadyAt"));
    }

    private static void write(ServerPlayer player, State state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Charges", state.charges());
        tag.putLong("ReadyAt", state.readyAt());
        player.getPersistentData().put(DATA_KEY, tag);
    }
}
