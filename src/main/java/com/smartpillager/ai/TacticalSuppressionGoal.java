package com.smartpillager.ai;

import com.smartpillager.tacz.TaczIntegration;
import com.smartpillager.entity.SmartPillagerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Suppression fire — burst fire with spread to pin the player down.
 * Uses TACZ guns when available for authentic burst-fire feel.
 */
public class TacticalSuppressionGoal extends Goal {

    private final SmartPillagerEntity mob;
    private final double speedModifier;
    private int burstTimer = 0;
    private int burstCount = 0;
    private boolean isBursting = false;
    private int strafeDir = 1;
    private final boolean useTacz;

    private static final int BURST_DURATION = 20;   // 1 second
    private static final int BURST_PAUSE = 15;      // 0.75 seconds
    private static final int MAX_BURSTS = 5;
    private static final float SUPPRESSION_RANGE = 35.0F;

    public TacticalSuppressionGoal(SmartPillagerEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.useTacz = TaczIntegration.isTaczLoaded();
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive()
                && this.mob.distanceTo(target) < SUPPRESSION_RANGE
                && this.mob.getTacticState() == SmartPillagerEntity.TACTIC_SUPPRESSING;
    }

    @Override
    public boolean canContinueToUse() {
        return burstCount < MAX_BURSTS && this.mob.getTarget() != null && this.mob.getTarget().isAlive();
    }

    @Override
    public void start() {
        this.burstTimer = BURST_DURATION;
        this.burstCount = 0;
        this.isBursting = true;
        this.strafeDir = this.mob.getRandom().nextBoolean() ? 1 : -1;
        this.mob.setSuppressing(true);
    }

    @Override
    public void stop() {
        this.mob.setSuppressing(false);
        this.isBursting = false;
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.isBursting) {
            --this.burstTimer;

            // Fire during burst — every 4 ticks
            if (this.burstTimer % 4 == 0) {
                performShot(target);
            }

            if (this.burstTimer <= 0) {
                this.isBursting = false;
                this.burstTimer = BURST_PAUSE;
                this.burstCount++;
            }
        } else {
            --this.burstTimer;

            // Strafe during pause
            this.mob.getMoveControl().strafe(0, this.strafeDir * 0.3F);

            if (this.burstTimer <= 0) {
                this.isBursting = true;
                this.burstTimer = BURST_DURATION;
                this.strafeDir = this.mob.getRandom().nextBoolean() ? 1 : -1;
            }
        }
    }

    private void performShot(LivingEntity target) {
        if (useTacz) {
            boolean fired = TaczIntegration.shootGun(this.mob);
            if (!fired) {
                performFallbackShot(target);
            }
        } else {
            performFallbackShot(target);
        }
    }

    private void performFallbackShot(LivingEntity target) {
        Vec3 mobEye = this.mob.getEyePosition();
        Vec3 targetPos = target.getPosition(1.0F);
        double dist = this.mob.distanceTo(target);

        // Wider spread for suppression
        double inaccuracy = 0.15 + dist * 0.01;
        Vec3 aim = targetPos.add(
                (this.mob.getRandom().nextDouble() - 0.5) * inaccuracy * dist,
                (this.mob.getRandom().nextDouble() - 0.5) * inaccuracy * dist * 0.3,
                (this.mob.getRandom().nextDouble() - 0.5) * inaccuracy * dist
        );

        Vec3 dir = aim.subtract(mobEye).normalize();

        net.minecraft.world.entity.projectile.Arrow arrow =
                new net.minecraft.world.entity.projectile.Arrow(this.mob.level(), this.mob);
        arrow.setBaseDamage(4.0D + this.mob.getRandom().nextDouble() * 2.0D);
        arrow.setPierceLevel((byte) 0);
        arrow.shoot(dir.x, dir.y, dir.z, 3.0F, 3.0F);
        this.mob.level().addFreshEntity(arrow);
    }
}
