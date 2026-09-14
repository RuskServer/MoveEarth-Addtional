package com.ruskserver.moveearth_addtional.s2.combat;

import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.ServerSchedule;
import com.ruskserver.moveearth_addtional.config.S2TerritoryConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerSavedData;

import java.util.UUID;

/** Server-authoritative PvP combat tags and disconnect bodies. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class CombatTagService {
    public static final String BODY_OWNER = "MoveEarthCombatBodyOwner";
    private CombatTagService() { }

    public static boolean isTagged(ServerPlayer player) {
        return CombatTagSavedData.get(player.server).isTagged(player.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getNewDamage() <= 0.0F) return;
        if (event.getEntity() instanceof ServerPlayer victim
                && event.getSource().getEntity() instanceof ServerPlayer attacker
                && !victim.getUUID().equals(attacker.getUUID())) {
            if (com.ruskserver.moveearth_addtional.pvp.PvpMatchManager.INSTANCE.isParticipant(victim)
                    || com.ruskserver.moveearth_addtional.pvp.PvpMatchManager.INSTANCE.isParticipant(attacker)) return;
            long duration = S2TerritoryConfig.combatTagTicks();
            CombatTagSavedData data = CombatTagSavedData.get(victim.server);
            data.tag(victim.getUUID(), attacker.getUUID(), duration);
            data.tag(attacker.getUUID(), victim.getUUID(), duration);
            return;
        }
        if (event.getEntity() instanceof ArmorStand body && body.getPersistentData().hasUUID(BODY_OWNER)) {
            UUID owner = body.getPersistentData().getUUID(BODY_OWNER);
            MinecraftServer server = body.getServer();
            if (server == null) return;
            CombatTagSavedData data = CombatTagSavedData.get(server);
            CombatTagSavedData.CombatState state = data.state(owner).orElse(null);
            if (state == null) return;
            if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
                data.tag(attacker.getUUID(), owner, S2TerritoryConfig.combatTagTicks());
                data.tag(owner, attacker.getUUID(), S2TerritoryConfig.combatTagTicks());
            }
            float nextHealth = state.health() - event.getNewDamage();
            boolean downed = state.downed();
            boolean killed = state.killed();
            if (nextHealth <= 0.0F) {
                if (downed) killed = true;
                else downed = true;
                nextHealth = killed ? 0.0F : 1.0F;
            }
            if (downed && !state.downed()) data.tag(owner, state.opponent(), 60L * 20L);
            data.updateBody(owner, body.level().dimension().location(), body.blockPosition(),
                    nextHealth, downed, killed);
            event.setNewDamage(0.0F);
            if (killed) body.discard();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PrisonerSavedData prisoners = PrisonerSavedData.get(player.server);
        boolean inCustody = prisoners.custody(player.getUUID()).isPresent();
        if (!isTagged(player) && !inCustody) return;
        if (inCustody && !isTagged(player)) {
            CombatTagSavedData.get(player.server).tag(player.getUUID(), null,
                    prisoners.custody(player.getUUID()).map(PrisonerSavedData.Custody::remainingTicks).orElse(20L));
        }
        ServerLevel level = player.serverLevel();
        ArmorStand body = new ArmorStand(level, player.getX(), player.getY(), player.getZ());
        body.setCustomName(Component.literal(player.getGameProfile().getName() + " [COMBAT LOG]")
                .withStyle(ChatFormatting.RED));
        body.setCustomNameVisible(true);
        body.setNoGravity(false);
        body.setShowArms(true);
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
                body.setItemSlot(slot, player.getItemBySlot(slot).copy());
            }
        }
        body.getPersistentData().putUUID(BODY_OWNER, player.getUUID());
        boolean downed = inCustody || CompatEventHandler.isPlayerDown(player);
        if (downed) CombatTagSavedData.get(player.server).tag(player.getUUID(), null,
                inCustody ? prisoners.custody(player.getUUID()).map(PrisonerSavedData.Custody::remainingTicks).orElse(1200L)
                        : 60L * 20L);
        CombatTagSavedData.get(player.server).disconnect(player.getUUID(), level.dimension().location(),
                player.blockPosition(), player.getHealth(), downed, body.getUUID());
        if (!level.addFreshEntity(body)) {
            CombatTagSavedData.get(player.server).disconnect(player.getUUID(), level.dimension().location(),
                    player.blockPosition(), player.getHealth(), downed, null);
        }
    }

    /** Removes bodies whose SavedData owner link expired while their chunk was unloaded. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ArmorStand body)
                || !body.getPersistentData().hasUUID(BODY_OWNER)
                || event.getLevel().isClientSide()) return;
        MinecraftServer server = body.getServer();
        if (server == null) return;
        UUID owner = body.getPersistentData().getUUID(BODY_OWNER);
        UUID expectedBody = CombatTagSavedData.get(server).state(owner)
                .map(CombatTagSavedData.CombatState::bodyEntity).orElse(null);
        if (!body.getUUID().equals(expectedBody)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CombatTagSavedData data = CombatTagSavedData.get(player.server);
        CombatTagSavedData.CombatState state = data.consume(player.getUUID());
        if (state == null || !state.disconnected()) return;
        discardBody(player.server, state);
        if (state.dimension() != null && state.position() != null) {
            ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, state.dimension()));
            if (level != null) player.teleportTo(level, state.position().getX() + 0.5D,
                    state.position().getY(), state.position().getZ() + 0.5D, player.getYRot(), player.getXRot());
        }
        player.setHealth(Math.max(1.0F, Math.min(player.getMaxHealth(), state.health())));
        boolean inCustody = PrisonerSavedData.get(player.server).custody(player.getUUID()).isPresent();
        if (state.killed()) {
            player.setHealth(0.0F);
        } else if (state.downed() && !inCustody) {
            CompatEventHandler.knockOutPlayer(player);
        } else if (state.remainingTicks() > 0L || inCustody) {
            data.tag(player.getUUID(), state.opponent(), state.remainingTicks());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % 20L != 11L) return;
        if (!server.isDedicatedServer() || ServerSchedule.isOpenNow()) {
            for (CombatTagSavedData.CombatState expired : CombatTagSavedData.get(server).advance(20L)) {
                discardBody(server, expired);
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CombatTagSavedData.get(server).state(player.getUUID()).ifPresent(state -> {
                PrisonerSavedData prisoners = PrisonerSavedData.get(server);
                if (state.remainingTicks() > 0L && prisoners.custody(player.getUUID()).isEmpty()
                        && prisoners.prisoner(player.getUUID()).isEmpty()) player.displayClientMessage(Component.literal(
                        "戦闘中 " + Math.max(1L, (state.remainingTicks() + 19L) / 20L) + "秒")
                        .withStyle(ChatFormatting.RED), true);
            });
        }
    }

    public static void releaseBody(MinecraftServer server, UUID playerId, boolean keepRestore) {
        CombatTagSavedData data = CombatTagSavedData.get(server);
        CombatTagSavedData.CombatState state = data.state(playerId).orElse(null);
        if (state == null) return;
        discardBody(server, state);
        data.detachBody(playerId, state.dimension(), state.position(), state.health(), keepRestore);
    }

    private static void discardBody(MinecraftServer server, CombatTagSavedData.CombatState state) {
        if (state.bodyEntity() == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(state.bodyEntity());
            if (entity != null) {
                entity.discard();
                return;
            }
        }
    }
}
