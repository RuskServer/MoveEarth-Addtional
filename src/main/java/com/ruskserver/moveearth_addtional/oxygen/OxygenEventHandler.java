package com.ruskserver.moveearth_addtional.oxygen;

import com.ruskserver.moveearth_addtional.ModSounds;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public class OxygenEventHandler {
    private record TorchInteraction(InteractionHand hand, int tick) {}
    private static final Map<UUID, TorchInteraction> TORCH_INTERACTIONS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = player.getItemInHand(event.getHand());
        if (stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH)) {
            TORCH_INTERACTIONS.put(player.getUUID(), new TorchInteraction(event.getHand(), player.server.getTickCount()));
        } else {
            TORCH_INTERACTIONS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TorchInteraction interaction = TORCH_INTERACTIONS.get(player.getUUID());
            if (interaction != null && interaction.tick() != player.server.getTickCount()) {
                TORCH_INTERACTIONS.remove(player.getUUID(), interaction);
            }
            PlayerOxygenManager.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerOxygenManager.markMining(player);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerOxygenManager.markCombat(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSource().getEntity() != null) {
            PlayerOxygenManager.markCombat(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TORCH_INTERACTIONS.remove(player.getUUID());
            PlayerOxygenManager.remove(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerOxygenManager.onDeath(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TORCH_INTERACTIONS.clear();
        PlayerOxygenManager.clearAll();
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerOxygenManager.reset(player);
        }
    }

    /**
     * 無酸素深度での松明消灯ギミック
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!OxygenConfig.EXTINGUISH_TORCHES.get()) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.isCreative()) {
                return;
            }

            int posY = event.getPos().getY();
            if (posY <= OxygenConfig.DEPTH_DANGER_Y.get()) {
                BlockState placedState = event.getPlacedBlock();
                if (placedState.is(Blocks.TORCH) || placedState.is(Blocks.WALL_TORCH)
                        || placedState.is(Blocks.SOUL_TORCH) || placedState.is(Blocks.SOUL_WALL_TORCH)) {

                    // 設置をキャンセル
                    event.setCanceled(true);

                    // 消火音と煙パーティクル
                    ServerLevel level = (ServerLevel) player.level();
                    double px = event.getPos().getX() + 0.5;
                    double py = event.getPos().getY() + 0.5;
                    double pz = event.getPos().getZ() + 0.5;

                    level.playSound(null, px, py, pz, ModSounds.TORCH_EXTINGUISH.get(),
                            SoundSource.BLOCKS, 1.0f, 1.0f);
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, 15, 0.1, 0.2, 0.1, 0.05);
                    level.sendParticles(ParticleTypes.SMOKE, px, py, pz, 10, 0.1, 0.2, 0.1, 0.02);

                    // 設置を試みた松明を1個消費する。
                    TorchInteraction interaction = TORCH_INTERACTIONS.remove(player.getUUID());
                    if (interaction != null && interaction.tick() == player.server.getTickCount()) {
                        ItemStack used = player.getItemInHand(interaction.hand());
                        if (used.is(Items.TORCH) || used.is(Items.SOUL_TORCH)) used.shrink(1);
                    }

                    // 警告メッセージ
                    player.displayClientMessage(Component.translatable("message.moveearth_addtional.torch_extinguished")
                            .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), true);
                }
            }
        }
    }
}
