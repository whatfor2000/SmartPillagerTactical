package com.smartpillager.ai;

import com.smartpillager.tacz.TaczIntegration;
import com.smartpillager.entity.SmartPillagerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Tactical gun attack goal.
 * When TACZ is available, fires real TACZ gunshots via IGunOperator.
 * Falls back to arrow projectiles when TACZ is not installed.
 */
public class TacticalGunAttackGoal extends Goal {

    private final SmartPillagerEntity mob;
    private final double speedModifier;
    private final int attackIntervalMin;
    private final float attackRadius;
    private final float attackRadiusSqr;

    private int attackTime = -1;
    private int seeTime = 0;
    private boolean strafingClockwise = false;
    private boolean strafingBackwards = false;
    private int strafingTime = -1;

    private static final float HEADSHOT_BONUS = 1.5F;

    private final boolean useTacz;
    private int reloadWait = 0;

    public TacticalGunAttackGoal(SmartPillagerEntity mob, double speedModifier,
                                  int attackIntervalMin, float attackRadius) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.attackIntervalMin = attackIntervalMin;
        this.attackRadius = attackRadius;
        this.attackRadiusSqr = attackRadius * attackRadius;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.useTacz = TaczIntegration.isTaczLoaded();
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && this.mob.distanceToSqr(target) < attackRadiusSqr;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() || !this.mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        super.start();
        this.attackTime = 0;
        this.seeTime = 0;
        this.strafingTime = -1;
        this.reloadWait = 0;
    }

    @Override
    public void stop() {
        super.stop();
        this.seeTime = 0;
        this.attackTime = -1;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        double distSqr = this.mob.distanceToSqr(target);
        boolean canSee = this.mob.getSensing().hasLineOfSight(target);

        if (canSee) {
            ++this.seeTime;
        } else {
            this.seeTime = 0;
        }

        // Handle TACZ reload wait
        if (useTacz && this.reloadWait > 0) {
            this.reloadWait--;
            if (distSqr < 25.0D) this.strafingBackwards = true;
            return;
        }

        // Check if TACZ gun needs reloading
        if (useTacz && TaczIntegration.needsReload(this.mob)) {
            TaczIntegration.reloadGun(this.mob);
            this.reloadWait = 40; // 2 seconds
            return;
        }

        // Movement logic
        if (this.mob.isInCover() && canSee) {
            this.mob.getNavigation().stop();
        } else if (distSqr > attackRadiusSqr * 0.5) {
            this.mob.getNavigation().moveTo(target, this.speedModifier);
        } else {
            this.mob.getNavigation().stop();
        }

        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Strafing
        if (distSqr <= attackRadiusSqr * 0.3) {
            this.strafingBackwards = true;
        } else if (distSqr > attackRadiusSqr * 0.7) {
            this.strafingBackwards = false;
        }

        if (canSee && this.seeTime >= 10) {
            ++this.strafingTime;
        } else {
            this.strafingTime = -1;
        }

        if (this.strafingTime >= 20) {
            if (this.mob.getRandom().nextFloat() < 0.3F) {
                this.strafingClockwise = !this.strafingClockwise;
            }
            if (this.mob.getRandom().nextFloat() < 0.3F) {
                this.strafingBackwards = !this.strafingBackwards;
            }
            this.strafingTime = 0;
        }

        if (this.strafingTime > -1) {
            if (distSqr > attackRadiusSqr * 0.6) this.strafingBackwards = false;
            else if (distSqr < attackRadiusSqr * 0.3) this.strafingBackwards = true;

            this.mob.getMoveControl().strafe(this.strafingBackwards ? -0.5F : 0.5F,
                    this.strafingClockwise ? 0.5F : -0.5F);
            this.mob.lookAt(target, 30.0F, 30.0F);
        } else {
            this.mob.getMoveControl().strafe(0, 0);
        }

        // Attack
        if (canSee && this.seeTime >= 5) {
            ++this.attackTime;
            if (this.attackTime >= this.attackIntervalMin) {
                performGunAttack(target);
                this.attackTime = 0;
            }
        } else {
            this.attackTime = 0;
        }
    }

    private void performGunAttack(LivingEntity target) {
        if (useTacz) {
            boolean fired = TaczIntegration.shootGun(this.mob);
            if (!fired) {
                // TACZ shot failed (no ammo, etc.) — try fallback
                performFallbackAttack(target);
            }
        } else {
            performFallbackAttack(target);
        }
    }

    private void performFallbackAttack(LivingEntity target) {
        Vec3 mobEye = this.mob.getEyePosition();
        Vec3 targetEye = target.getEyePosition();
        double distSqr = this.mob.distanceToSqr(target);
        double dist = Math.sqrt(distSqr);
        double accuracy = Math.max(0.05, 0.15 - dist * 0.002);
        boolean headshot = this.mob.getRandom().nextFloat() < 0.15;

        Vec3 aimPoint = headshot ? targetEye : target.getEyePosition().subtract(0, 0.5, 0);

        double offsetX = (this.mob.getRandom().nextDouble() - 0.5) * accuracy * dist;
        double offsetY = (this.mob.getRandom().nextDouble() - 0.5) * accuracy * dist * 0.3;
        double offsetZ = (this.mob.getRandom().nextDouble() - 0.5) * accuracy * dist;

        Vec3 shootTarget = aimPoint.add(offsetX, offsetY, offsetZ);
        Vec3 direction = shootTarget.subtract(mobEye).normalize();

        net.minecraft.world.entity.projectile.Arrow arrow =
                new net.minecraft.world.entity.projectile.Arrow(this.mob.level(), this.mob);

        double damage = 5.0D + this.mob.getRandom().nextDouble() * 3.0D;
        if (headshot) damage *= HEADSHOT_BONUS;

        arrow.setBaseDamage(damage);
        arrow.setPierceLevel((byte) 0);
        arrow.shoot(direction.x, direction.y, direction.z, 3.0F, 3.0F);
        this.mob.level().addFreshEntity(arrow);
    }
}
