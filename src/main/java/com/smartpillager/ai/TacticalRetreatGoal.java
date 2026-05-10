package com.smartpillager.ai;

import com.smartpillager.tacz.TaczIntegration;
import com.smartpillager.entity.SmartPillagerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Retreat goal — flee when low on health, firing backward to discourage pursuit.
 * Uses TACZ guns when available.
 */
public class TacticalRetreatGoal extends Goal {

    private final SmartPillagerEntity mob;
    private final double speedModifier;
    private Vec3 retreatTarget = null;
    private int retreatTimer = 0;
    private int backwardShotTimer = 0;
    private final boolean useTacz;

    private static final double RETREAT_DISTANCE = 20.0D;
    private static final double SAFE_DISTANCE = 25.0D;
    private static final int MAX_RETREAT_TIME = 160; // 8 seconds
    private static final int BACKWARD_SHOT_INTERVAL = 30; // every 1.5 seconds

    public TacticalRetreatGoal(SmartPillagerEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.useTacz = TaczIntegration.isTaczLoaded();
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.getHealth() / this.mob.getMaxHealth() < 0.25F
                && this.mob.getTacticState() == SmartPillagerEntity.TACTIC_RETREATING;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.mob.getTarget() == null || !this.mob.getTarget().isAlive()) return false;
        if (this.retreatTimer > MAX_RETREAT_TIME) return false;

        double healthPct = this.mob.getHealth() / this.mob.getMaxHealth();
        if (healthPct > 0.5F && this.mob.distanceTo(this.mob.getTarget()) > 17.5D) return false;

        return true;
    }

    @Override
    public void start() {
        this.retreatTimer = 0;
        this.backwardShotTimer = BACKWARD_SHOT_INTERVAL;
        calculateRetreatTarget();
    }

    @Override
    public void stop() {
        this.retreatTarget = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        this.retreatTimer++;

        // Recalculate retreat target periodically
        if (this.retreatTimer % 60 == 0 || this.retreatTarget == null) {
            calculateRetreatTarget();
        }

        if (this.retreatTarget != null) {
            this.mob.getNavigation().moveTo(this.retreatTarget.x, this.retreatTarget.y,
                    this.retreatTarget.z, this.speedModifier * 1.2D);
        }

        // Fire backward at pursuer
        --this.backwardShotTimer;
        if (this.backwardShotTimer <= 0) {
            performBackwardShot(target);
            this.backwardShotTimer = BACKWARD_SHOT_INTERVAL;
        }
    }

    private void calculateRetreatTarget() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        Vec3 mobPos = this.mob.position();
        Vec3 targetPos = target.position();
        Vec3 retreatDir = mobPos.subtract(targetPos).normalize();
        Vec3 desired = mobPos.add(retreatDir.scale(RETREAT_DISTANCE));

        Vec3 valid = LandRandomPos.getPosTowards(this.mob, 16, 7, desired);
        if (valid != null) {
            this.retreatTarget = valid;
        }
    }

    private void performBackwardShot(LivingEntity target) {
        if (useTacz) {
            // For backward shots, we still aim at the target but with extra spread
            boolean fired = TaczIntegration.shootGun(this.mob);
            if (!fired) {
                performFallbackBackwardShot(target);
            }
        } else {
            performFallbackBackwardShot(target);
        }
    }

    private void performFallbackBackwardShot(LivingEntity target) {
        Vec3 mobEye = this.mob.getEyePosition();
        Vec3 targetPos = target.getPosition(1.0F);
        double dist = this.mob.distanceTo(target);

        // High inaccuracy for backward shots
        double inaccuracy = 0.3;
        Vec3 aim = targetPos.add(
                (this.mob.getRandom().nextDouble() - 0.5) * inaccuracy * dist,
                (this.mob.getRandom().nextDouble() - 0.5) * inaccuracy * dist * 0.3,
                (this.mob.getRandom().nextDouble() - 0.5) * inaccuracy * dist
        );

        Vec3 dir = aim.subtract(mobEye).normalize();

        net.minecraft.world.entity.projectile.Arrow arrow =
                new net.minecraft.world.entity.projectile.Arrow(this.mob.level(), this.mob);
        arrow.setBaseDamage(3.0D);
        arrow.setPierceLevel((byte) 0);
        arrow.shoot(dir.x, dir.y, dir.z, 2.5F, 5.0F); // slower, more spread
        this.mob.level().addFreshEntity(arrow);
    }
}
