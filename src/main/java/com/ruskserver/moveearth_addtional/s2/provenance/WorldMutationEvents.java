package com.ruskserver.moveearth_addtional.s2.provenance;

import static com.ruskserver.moveearth_addtional.analytics.provenance.MutationRejectionAudit.rejected;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.dispatch.AttributionSnapshotService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class WorldMutationEvents {
    private WorldMutationEvents() { }

    public static boolean rejectExplosion(ExplosionEvent.Detonate event) {
        if (WorldMutationGuard.allowsExplosion(event.getExplosion())) return false;
        if (!(event.getExplosion() instanceof ExplosionOrigin tracked) || tracked.moveearth$markRejectionLogged()) {
            String route = event.getExplosion().getClass().getName().startsWith("rbasamoyai.createbigcannons.")
                    ? "cbc.explosion" : "explosion";
            rejected(route, event.getLevel(), net.minecraft.core.BlockPos.containing(event.getExplosion().center()));
        }
        event.getAffectedBlocks().clear();
        // This policy is about objects/terrain; it does not change combat damage to players or mobs.
        event.getAffectedEntities().removeIf(entity -> !(entity instanceof LivingEntity) || entity instanceof ArmorStand);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel) rejectExplosion(event);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosionFinal(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel) rejectExplosion(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        ActionSource source = WorldMutationGuard.fromEntity(event.getEntity());
        if (!source.allowed()) source = WorldMutationGuard.source(ActionContext.current());
        if (!source.allowed()) {
            rejected("event.place", (ServerLevel) event.getLevel(), event.getPos());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rememberPlacement(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ActionSource source = WorldMutationGuard.fromEntity(event.getEntity());
        if (!source.attributable()) source = WorldMutationGuard.source(ActionContext.current());
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (var snapshot : multi.getReplacedBlockSnapshots()) remember(level, snapshot.getPos(), source);
        } else remember(level, event.getPos(), source);
    }

    private static void remember(ServerLevel level, net.minecraft.core.BlockPos pos, ActionSource source) {
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock) {
            ActuatorOwnershipSavedData.get(level.getServer()).put(level.dimension().location(), pos, source);
        }
        var block = level.getBlockEntity(pos);
        if (block == null || !source.attributable()) return;
        var data = block.getPersistentData();
        data.remove("moveearth_action_actor");
        data.remove("moveearth_action_nation");
        if (source.actor() != null) data.putUUID("moveearth_action_actor", source.actor());
        if (source.nation() != null) data.putUUID("moveearth_action_nation", source.nation());
        block.setChanged();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (!WorldMutationGuard.fromEntity(event.getPlayer()).allowed()
                && !WorldMutationGuard.source(ActionContext.current()).allowed()) {
            rejected("event.break", (ServerLevel) event.getLevel(), event.getPos());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPistonRemoved(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getState().getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock) {
            ActuatorOwnershipSavedData.get(level.getServer()).put(level.dimension().location(), event.getPos(), null);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPiston(PistonEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level
                && !WorldMutationGuard.fromBlock(level, event.getPos()).allowed()) {
            rejected("piston.event", level, event.getPos());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpawn(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || event.loadedFromDisk()) return;
        var entity = event.getEntity();
        if (entity instanceof net.minecraft.world.entity.Mob || entity instanceof net.minecraft.world.entity.item.ItemEntity) return;
        if (AttributionSnapshotService.attribution(entity, "spawn") != null) return;
        Object origin = ActionContext.current();
        if (origin == null || origin == entity) return;
        ActionSource source = WorldMutationGuard.source(origin);
        if (source.attributable()) {
            if (origin instanceof net.minecraft.world.entity.Entity parent) AttributionSnapshotService.copy(parent, entity);
            if (AttributionSnapshotService.attribution(entity, "spawn") == null) {
                AttributionSnapshotService.write(entity, new AttributionSnapshotService.Snapshot(
                        source.actor(), source.nation(), null, null));
            }
        } else if (source.kind() == ActionSource.Kind.MOB) entity.getPersistentData().putBoolean("moveearth_mob_origin", true);
        else if (source.kind() == ActionSource.Kind.UNKNOWN) entity.getPersistentData().putBoolean("moveearth_unknown_origin", true);
    }
}
