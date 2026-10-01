package com.ruskserver.moveearth_addtional.nether;

import com.ruskserver.moveearth_addtional.Moveearth_addtional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enemies summoned by a gate generator. Only players can hurt them, so a mob
 * grinder or a Create saw cannot farm the fight; they drop nothing of their own,
 * since the shards are the reward; they cannot set fire or break blocks; and one
 * left over from an earlier fight, such as after a restart, vanishes on load.
 */
@EventBusSubscriber(modid = Moveearth_addtional.MODID)
public final class NetherGateMobs {
    static final String ENCOUNTER_TAG = "MoveEarthNetherGate";
    private static final ResourceLocation HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nether_gate_health");
    private static final ResourceLocation DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(Moveearth_addtional.MODID, "nether_gate_damage");
    /** Fights running in this server session; anything else tagged is stale. */
    static final Map<UUID, BlockPos> ACTIVE = new ConcurrentHashMap<>();

    private NetherGateMobs() { }

    static Mob spawn(ServerLevel level, NetherGateWaves.Kind kind, BlockPos generator, UUID encounter,
                     RandomSource random) {
        EntityType<? extends Mob> type = switch (kind) {
            case WITHER_SKELETON -> EntityType.WITHER_SKELETON;
            case BLAZE -> EntityType.BLAZE;
            case PIGLIN_BRUTE -> EntityType.PIGLIN_BRUTE;
        };
        Mob mob = type.create(level);
        if (mob == null) return null;
        BlockPos at = spawnPosition(level, generator, random, kind == NetherGateWaves.Kind.BLAZE);
        mob.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        mob.getPersistentData().putUUID(ENCOUNTER_TAG, encounter);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
        strengthen(mob, Attributes.MAX_HEALTH, HEALTH_ID, NetherGateConfig.fightHealth());
        strengthen(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, NetherGateConfig.fightDamage());
        mob.setHealth(mob.getMaxHealth());
        mob.setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.values()) mob.setDropChance(slot, 0.0F);
        if (mob instanceof AbstractPiglin piglin) piglin.setImmuneToZombification(true);
        Player target = level.getNearestPlayer(mob, NetherGateConfig.fightLeash());
        if (target != null && !target.isCreative() && !target.isSpectator()) mob.setTarget(target);
        return level.addFreshEntity(mob) ? mob : null;
    }

    static boolean tagged(Entity entity) {
        return entity.getPersistentData().hasUUID(ENCOUNTER_TAG);
    }

    static BlockPos spawnPosition(ServerLevel level, BlockPos generator, RandomSource random, boolean flying) {
        for (int attempt = 0; attempt < 24; attempt++) {
            int dx = random.nextIntBetweenInclusive(-6, 6);
            int dz = random.nextIntBetweenInclusive(-6, 6);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos feet = generator.offset(dx, dy, dz);
                if (!level.isLoaded(feet)) continue;
                boolean clear = level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && level.getBlockState(feet.above(2)).getCollisionShape(level, feet.above(2)).isEmpty();
                boolean floor = level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
                if (clear && (floor || flying) && level.getFluidState(feet).isEmpty()) return flying ? feet.above() : feet;
            }
        }
        return generator.above();
    }

    private static void strengthen(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                   ResourceLocation id, double multiplier) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null || multiplier == 1.0D || instance.hasModifier(id)) return;
        instance.addPermanentModifier(new AttributeModifier(id, multiplier - 1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!tagged(event.getEntity())) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (com.ruskserver.moveearth_addtional.s2.combat.RealPlayers.attacker(event.getSource()) == null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (tagged(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onGriefing(EntityMobGriefingEvent event) {
        if (event.getEntity() != null && tagged(event.getEntity())) event.setCanGrief(false);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        NetherGateBattles.clear();
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !tagged(event.getEntity())) return;
        UUID encounter = event.getEntity().getPersistentData().getUUID(ENCOUNTER_TAG);
        if (!ACTIVE.containsKey(encounter)) event.setCanceled(true);
    }
}
