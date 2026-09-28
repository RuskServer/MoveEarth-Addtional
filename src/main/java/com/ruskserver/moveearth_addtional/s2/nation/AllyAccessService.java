package com.ruskserver.moveearth_addtional.s2.nation;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.S2Permission;
import com.ruskserver.moveearth_addtional.s2.territory.NationUpkeepService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side resolution of what an allied player may do in a host nation's territory. A host grants
 * each ally, or single members of it, {@link AllyPermission}s; nothing is granted by default, and an
 * ally's own roles never reach into foreign land on their own.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class AllyAccessService {
    private static final long NOTICE_COOLDOWN_TICKS = 20L;
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

    private AllyAccessService() { }

    /** The nation whose active territory holds {@code pos}, if any. */
    public static UUID hostAt(ServerPlayer player, BlockPos pos) {
        return TerritorySavedData.get(player.server)
                .controllingNation(player.server, player.level().dimension().location(), pos).orElse(null);
    }

    /** Whether the player is a member of a nation allied with the host of {@code pos}. */
    public static boolean alliedHostAt(ServerPlayer player, BlockPos pos) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID actorNation = nations.nationIdFor(player.getUUID()).orElse(null);
        UUID host = hostAt(player, pos);
        return actorNation != null && host != null && !host.equals(actorNation) && nations.isAllied(host, actorNation);
    }

    /** Build access (Bastion placement and block breaking) as an ally; also needs Bastion access at home. */
    public static boolean canBuild(ServerPlayer player, UUID hostNation) {
        NationSavedData nations = NationSavedData.get(player.server);
        UUID actorNation = nations.nationIdFor(player.getUUID()).orElse(null);
        return grant(nations, player, hostNation, actorNation, AllyPermission.BUILD,
                nations.can(player.getUUID(), S2Permission.BASTION_ACCESS));
    }

    /** Use of the host's storage on its storage-eligible land. Storage needs no role at home. */
    public static boolean canUseStorage(ServerPlayer player, BlockPos pos) {
        NationSavedData nations = NationSavedData.get(player.server);
        if (!nations.hasAnyAllyGrant(player.getUUID(), AllyPermission.STORAGE)) return false;
        UUID host = hostAt(player, pos);
        UUID actorNation = nations.nationIdFor(player.getUUID()).orElse(null);
        ResourceLocation dimension = player.level().dimension().location();
        return grant(nations, player, host, actorNation, AllyPermission.STORAGE, actorNation != null)
                && TerritorySavedData.get(player.server).allowsStorage(player.server, host, dimension, pos);
    }

    /**
     * Reinforcement work on the host's territory, as if the player managed reinforcement there.
     * Needs the host's grant and the player's own reinforcement permission.
     */
    public static boolean canReinforce(ServerPlayer player, BlockPos pos) {
        NationSavedData nations = NationSavedData.get(player.server);
        if (!nations.hasAnyAllyGrant(player.getUUID(), AllyPermission.REINFORCE)) return false;
        UUID host = hostAt(player, pos);
        UUID actorNation = nations.nationIdFor(player.getUUID()).orElse(null);
        ResourceLocation dimension = player.level().dimension().location();
        return grant(nations, player, host, actorNation, AllyPermission.REINFORCE,
                nations.can(player.getUUID(), S2Permission.MANAGE_REINFORCEMENT))
                && TerritorySavedData.get(player.server).allowsReinforcement(player.server, host, dimension, pos);
    }

    private static boolean grant(NationSavedData nations, ServerPlayer player, UUID host, UUID actorNation,
                                 AllyPermission permission, boolean ownRolePermission) {
        if (host == null || actorNation == null) return false;
        boolean allied = nations.isAllied(host, actorNation);
        return AllyPermissionPolicy.foreignAccess(host, actorNation, allied,
                allied ? nations.allyNationGrant(host, actorNation) : 0,
                allied ? nations.allyPlayerGrant(host, actorNation, player.getUUID()) : 0,
                ownRolePermission, permission);
    }

    /**
     * Allies may no longer break blocks in a host's territory unless the host granted them BUILD.
     * Outsiders are left to the Siege rules, which treat their breaking as an attack.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.hasPermissions(2) || player.isCreative() || player.isSpectator()) return;
        UUID host = hostAt(player, event.getPos());
        if (host == null || !alliedHostAt(player, event.getPos())) return;
        if (!NationUpkeepService.penalty(player.server, host).bastionEnabled()) return;
        if (canBuild(player, host)) return;
        event.setCanceled(true);
        refuse(player, "message.moveearth_addtional.ally_access.build_denied");
    }

    /** Refusal notice for an ally acting without the host's grant, throttled per player. */
    public static void refuse(ServerPlayer player, String messageKey) {
        long now = player.server.overworld().getGameTime();
        Long previous = LAST_NOTICE.get(player.getUUID());
        if (previous != null && now - previous < NOTICE_COOLDOWN_TICKS) return;
        LAST_NOTICE.put(player.getUUID(), now);
        player.sendSystemMessage(MoveEarthMessage.error(Component.translatable(messageKey)));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_NOTICE.clear();
    }
}
