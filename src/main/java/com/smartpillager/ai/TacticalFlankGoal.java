package com.smartpillager.ai;

import com.smartpillager.entity.SmartPillagerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Division 2 inspired flanking behavior.
 * Pillagers try to move to the side/rear of the enemy
 * to break their cover and create crossfire with allies.
 */
public class TacticalFlankGoal extends Goal {

    private final SmartPillagerEntity mob;
    private final double speedModifier;
    private Vec3 flankTarget = null;
    private int flankTimer = 0;

    private static final int MAX_FLANK_TIME = 200; // 10 seconds max
    private static final double FLANK_DISTANCE = 15.0D;
    private static final double FLANK_ANGLE = 90.0D; // Try to get 90 degrees to target's facing

    public TacticalFlankGoal(SmartPillagerEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.getTacticState() != SmartPillagerEntity.TACTIC_FLANKING) {
            return false;
        }

        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return false;
        }

        double dist = this.mob.distanceTo(target);
        return dist > 10.0D && dist < 40.0D;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.flankTimer > MAX_FLANK_TIME) {
            return false;
        }

        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }

        // Stop flanking if we reached a good position
        if (this.flankTarget != null && this.mob.position().closerThan(this.flankTarget, 3.0D)) {
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        this.flankTimer = 0;
        LivingEntity target = this.mob.getTarget();
        if (target != null) {
            this.flankTarget = calculateFlankPosition(target);
        }
    }

    @Override
    public void tick() {
        this.flankTimer++;
        LivingEntity target = this.mob.getTarget();

        if (target == null) {
            return;
        }

        // Always look at target
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.flankTarget != null) {
            this.mob.getNavigation().moveTo(
                    this.flankTarget.x, this.flankTarget.y, this.flankTarget.z,
                    this.speedModifier);
        }

        // Recalculate flank position periodically
        if (this.flankTimer % 40 == 0) {
            this.flankTarget = calculateFlankPosition(target);
        }
    }

    @Override
    public void stop() {
        this.flankTarget = null;
        this.flankTimer = 0;
    }

    /**
     * Calculate a flanking position relative to the target's facing direction.
     * Tries to position at 90 degrees to the target's look direction.
     */
    private Vec3 calculateFlankPosition(LivingEntity target) {
        Vec3 targetLook = target.getLookAngle();
        Vec3 targetPos = target.position();

        // Rotate look direction by ~90 degrees to get flank direction
        boolean goRight = this.mob.getRandom().nextBoolean();
        double angle = Math.toRadians(FLANK_ANGLE * (goRight ? 1 : -1));

        double flankX = targetLook.x * Math.cos(angle) - targetLook.z * Math.sin(angle);
        double flankZ = targetLook.x * Math.sin(angle) + targetLook.z * Math.cos(angle);

        Vec3 flankPos = targetPos.add(flankX * FLANK_DISTANCE, 0, flankZ * FLANK_DISTANCE);

        // Try to find a valid position near the calculated flank point
        Vec3 validPos = LandRandomPos.getPosTowards(this.mob, 8, 4, flankPos);
        if (validPos != null) {
            return validPos;
        }

        return flankPos;
    }
}
