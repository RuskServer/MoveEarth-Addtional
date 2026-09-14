package com.ruskserver.moveearth_addtional.compat.warnautics;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import com.ruskserver.moveearth_addtional.s2.nation.NationSavedData;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementEntry;
import com.ruskserver.moveearth_addtional.s2.reinforcement.ReinforcementSavedData;
import com.ruskserver.moveearth_addtional.s2.siege.SiegeService;
import com.ruskserver.moveearth_addtional.s2.territory.TerritorySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.phys.BlockHitResult;

import java.util.UUID;

/** C4 placement policy and tracking without a binary dependency on Warnautics. */
@EventBusSubscriber(modid = Moveearth_addtional.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class WarnauticsC4Placement {
    private WarnauticsC4Placement() { }

    public static boolean isC4(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && "cbc_more_content".equals(id.getNamespace()) && "c4".equals(id.getPath());
    }

    public static boolean isC4Projectile(net.minecraft.world.entity.Entity entity) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && "cbc_more_content".equals(id.getNamespace()) && "c4".equals(id.getPath());
    }

    public static BlockPos support(BlockPos chargePos, BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (!"facing".equals(property.getName())) continue;
            Comparable<?> value = value(state, property);
            if (value instanceof Direction facing) return chargePos.relative(facing.getOpposite());
        }
        return chargePos.below();
    }

    /** Narrow Bastion exception: only a hostile player's C4 attached to reinforced enemy structure. */
    public static boolean canPlaceOnHostileReinforcement(ServerPlayer player, ServerLevel level,
                                                          BlockPos chargePos, BlockState state) {
        if (!isC4(state)) return false;
        return canAttachToHostileReinforcement(player, level, support(chargePos, state));
    }

    private static boolean canAttachToHostileReinforcement(ServerPlayer player, ServerLevel level,
                                                             BlockPos support) {
        ReinforcementEntry reinforcement = ReinforcementSavedData.get(level).get(support).orElse(null);
        TerritorySavedData.CoreRecord core = TerritorySavedData.get(level.getServer())
                .core(level.dimension().location(), support).orElse(null);
        boolean protectedTarget = reinforcement != null && reinforcement.enabled()
                || core != null && core.state() == TerritorySavedData.CoreState.EXPOSED;
        if (!protectedTarget) return false;
        NationSavedData nations = NationSavedData.get(level.getServer());
        UUID actorNation = nations.nationIdFor(player.getUUID()).orElse(null);
        UUID defenderNation = TerritorySavedData.get(level.getServer())
                .controllingNation(level.getServer(), level.dimension().location(), support).orElse(null);
        if (defenderNation == null || defenderNation.equals(actorNation)
                || (actorNation != null && nations.relation(actorNation, defenderNation)
                != NationSavedData.DiplomacyRelation.HOSTILE)) {
            return false;
        }
        return !SiegeService.peaceTruceBlocks(new SiegeService.AttackAttribution(
                actorNation, player.getUUID(), "warnautics_c4_placement"), level, support);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!isC4Projectile(event.getProjectile())
                || !(event.getProjectile().level() instanceof ServerLevel level)
                || !(event.getProjectile().getOwner() instanceof ServerPlayer player)
                || !(event.getRayTraceResult() instanceof BlockHitResult hit)) return;
        BlockPos support = hit.getBlockPos();
        BlockPos chargePos = support.relative(hit.getDirection());
        UUID defenderNation = TerritorySavedData.get(level.getServer())
                .controllingNation(level.getServer(), level.dimension().location(), support)
                .or(() -> TerritorySavedData.get(level.getServer())
                        .controllingNation(level.getServer(), level.dimension().location(), chargePos))
                .orElse(null);
        UUID actorNation = NationSavedData.get(level.getServer())
                .nationIdFor(player.getUUID()).orElse(null);
        boolean foreignTerritory = defenderNation != null
                && (actorNation == null || !defenderNation.equals(actorNation));
        if (foreignTerritory && !player.hasPermissions(2)
                && !canAttachToHostileReinforcement(player, level, support)) {
            event.setCanceled(true);
            event.getProjectile().discard();
            player.sendSystemMessage(com.ruskserver.moveearth_addtional.ui.MoveEarthMessage.error(
                    net.minecraft.network.chat.Component.literal(
                            "C4は敵対国家の補強された外面にだけ設置できます。")));
            return;
        }
        record(level, chargePos, support, player, actorNation);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || event instanceof BlockEvent.EntityMultiPlaceEvent
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || !isC4(event.getPlacedBlock())) return;
        UUID nationId = NationSavedData.get(level.getServer()).nationIdFor(player.getUUID()).orElse(null);
        BlockPos support = support(event.getPos(), event.getPlacedBlock());
        record(level, event.getPos(), support, player, nationId);
    }

    private static void record(ServerLevel level, BlockPos chargePos, BlockPos support,
                               ServerPlayer player, UUID nationId) {
        WarnauticsC4SavedData.get(level).put(
                chargePos, support, player.getUUID(), nationId, level.getGameTime());
        if (TerritorySavedData.get(level.getServer())
                .controllingNation(level.getServer(), level.dimension().location(), support)
                .filter(defender -> nationId == null || !defender.equals(nationId)).isPresent()) {
            SiegeService.recordAttack(new SiegeService.AttackAttribution(
                    nationId, player.getUUID(), "warnautics_c4_placed"), level, support, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isC4(event.getState())) return;
        WarnauticsC4SavedData.get(level).remove(event.getPos());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> value(BlockState state, Property<?> property) {
        return state.getValue((Property) property);
    }
}
