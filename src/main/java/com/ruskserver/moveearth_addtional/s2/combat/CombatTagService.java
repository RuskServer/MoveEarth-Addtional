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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import com.ruskserver.moveearth_addtional.s2.siege.PrisonerSavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative PvP combat tags and disconnect bodies. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class CombatTagService {
    public static final String BODY_OWNER = "MoveEarthCombatBodyOwner";
    private static final Set<UUID> FORCED_LOGOUT_DEATHS = new HashSet<>();
    private CombatTagService() { }

    public static boolean isTagged(ServerPlayer player) {
        return CombatTagSavedData.get(player.server).isTagged(player.getUUID());
    }

    public static void clearForCustody(MinecraftServer server, UUID playerId) {
        CombatTagSavedData.get(server).clearTag(playerId);
        CombatTagBossBar.remove(playerId);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getNewDamage() <= 0.0F) return;
        if (event.getEntity() instanceof ServerPlayer victim
                && RealPlayers.attacker(event.getSource()) instanceof ServerPlayer attacker
                && !victim.getUUID().equals(attacker.getUUID())) {
            PrisonerSavedData prisoners = PrisonerSavedData.get(victim.server);
            if (prisoners.custody(victim.getUUID()).isPresent()
                    || prisoners.prisoner(victim.getUUID()).isPresent()
                    || prisoners.custody(attacker.getUUID()).isPresent()
                    || prisoners.prisoner(attacker.getUUID()).isPresent()) return;
            if (com.ruskserver.moveearth_addtional.pvp.PvpMatchManager.INSTANCE.isActive(victim)
                    || com.ruskserver.moveearth_addtional.pvp.PvpMatchManager.INSTANCE.isActive(attacker)) return;
            long duration = S2TerritoryConfig.combatTagTicks();
            CombatTagSavedData data = CombatTagSavedData.get(victim.server);
            boolean victimFirst = !data.isTagged(victim.getUUID());
            boolean attackerFirst = !data.isTagged(attacker.getUUID());
            data.tag(victim.getUUID(), attacker.getUUID(), duration);
            data.tag(attacker.getUUID(), victim.getUUID(), duration);
            if (victimFirst) {
                victim.sendSystemMessage(Component.translatable("message.moveearth_addtional.combat.warning"));
            }
            if (attackerFirst) {
                attacker.sendSystemMessage(Component.translatable("message.moveearth_addtional.combat.warning"));
            }
            com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(victim,
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.COMBAT_STARTED);
            com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(attacker,
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.COMBAT_STARTED);
            return;
        }
        if (event.getEntity() instanceof ArmorStand body && body.getPersistentData().hasUUID(BODY_OWNER)) {
            clearProxyEquipment(body);
            UUID owner = body.getPersistentData().getUUID(BODY_OWNER);
            MinecraftServer server = body.getServer();
            if (server == null) return;
            CombatTagSavedData data = CombatTagSavedData.get(server);
            CombatTagSavedData.CombatState state = data.state(owner).orElse(null);
            if (state == null) return;
            boolean inCustody = PrisonerSavedData.get(server).custody(owner).isPresent();
            if (RealPlayers.attacker(event.getSource()) instanceof ServerPlayer attacker) {
                data.tag(attacker.getUUID(), owner, S2TerritoryConfig.combatTagTicks());
                if (!inCustody) data.tag(owner, attacker.getUUID(), S2TerritoryConfig.combatTagTicks());
            }
            float nextHealth = state.health() - event.getNewDamage();
            boolean downed = state.downed();
            boolean killed = state.killed();
            if (nextHealth <= 0.0F) {
                if (downed) killed = true;
                else downed = true;
                nextHealth = killed ? 0.0F : 1.0F;
            }
            if (downed && !state.downed() && !inCustody) data.tag(owner, state.opponent(), 60L * 20L);
            data.updateBody(owner, body.level().dimension().location(), body.blockPosition(),
                    nextHealth, downed, killed);
            event.setNewDamage(0.0F);
            if (killed) body.discard();
        }
    }

    /** PlayerRevive may cancel an ordinary death; combat logout is an explicit terminal penalty. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onForcedLogoutDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (FORCED_LOGOUT_DEATHS.contains(player.getUUID())) {
            // PlayerRevive's HIGHEST listener can start bleeding before cancelling this event.
            // Undo that intermediate state before allowing the real death to complete.
            CompatEventHandler.revivePlayer(player);
            player.getPersistentData().remove("playerrevive:bleeding");
            player.setHealth(0.0F);
            event.setCanceled(false);
        } else if (!event.isCanceled()) {
            CombatTagSavedData.get(player.server).clearTag(player.getUUID());
            CombatTagBossBar.remove(player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CombatTagBossBar.remove(player.getUUID());
        if (com.ruskserver.moveearth_addtional.TimeRestrictionHandler.consumeScheduledDisconnect(player)
                || player.server.isStopped()) return; // A planned shutdown is not a combat log.
        PrisonerSavedData prisoners = PrisonerSavedData.get(player.server);
        boolean inCustody = prisoners.custody(player.getUUID()).isPresent();
        if (!isTagged(player) && !inCustody) return;
        CombatTagSavedData data = CombatTagSavedData.get(player.server);
        if (!inCustody) {
            // NeoForge fires this event before PlayerList saves the player. Death loot is therefore
            // removed from the saved inventory, and the persistent marker covers interrupted saves.
            data.recordLogoutDeath(player.getUUID());
            com.ruskserver.moveearth_addtional.analytics.event.GameEvents.player(com.ruskserver.moveearth_addtional.analytics.event.GameEventType.COMBAT_LOGOUT, player, 0L, null);
            forceLogoutDeath(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        ArmorStand body = new ArmorStand(level, player.getX(), player.getY(), player.getZ());
        body.setCustomName(Component.literal(player.getGameProfile().getName() + " [CUSTODY]")
                .withStyle(ChatFormatting.RED));
        body.setCustomNameVisible(true);
        body.setNoGravity(true);
        body.setShowArms(false);
        body.setInvulnerable(true);
        body.getPersistentData().putUUID(BODY_OWNER, player.getUUID());
        data.disconnectCustody(player.getUUID(), level.dimension().location(),
                player.blockPosition(), player.getHealth(), body.getUUID());
        if (!level.addFreshEntity(body)) {
            data.disconnectCustody(player.getUUID(), level.dimension().location(),
                    player.blockPosition(), player.getHealth(), null);
        }
    }

    /** Removes bodies whose SavedData owner link expired while their chunk was unloaded. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ArmorStand body)
                || !body.getPersistentData().hasUUID(BODY_OWNER)
                || event.getLevel().isClientSide()) return;
        clearProxyEquipment(body);
        MinecraftServer server = body.getServer();
        if (server == null) return;
        UUID owner = body.getPersistentData().getUUID(BODY_OWNER);
        body.setInvulnerable(true);
        body.setNoGravity(true);
        if (PrisonerSavedData.get(server).custody(owner).isEmpty()) {
            CombatTagSavedData data = CombatTagSavedData.get(server);
            CombatTagSavedData.CombatState state = data.state(owner).orElse(null);
            if (server.getPlayerList().getPlayer(owner) == null && state != null
                    && state.disconnected() && body.getUUID().equals(state.bodyEntity())) {
                data.recordLogoutDeath(owner);
            }
            event.setCanceled(true);
            return;
        }
        UUID expectedBody = CombatTagSavedData.get(server).state(owner)
                .map(CombatTagSavedData.CombatState::bodyEntity).orElse(null);
        if (!body.getUUID().equals(expectedBody)) {
            event.setCanceled(true);
        }
    }

    /** The custody proxy is a hitbox, not an equipment inventory. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBodyEquipmentInteraction(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof ArmorStand body
                && body.getPersistentData().hasUUID(BODY_OWNER)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CombatTagSavedData data = CombatTagSavedData.get(player.server);
        CombatTagSavedData.CombatState state = data.state(player.getUUID()).orElse(null);
        if (state == null || !state.disconnected()) return; // Keep a tag restored after a crash.
        data.consume(player.getUUID());
        discardBody(player.server, state);
        boolean inCustody = PrisonerSavedData.get(player.server).custody(player.getUUID()).isPresent();
        if (state.killed() || state.bodyEntity() != null && !inCustody) {
            // A crash between the logout event and player-data save can restore an old inventory
            // even when health was already written as zero. Re-run death only if loot remains.
            if (player.getHealth() > 0.0F || !player.getInventory().isEmpty()) forceLogoutDeath(player);
            return;
        }
        if (state.dimension() != null && state.position() != null) {
            ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, state.dimension()));
            if (level != null) player.teleportTo(level, state.position().getX() + 0.5D,
                    state.position().getY(), state.position().getZ() + 0.5D, player.getYRot(), player.getXRot());
        }
        player.setHealth(Math.max(1.0F, Math.min(player.getMaxHealth(), state.health())));
        PrisonerSavedData prisoners = PrisonerSavedData.get(player.server);
        boolean imprisoned = prisoners.prisoner(player.getUUID()).isPresent();
        if (state.downed() && !inCustody && !imprisoned) {
            CompatEventHandler.knockOutPlayer(player);
        } else if (state.remainingTicks() > 0L && !inCustody && !imprisoned) {
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
        Set<UUID> visibleBars = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PrisonerSavedData prisoners = PrisonerSavedData.get(server);
            if (prisoners.custody(player.getUUID()).isPresent()
                    || prisoners.prisoner(player.getUUID()).isPresent()) {
                CombatTagSavedData.get(server).clearTag(player.getUUID());
                continue;
            }
            CombatTagSavedData.get(server).state(player.getUUID()).ifPresent(state -> {
                if (state.remainingTicks() > 0L) {
                    visibleBars.add(player.getUUID());
                    CombatTagBossBar.update(player, state.remainingTicks());
                }
            });
        }
        CombatTagBossBar.retain(visibleBars);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CombatTagBossBar.clear();
        FORCED_LOGOUT_DEATHS.clear();
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
                if (entity instanceof ArmorStand body) clearProxyEquipment(body);
                entity.discard();
                return;
            }
        }
    }

    private static void clearProxyEquipment(ArmorStand body) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            body.setItemSlot(slot, ItemStack.EMPTY);
        }
        body.setShowArms(false);
    }

    private static void forceLogoutDeath(ServerPlayer player) {
        FORCED_LOGOUT_DEATHS.add(player.getUUID());
        try {
            player.setHealth(0.0F);
            player.die(player.damageSources().genericKill());
        } finally {
            FORCED_LOGOUT_DEATHS.remove(player.getUUID());
        }
    }
}
