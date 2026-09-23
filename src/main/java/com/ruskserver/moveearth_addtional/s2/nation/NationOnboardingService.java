package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.handler.RandomSpawnHandler;
import com.ruskserver.moveearth_addtional.network.C2S_NationApplicationActionPacket;
import com.ruskserver.moveearth_addtional.network.C2S_OnboardingActionPacket;
import com.ruskserver.moveearth_addtional.network.S2C_NationApplicationsPacket;
import com.ruskserver.moveearth_addtional.network.S2C_CloseOnboardingPacket;
import com.ruskserver.moveearth_addtional.network.S2C_OnboardingPacket;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationSavedData;
import com.ruskserver.moveearth_addtional.s2.notification.NationNotificationService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class NationOnboardingService {
    private static final String NBT_PENDING = "MoveEarthOnboardingPending";
    private static final Map<UUID, HoldPosition> HOLDS = new HashMap<>();
    private static final java.util.Set<UUID> SEARCHING = new java.util.HashSet<>();
    private static final Map<UUID, OnboardingSyncState> LAST_SENT = new HashMap<>();

    private NationOnboardingService() { }

    public static boolean pending(ServerPlayer player) {
        return persistedData(player).getBoolean(NBT_PENDING);
    }

    public static void begin(ServerPlayer player) {
        persistedData(player).putBoolean(NBT_PENDING, true);
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        if (nationId != null) {
            releaseForSearch(player);
            RandomSpawnHandler.beginNationSpawnSearch(player, nationId);
            return;
        }
        hold(player);
        sendOnboarding(player, "", true);
    }

    public static void complete(ServerPlayer player) {
        persistedData(player).remove(NBT_PENDING);
        HOLDS.remove(player.getUUID());
        SEARCHING.remove(player.getUUID());
        LAST_SENT.remove(player.getUUID());
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.SATURATION);
        PacketDistributor.sendToPlayer(player, new S2C_CloseOnboardingPacket());
    }

    public static void releaseForSearch(ServerPlayer player) {
        if (!HOLDS.containsKey(player.getUUID())) hold(player);
        SEARCHING.add(player.getUUID());
        sendOnboarding(player, "screen.moveearth_addtional.onboarding.searching", true);
    }

    public static void searchFailed(ServerPlayer player) {
        if (!pending(player)) return;
        SEARCHING.remove(player.getUUID());
        hold(player);
        sendOnboarding(player, "screen.moveearth_addtional.onboarding.search_failed", false);
    }

    public static void handlePlayerAction(ServerPlayer player, long expectedRevision,
                                          C2S_OnboardingActionPacket.Action action, UUID nationId) {
        if (!pending(player)) return;
        if (SEARCHING.contains(player.getUUID())) return;
        NationSavedData nations = NationSavedData.get(player.server);
        switch (action) {
            case APPLY -> {
                NationSavedData.ApplicationResult result = nations.applyToNation(player.getUUID(),
                        player.getGameProfile().getName(), nationId, expectedRevision);
                if (result.success()) {
                    notifyManagers(player, nationId);
                    com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(player,
                            com.ruskserver.moveearth_addtional.advancement.ModCriteria.NATION_APPLIED);
                }
                sendOnboarding(player, applicationMessage(result.status()), result.success());
            }
            case CANCEL -> {
                NationSavedData.ApplicationResult result = nations.cancelApplication(
                        player.getUUID(), expectedRevision);
                sendOnboarding(player, applicationMessage(result.status()), result.success());
            }
            case WILDERNESS -> {
                NationSavedData.JoinApplication application = nations.joinApplicationFor(
                        player.getUUID()).orElse(null);
                if (application != null) {
                    NationSavedData.ApplicationResult cancelled = nations.cancelApplication(
                            player.getUUID(), expectedRevision);
                    if (!cancelled.success()) {
                        sendOnboarding(player, applicationMessage(cancelled.status()), false);
                        return;
                    }
                } else if (expectedRevision != nations.revision()) {
                    sendOnboarding(player, applicationMessage(NationSavedData.ApplicationStatus.STALE), false);
                    return;
                }
                releaseForSearch(player);
                RandomSpawnHandler.beginInitialRandomSpawn(player);
            }
            case REFRESH -> sendOnboarding(player, "", true);
            case UNKNOWN -> sendOnboarding(player,
                    "screen.moveearth_addtional.onboarding.action.invalid", false);
        }
    }

    public static void handleManagerAction(ServerPlayer player, long expectedRevision,
                                           C2S_NationApplicationActionPacket.Action action, UUID applicantId) {
        NationSavedData nations = NationSavedData.get(player.server);
        if (action == C2S_NationApplicationActionPacket.Action.OPEN) {
            sendApplications(player, "", true);
            return;
        }
        boolean approve = action == C2S_NationApplicationActionPacket.Action.APPROVE;
        if (!approve && action != C2S_NationApplicationActionPacket.Action.REJECT) {
            sendApplications(player, "screen.moveearth_addtional.onboarding.action.invalid", false);
            return;
        }
        NationSavedData.ApplicationResult result = nations.decideApplication(
                player.getUUID(), applicantId, approve, expectedRevision);
        ServerPlayer applicant = applicantId == null ? null : player.server.getPlayerList().getPlayer(applicantId);
        if (result.success() && applicant != null) {
            applicant.sendSystemMessage(approve
                    ? MoveEarthMessage.success(net.minecraft.network.chat.Component.translatable(
                    "screen.moveearth_addtional.onboarding.application.approved"))
                    : MoveEarthMessage.error(net.minecraft.network.chat.Component.translatable(
                    "screen.moveearth_addtional.onboarding.application.rejected")));
            if (approve) {
                UUID nationId = nations.nationIdFor(applicantId).orElse(null);
                if (nationId != null) {
                    com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData technology =
                            com.ruskserver.moveearth_addtional.s2.technology.NationTechnologySavedData.get(applicant.server);
                    technology.recordObjective(applicant,
                            com.ruskserver.moveearth_addtional.s2.technology.TechnologyDefinition.ObjectiveType.JOIN_OR_FOUND_NATION,
                            null, 1L, applicant.blockPosition());
                }
                releaseForSearch(applicant);
                if (nationId != null) RandomSpawnHandler.beginNationSpawnSearch(applicant, nationId);
                com.ruskserver.moveearth_addtional.advancement.ModCriteria.trigger(applicant,
                        com.ruskserver.moveearth_addtional.advancement.ModCriteria.NATION_CITIZEN);
            } else {
                sendOnboarding(applicant, applicationMessage(result.status()), false);
            }
        }
        sendApplications(player, applicationMessage(result.status()), result.success());
    }

    public static void sendOnboarding(ServerPlayer player, String messageKey, boolean success) {
        sendOnboarding(player, messageKey, success, true);
    }

    private static void sendOnboarding(ServerPlayer player, String messageKey, boolean success, boolean force) {
        NationSavedData nations = NationSavedData.get(player.server);
        TerritorySavedData territories = TerritorySavedData.get(player.server);
        NationSavedData.JoinApplication application = nations.joinApplicationFor(player.getUUID()).orElse(null);
        String applicationNationName = application == null ? "" : nations.nation(application.nationId())
                .map(NationSavedData.Nation::name).orElse("");
        Map<UUID, Integer> controlledCoreCounts = new HashMap<>();
        for (TerritorySavedData.CoreRecord core : territories.cores()) {
            if (core.state() == TerritorySavedData.CoreState.ACTIVE
                    || core.state() == TerritorySavedData.CoreState.EXPOSED) {
                controlledCoreCounts.merge(core.nationId(), 1, Integer::sum);
            }
        }
        var entries = nations.nations().values().stream()
                .sorted(Comparator.comparing(NationSavedData.Nation::name, String.CASE_INSENSITIVE_ORDER))
                .limit(512)
                .map(nation -> new S2C_OnboardingPacket.NationEntry(nation.id(), nation.name(), nation.tag(),
                        nation.members().size(), controlledCoreCounts.getOrDefault(nation.id(), 0)))
                .toList();
        boolean searching = SEARCHING.contains(player.getUUID());
        OnboardingSyncState nextState = new OnboardingSyncState(nations.revision(),
                application == null ? null : application.nationId(),
                application == null ? 0L : application.requestedAt(), searching, entries.hashCode());
        if (!force && nextState.equals(LAST_SENT.get(player.getUUID()))) return;
        LAST_SENT.put(player.getUUID(), nextState);
        PacketDistributor.sendToPlayer(player, new S2C_OnboardingPacket(nations.revision(),
                application == null ? null : application.nationId(), applicationNationName,
                application == null ? 0L : application.requestedAt(), searching, entries,
                messageKey == null ? "" : messageKey, success));
    }

    public static void sendApplications(ServerPlayer player, String messageKey, boolean success) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID nationId = nations.nationIdFor(player.getUUID()).orElse(null);
        boolean allowed = nationId != null && nations.can(player.getUUID(), S2Permission.MANAGE_MEMBERS);
        var entries = allowed ? nations.joinApplicationsFor(nationId).stream().limit(512)
                .map(application -> new S2C_NationApplicationsPacket.Entry(application.applicantId(),
                        application.applicantName(), application.requestedAt(),
                        player.server.getPlayerList().getPlayer(application.applicantId()) != null))
                .toList() : java.util.List.<S2C_NationApplicationsPacket.Entry>of();
        String effectiveMessage = allowed ? messageKey : "screen.moveearth_addtional.onboarding.application.no_permission";
        PacketDistributor.sendToPlayer(player, new S2C_NationApplicationsPacket(
                nations.revision(), entries, effectiveMessage == null ? "" : effectiveMessage,
                allowed && success));
    }

    private static void notifyManagers(ServerPlayer applicant, UUID nationId) {
        NationSavedData nations = NationSavedData.get(applicant.server);
        NationSavedData.Nation nation = nations.nation(nationId).orElse(null);
        if (nation == null) return;
        for (UUID memberId : nation.members().keySet()) {
            if (!nations.can(memberId, S2Permission.MANAGE_MEMBERS)) continue;
            ServerPlayer manager = applicant.server.getPlayerList().getPlayer(memberId);
            if (manager != null) manager.sendSystemMessage(MoveEarthMessage.info(
                    net.minecraft.network.chat.Component.translatable(
                            "screen.moveearth_addtional.onboarding.application.received",
                            applicant.getGameProfile().getName())));
        }
        NationNotificationService.publish(applicant.server, java.util.List.of(nationId),
                NationNotificationSavedData.EventType.JOIN_APPLICATION, null, null, null,
                java.util.List.of(applicant.getGameProfile().getName()));
    }

    private static String applicationMessage(NationSavedData.ApplicationStatus status) {
        return "screen.moveearth_addtional.onboarding.application."
                + status.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static void hold(ServerPlayer player) {
        HOLDS.put(player.getUUID(), new HoldPosition(player.level().dimension().location(), player.position()));
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    private static boolean held(ServerPlayer player) {
        return pending(player) && HOLDS.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !held(player)) return;
        HoldPosition hold = HOLDS.get(player.getUUID());
        ServerLevel level = player.serverLevel();
        if (!level.dimension().location().equals(hold.dimension)) {
            hold(player);
            hold = HOLDS.get(player.getUUID());
        }
        if (player.position().distanceToSqr(hold.position) > 0.01D) {
            player.teleportTo(level, hold.position.x, hold.position.y, hold.position.z,
                    player.getYRot(), player.getXRot());
        }
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        if (player.tickCount % 20 == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, false));
        }
        if (player.tickCount % 40 == 1) sendOnboarding(player, "", true, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingIncomingDamageEvent event) {
        if ((event.getEntity() instanceof ServerPlayer victim && held(victim))
                || (event.getSource().getEntity() instanceof ServerPlayer attacker && held(attacker))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && held(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && held(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && held(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && held(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && held(player)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HOLDS.remove(player.getUUID());
            SEARCHING.remove(player.getUUID());
            LAST_SENT.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        HOLDS.clear();
        SEARCHING.clear();
        LAST_SENT.clear();
    }

    private static CompoundTag persistedData(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
    }

    private record HoldPosition(ResourceLocation dimension, net.minecraft.world.phys.Vec3 position) { }

    private record OnboardingSyncState(long revision, UUID applicationNationId, long requestedAt,
                                       boolean searching, int entriesHash) { }
}
