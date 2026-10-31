package com.ruskserver.moveearth_addtional.s2.provenance;

import com.ruskserver.moveearth_addtional.s2.combat.RealPlayers;
import com.ruskserver.moveearth_addtional.s2.dispatch.AttributionSnapshotService;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

/** Source gate for artificial mutations. Unscoped natural/system updates are not blanket-disabled. */
public final class WorldMutationGuard {
    private WorldMutationGuard() { }
    public record BlockOrigin(ServerLevel level, BlockPos pos) { }

    public static ActionSource fromEntity(Entity entity) {
        if (entity == null) return ActionSource.UNKNOWN;
        if (entity instanceof Mob) return ActionSource.MOB;
        var snapshot = AttributionSnapshotService.attribution(entity, "world_mutation");
        if (snapshot != null) return fromAttack(snapshot);
        if (entity.getPersistentData().getBoolean("moveearth_mob_origin")) return ActionSource.MOB;
        var player = RealPlayers.real(entity);
        if (player != null) {
            var captured = AttributionSnapshotService.capture(player);
            return new ActionSource(captured.actorId(), captured.nationId(), ActionSource.Kind.ATTRIBUTED);
        }
        Entity owner = entity instanceof Projectile projectile ? projectile.getOwner()
                : entity instanceof PrimedTnt tnt ? tnt.getOwner() : null;
        if (owner instanceof Mob || entity instanceof Mob) return ActionSource.MOB;
        player = RealPlayers.real(owner);
        if (player != null) {
            var captured = AttributionSnapshotService.capture(player);
            return new ActionSource(captured.actorId(), captured.nationId(), ActionSource.Kind.ATTRIBUTED);
        }
        if ((entity instanceof FallingBlockEntity || entity instanceof net.minecraft.world.entity.LightningBolt
                || entity instanceof net.minecraft.world.entity.item.ItemEntity)
                && !entity.getPersistentData().getBoolean("moveearth_unknown_origin")) {
            return ActionSource.NATURAL;
        }
        if (entity instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity
                && entity.level() instanceof ServerLevel level) return fromBlock(level, entity.blockPosition());
        return ActionSource.UNKNOWN;
    }

    public static ActionSource fromAttack(SiegeService.AttackAttribution attack) {
        return attack == null ? ActionSource.UNKNOWN
                : new ActionSource(attack.actorId(), attack.nationId(), ActionSource.Kind.ATTRIBUTED);
    }

    public static ActionSource fromBlock(ServerLevel level, BlockPos pos) {
        BlockEntity block = level.getBlockEntity(pos);
        var sourceBlock = level.getBlockState(pos).getBlock();
        // Vanilla environmental block entities (sensors, gateways, beehives, etc.) are not weapons.
        // Dispensers/pistons and programmable construction blocks remain artificial actors.
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(sourceBlock);
        if (block != null && id != null && "minecraft".equals(id.getNamespace())
                && !(sourceBlock instanceof net.minecraft.world.level.block.DispenserBlock)
                && !(sourceBlock instanceof net.minecraft.world.level.block.CrafterBlock)
                && !(sourceBlock instanceof net.minecraft.world.level.block.CommandBlock)
                && !(sourceBlock instanceof net.minecraft.world.level.block.StructureBlock)
                && !(sourceBlock instanceof net.minecraft.world.level.block.JigsawBlock)
                && !(sourceBlock instanceof net.minecraft.world.level.block.piston.MovingPistonBlock)) return ActionSource.NATURAL;
        ActionSource recordedBlock = recorded(block);
        if (recordedBlock != null) return recordedBlock;
        if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.piston.PistonBaseBlock) {
            var recorded = ActuatorOwnershipSavedData.get(level.getServer()).find(level.dimension().location(), pos);
            if (recorded != null) return recorded;
        }
        var territories = TerritorySavedData.get(level.getServer());
        UUID nation = territories.core(level.dimension().location(), pos)
                .map(com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData.CoreRecord::nationId).orElse(null);
        if (nation == null) nation = com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData.get(level.getServer())
                .at(level.dimension().location(), pos).map(com.ruskserver.moveearth_addtional.s2.vehicle.VehicleSavedData.VehicleRecord::nationId)
                .orElse(null);
        try {
            if (nation == null) nation = com.ruskserver.moveearth_addtional.compat.vehicle.SableVehicleTopology.owningNation(level, pos);
        } catch (RuntimeException | LinkageError absent) { }
        if (nation == null) nation = territories.controllingNation(
                level.getServer(), level.dimension().location(), pos).orElse(null);
        return nation == null ? ActionSource.UNKNOWN : new ActionSource(null, nation, ActionSource.Kind.ATTRIBUTED);
    }

    public static ActionSource recorded(BlockEntity block) {
        if (block == null) return null;
        var data = block.getPersistentData();
        if (!data.hasUUID("moveearth_action_actor") && !data.hasUUID("moveearth_action_nation")) return null;
        return new ActionSource(data.hasUUID("moveearth_action_actor") ? data.getUUID("moveearth_action_actor") : null,
                data.hasUUID("moveearth_action_nation") ? data.getUUID("moveearth_action_nation") : null,
                ActionSource.Kind.ATTRIBUTED);
    }

    public static void inherit(BlockEntity block) {
        if (block == null || !(block.getLevel() instanceof ServerLevel) || recorded(block) != null) return;
        var source = source(ActionContext.current());
        if (!source.attributable()) return;
        var data = block.getPersistentData();
        if (source.actor() != null) data.putUUID("moveearth_action_actor", source.actor());
        if (source.nation() != null) data.putUUID("moveearth_action_nation", source.nation());
        block.setChanged();
    }

    public static Object captureExplosionOrigin(Explosion explosion, Object context) {
        if (explosion instanceof ExplosionOrigin access && access.moveearth$level() instanceof ServerLevel level
                && "com.cbc_more_content.effects.BombExplosionHandler$TerrainShellExplosion".equals(explosion.getClass().getName())) {
            var charge = com.ruskserver.moveearth_addtional.compat.warnautics.WarnauticsC4SavedData.get(level)
                    .peek(BlockPos.containing(explosion.center()));
            if (charge != null) return new ActionSource(charge.placerId(), charge.nationId(), ActionSource.Kind.ATTRIBUTED);
        }
        var damage = explosion instanceof ExplosionOrigin access ? access.moveearth$damageSource() : null;
        if (explosion.getDirectSourceEntity() == null && damage != null) {
            if (damage.getDirectEntity() != null) return damage.getDirectEntity();
            if (damage.getEntity() != null) return damage.getEntity();
        }
        return context;
    }

    public static Object explosionOrigin(Explosion explosion) {
        Entity direct = explosion.getDirectSourceEntity();
        if (direct != null) return direct;
        if (explosion instanceof ExplosionOrigin tracked && tracked.moveearth$origin() != null) {
            return tracked.moveearth$origin();
        }
        return ActionSource.UNKNOWN;
    }

    public static ActionSource source(Object origin) {
        if (origin instanceof ActionSource source) return source;
        if (origin instanceof Entity entity) return fromEntity(entity);
        if (origin instanceof BlockEntity block && block.getLevel() instanceof ServerLevel level) {
            var recorded = recorded(block);
            return recorded != null ? recorded : fromBlock(level, block.getBlockPos());
        }
        if (origin instanceof BlockOrigin block) return fromBlock(block.level(), block.pos());
        return ActionSource.UNKNOWN;
    }

    public static Entity currentEntity() {
        return ActionContext.current() instanceof Entity entity ? entity : null;
    }

    public static boolean allowsExplosion(Explosion explosion) { return source(explosionOrigin(explosion)).allowed(); }

    public static boolean allowsObjectDamage(net.minecraft.world.damagesource.DamageSource damage, Entity target) {
        if (damage.getDirectEntity() != null) return fromEntity(damage.getDirectEntity()).allowed();
        if (damage.getEntity() != null) return fromEntity(damage.getEntity()).allowed();
        if (ActionContext.current() != null && ActionContext.current() != target) return source(ActionContext.current()).allowed();
        // Fire, void and other natural damage remain unchanged; unidentified weapons do not.
        if (damage.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                || damage.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) return false;
        var type = damage.typeHolder().unwrapKey();
        return type.isPresent() && "minecraft".equals(type.get().location().getNamespace());
    }

    public static boolean allowsMutation(ServerLevel level, BlockPos pos, BlockState replacement) {
        Object origin = ActionContext.current();
        // Natural growth/fluids/world generation and explicit system operations have no artificial context.
        if (origin == null) return true;
        // A machine updating its own lit/powered state is housekeeping, not placement/replacement.
        if (origin instanceof BlockEntity block && block.getBlockPos().equals(pos)
                && replacement != null && level.getBlockState(pos).is(replacement.getBlock())) return true;
        return source(origin).allowed();
    }
}
