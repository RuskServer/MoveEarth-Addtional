package com.ruskserver.moveearth_addtional.compat.yigd;

import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.events.YigdEvents;
import com.ruskserver.moveearth_addtional.CompatEventHandler;
import com.ruskserver.moveearth_addtional.s2.nation.AllyAccessService;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeLootService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import com.ruskserver.moveearth_addtional.ui.MoveEarthMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

/** Loaded only when YIGD is present. No client packets or YIGD classes are bundled. */
public final class YigdTerritoryCompat {
    private YigdTerritoryCompat() { }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                YigdEvents.AllowGraveGenerationEvent.class, YigdTerritoryCompat::onGeneration);
        // Cancel after setting our decision so YIGD's timer/keys/unlocked flag cannot override land rules.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                YigdEvents.GraveClaimEvent.class, YigdTerritoryCompat::onClaim);
    }

    private static BlockPos landPosition(ServerLevel level, BlockPos pos) {
        try {
            return com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology
                    .placement(level, pos).worldPos();
        } catch (LinkageError absent) {
            return pos;
        }
    }

    private static boolean inside(ServerLevel level, BlockPos pos, UUID nation) {
        return nation != null && TerritorySavedData.get(level.getServer()).allowsStorage(
                level.getServer(), nation, level.dimension().location(), landPosition(level, pos));
    }

    private static void onGeneration(YigdEvents.AllowGraveGenerationEvent event) {
        var context = event.getDeathContext();
        var player = context.player();
        UUID nation = NationSavedData.get(player.server).nationIdFor(player.getUUID()).orElse(null);
        GraveOwnershipSavedData.get(player.server).remember(event.getGrave().getGraveId(),
                new GraveAccessPolicy.Ownership(player.getUUID(), nation,
                        !inside(context.world(), BlockPos.containing(context.deathPos()), nation)));
    }

    /** Called only when a physical grave is bound or relocated, never when merely checking access. */
    public static void placed(GraveBlockEntity block) {
        if (!(block.getLevel() instanceof ServerLevel level)) return;
        GraveComponent grave = block.getComponent();
        if (grave == null) return;
        var data = GraveOwnershipSavedData.get(level.getServer());
        var owner = data.find(grave.getGraveId());
        // Unknown pre-existing YIGD graves fail closed; don't guess death-time nation from today's roster.
        if (owner != null) data.placed(grave.getGraveId(), level.dimension().location(), block.getBlockPos(),
                inside(level, block.getBlockPos(), owner.nation()));
    }

    private static void onClaim(YigdEvents.GraveClaimEvent event) {
        var player = event.getPlayer();
        var grave = event.getGrave();
        var level = event.getLevel();
        var pos = event.getPos();
        boolean physical = level == player.serverLevel()
                && level.getBlockEntity(pos) instanceof GraveBlockEntity block
                && grave.getGraveId().equals(block.getGraveId());
        BlockPos landPos = landPosition(level, pos);
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1;
        boolean valid = physical && !player.isDeadOrDying() && !player.isSpectator()
                && !CompatEventHandler.isPlayerDown(player)
                && player.distanceToSqr(Vec3.atCenterOf(landPos)) <= reach * reach;
        var owner = GraveOwnershipSavedData.get(player.server).find(grave.getGraveId());
        var nations = NationSavedData.get(player.server);
        UUID nation = nations.nationIdFor(player.getUUID()).orElse(null);
        boolean allied = owner != null && owner.nation() != null
                && owner.nation().equals(AllyAccessService.hostAt(player, landPos))
                && AllyAccessService.canUseStorage(player, landPos);
        var loot = SiegeLootService.access(player, landPos);
        boolean siege = owner != null && owner.nation() != null
                && owner.nation().equals(loot.formerOwner()) && loot.allowed();
        boolean allowed = valid && (owner != null
                ? owner.allows(player.getUUID(), nation, allied, siege, player.hasPermissions(2))
                : player.hasPermissions(2) || grave.getOwner().id().filter(player.getUUID()::equals).isPresent());
        event.setCanClaim(allowed);
        event.setCanceled(true);
        if (!allowed) {
            boolean ja = player.clientInformation().language().startsWith("ja");
            player.sendSystemMessage(MoveEarthMessage.warning(Component.translatableWithFallback(
                    "message.moveearth_addtional.grave.protected", ja
                            ? "この墓は領土内の物資として保護されています。所属国の領土外へ配置すると開錠されます。領土内では自国民・物資利用を許可された同盟国・略奪権のある攻撃側だけが回収できます。"
                            : "This grave is protected as national storage. Placement outside its home territory unlocks it. Citizens, allies with storage permission and attackers with loot rights can recover it.")));
        }
    }
}
