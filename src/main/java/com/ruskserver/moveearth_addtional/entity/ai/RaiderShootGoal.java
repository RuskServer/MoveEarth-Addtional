package com.ruskserver.moveearth_addtional.entity.ai;

import com.ruskserver.moveearth_addtional.entity.AirshipRaiderEntity;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IGun;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public final class RaiderShootGoal extends Goal {
    private final AirshipRaiderEntity raider;
    private final float attackRange;
    private final int flankDirection;
    private RaiderTacticalState state = RaiderTacticalState.SEARCHING;
    private int burstShots;
    private int burstCooldown;
    private int movementDecisionCooldown;
    private int strafeDirection;
    private int strafeTicks;
    private int retreatTicks;
    private BlockPos reservedCover;

    public RaiderShootGoal(AirshipRaiderEntity raider, float attackRange) {
        this.raider = raider;
        this.attackRange = attackRange;
        this.flankDirection = (raider.getUUID().getLeastSignificantBits() & 1L) == 0L ? 1 : -1;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = raider.getTarget();
        return target != null && target.isAlive() && IGun.mainHandHoldGun(raider);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        IGunOperator.fromLivingEntity(raider).draw(raider::getMainHandItem);
        movementDecisionCooldown = 0;
    }

    @Override
    public void stop() {
        IGunOperator.fromLivingEntity(raider).aim(false);
        RaiderSquadMemory.releaseCover(raider.getRaidId(), reservedCover, raider.getUUID());
        reservedCover = null;
        burstShots = 0;
        burstCooldown = 0;
        retreatTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = raider.getTarget();
        if (target == null) return;
        long gameTime = raider.level().getGameTime();
        boolean visible = raider.getSensing().hasLineOfSight(target);
        double distanceSqr = raider.distanceToSqr(target);
        double distance = Math.sqrt(distanceSqr);
        IGunOperator operator = IGunOperator.fromLivingEntity(raider);
        boolean reloading = operator.getSynReloadState().getStateType().isReloading();

        if (visible) {
            RaiderSquadMemory.reportTarget(raider.getRaidId(), target.position(), gameTime);
            raider.getLookControl().setLookAt(target, 45.0F, 45.0F);
        }

        if (reloading || retreatTicks > 0 || raider.getHealth() < raider.getMaxHealth() * 0.3F) {
            state = reloading ? RaiderTacticalState.RELOADING : RaiderTacticalState.RETREATING;
            retreatTicks = Math.max(0, retreatTicks - 1);
            moveDefensively(target, gameTime);
            operator.aim(false);
            return;
        }

        if (!visible) {
            state = RaiderTacticalState.SEARCHING;
            operator.aim(false);
            Vec3 lastSeen = RaiderSquadMemory.getRecentTarget(raider.getRaidId(), gameTime);
            if (lastSeen != null && (movementDecisionCooldown-- <= 0 || raider.getNavigation().isDone())) {
                raider.getNavigation().moveTo(lastSeen.x, lastSeen.y, lastSeen.z, movementSpeed());
                movementDecisionCooldown = 20;
            }
            return;
        }

        updateCombatMovement(target, distance);
        operator.aim(true);
        if (distanceSqr > attackRange * attackRange || burstCooldown-- > 0) return;
        shoot(operator, target, distance);
    }

    private void updateCombatMovement(LivingEntity target, double distance) {
        double minRange = switch (raider.getRole()) {
            case RIFLEMAN -> 14.0D;
            case FLANKER -> 7.0D;
            case HEAVY -> 18.0D;
        };
        double maxRange = switch (raider.getRole()) {
            case RIFLEMAN -> 27.0D;
            case FLANKER -> 16.0D;
            case HEAVY -> 32.0D;
        };

        if (movementDecisionCooldown-- > 0 && !raider.getNavigation().isDone()) return;
        movementDecisionCooldown = 12 + raider.getRandom().nextInt(10);

        if (distance < minRange) {
            state = RaiderTacticalState.RETREATING;
            moveAwayFrom(target.position(), 7.0D, 1.1D);
            return;
        }
        if (distance > maxRange) {
            state = RaiderTacticalState.ENGAGING;
            raider.getNavigation().moveTo(target, movementSpeed());
            return;
        }

        if (raider.getRole() == RaiderRole.FLANKER) {
            state = RaiderTacticalState.FLANKING;
            Vec3 radial = raider.position().subtract(target.position());
            radial = new Vec3(radial.x, 0.0D, radial.z);
            if (radial.lengthSqr() < 0.01D) radial = new Vec3(1.0D, 0.0D, 0.0D);
            radial = radial.normalize();
            Vec3 perpendicular = new Vec3(-radial.z * flankDirection, 0.0D, radial.x * flankDirection);
            Vec3 flankPoint = target.position().add(radial.scale(11.0D)).add(perpendicular.scale(9.0D));
            raider.getNavigation().moveTo(flankPoint.x, flankPoint.y, flankPoint.z, 1.15D);
            return;
        }

        if (raider.getRole() == RaiderRole.HEAVY && raider.getRandom().nextFloat() < 0.65F) {
            state = RaiderTacticalState.ENGAGING;
            raider.getNavigation().stop();
            return;
        }

        state = RaiderTacticalState.ENGAGING;
        if (strafeTicks-- <= 0) {
            strafeDirection = raider.getRandom().nextBoolean() ? 1 : -1;
            strafeTicks = 20 + raider.getRandom().nextInt(25);
        }
        Vec3 radial = target.position().subtract(raider.position());
        radial = new Vec3(radial.x, 0.0D, radial.z).normalize();
        Vec3 side = new Vec3(-radial.z * strafeDirection, 0.0D, radial.x * strafeDirection);
        Vec3 separation = separationVector();
        Vec3 destination = raider.position().add(side.scale(4.0D)).add(separation.scale(2.0D));
        raider.getNavigation().moveTo(destination.x, destination.y, destination.z, movementSpeed());
    }

    private void moveDefensively(LivingEntity target, long gameTime) {
        if (reservedCover != null && raider.distanceToSqr(Vec3.atCenterOf(reservedCover)) < 3.0D) {
            raider.getNavigation().stop();
            return;
        }
        if (movementDecisionCooldown-- > 0 && !raider.getNavigation().isDone()) return;
        movementDecisionCooldown = 25;
        BlockPos cover = findCover(target, gameTime);
        if (cover != null) {
            RaiderSquadMemory.releaseCover(raider.getRaidId(), reservedCover, raider.getUUID());
            reservedCover = cover;
            state = RaiderTacticalState.MOVING_TO_COVER;
            raider.getNavigation().moveTo(cover.getX() + 0.5D, cover.getY(), cover.getZ() + 0.5D, 1.15D);
        } else {
            moveAwayFrom(target.position(), 8.0D, 1.15D);
        }
    }

    private BlockPos findCover(LivingEntity target, long gameTime) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos origin = raider.blockPosition();
        for (int i = 0; i < 18; i++) {
            int radius = 4 + raider.getRandom().nextInt(8);
            double angle = raider.getRandom().nextDouble() * Math.PI * 2.0D;
            BlockPos candidate = origin.offset(Mth.floor(Math.cos(angle) * radius), raider.getRandom().nextInt(5) - 2,
                    Mth.floor(Math.sin(angle) * radius));
            candidate = findStandable(candidate);
            if (candidate == null || !isHiddenFrom(candidate, target)) continue;
            if (raider.getNavigation().createPath(candidate, 0) == null) continue;
            if (!RaiderSquadMemory.reserveCover(raider.getRaidId(), candidate, raider.getUUID(), gameTime)) continue;
            double score = candidate.distSqr(origin) + candidate.distSqr(target.blockPosition()) * 0.08D;
            if (score < bestScore) {
                if (best != null) RaiderSquadMemory.releaseCover(raider.getRaidId(), best, raider.getUUID());
                best = candidate;
                bestScore = score;
            } else {
                RaiderSquadMemory.releaseCover(raider.getRaidId(), candidate, raider.getUUID());
            }
        }
        return best;
    }

    private BlockPos findStandable(BlockPos candidate) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos feet = candidate.offset(0, dy, 0);
            if (raider.level().getBlockState(feet).isAir()
                    && raider.level().getBlockState(feet.above()).isAir()
                    && !raider.level().getBlockState(feet.below()).isAir()) return feet;
        }
        return null;
    }

    private boolean isHiddenFrom(BlockPos candidate, LivingEntity target) {
        Vec3 from = Vec3.atBottomCenterOf(candidate).add(0.0D, raider.getEyeHeight(), 0.0D);
        HitResult hit = raider.level().clip(new ClipContext(from, target.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, raider));
        return hit.getType() != HitResult.Type.MISS;
    }

    private void moveAwayFrom(Vec3 danger, double amount, double speed) {
        Vec3 away = raider.position().subtract(danger);
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 0.01D) away = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 destination = raider.position().add(away.normalize().scale(amount)).add(separationVector().scale(2.0D));
        raider.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
    }

    private Vec3 separationVector() {
        Vec3 separation = Vec3.ZERO;
        for (AirshipRaiderEntity ally : raider.level().getEntitiesOfClass(AirshipRaiderEntity.class,
                raider.getBoundingBox().inflate(3.0D), other -> other != raider && other.getRaidId() == raider.getRaidId())) {
            Vec3 away = raider.position().subtract(ally.position());
            if (away.lengthSqr() > 0.01D) separation = separation.add(away.normalize());
        }
        return separation.lengthSqr() > 0.01D ? separation.normalize() : Vec3.ZERO;
    }

    private double movementSpeed() {
        return raider.getRole() == RaiderRole.HEAVY ? 0.75D : 1.0D;
    }

    private void shoot(IGunOperator operator, LivingEntity target, double distance) {
        double leadTicks = Mth.clamp(distance / 18.0D, 0.0D, 4.0D);
        Vec3 aimPoint = target.getEyePosition().add(target.getDeltaMovement().scale(leadTicks));
        Vec3 origin = raider.getEyePosition();
        double dx = aimPoint.x - origin.x;
        double dy = aimPoint.y - origin.y;
        double dz = aimPoint.z - origin.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float baseYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float basePitch = (float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
        raider.setYRot(baseYaw);
        raider.setYHeadRot(baseYaw);
        raider.setYBodyRot(baseYaw);
        raider.setXRot(basePitch);

        float spread = switch (raider.getRaidDifficulty()) {
            case NORMAL -> 1.7F;
            case ELITE -> 0.9F;
            case LARGE -> 0.55F;
        };
        if (!raider.getNavigation().isDone()) spread *= 1.35F;
        float pitch = basePitch + Mth.nextFloat(raider.getRandom(), -spread, spread);
        float yaw = baseYaw + Mth.nextFloat(raider.getRandom(), -spread, spread);
        ShootResult result = operator.shoot(() -> pitch, () -> yaw);
        if (result == ShootResult.SUCCESS) {
            burstShots++;
            int burstLimit = raider.getRole() == RaiderRole.HEAVY ? 6 : raider.getRole() == RaiderRole.FLANKER ? 4 : 3;
            if (burstShots >= burstLimit) {
                burstShots = 0;
                burstCooldown = raider.getRole() == RaiderRole.HEAVY ? 10 : 14 + raider.getRandom().nextInt(14);
            } else {
                burstCooldown = 3;
            }
        } else if (result == ShootResult.NEED_BOLT) {
            operator.bolt();
            burstCooldown = 10;
        } else if (result == ShootResult.NO_AMMO) {
            operator.reload();
            retreatTicks = 50;
            burstCooldown = 40;
        } else {
            burstCooldown = 5;
        }
    }
}
