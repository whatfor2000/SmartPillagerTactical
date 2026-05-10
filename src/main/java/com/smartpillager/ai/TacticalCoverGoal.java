package com.smartpillager.ai;

import com.smartpillager.entity.SmartPillagerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Division 2 inspired cover system.
 * Pillagers seek solid blocks between themselves and the enemy to use as cover.
 * They position themselves so the cover block blocks the line of sight from the target.
 */
public class TacticalCoverGoal extends Goal {

    private final SmartPillagerEntity mob;
    private final double speedModifier;
    private BlockPos coverPos = null;
    private int coverTimer = 0;
    private int noPathTimer = 0;

    // Radius to search for cover blocks
    private static final int COVER_SEARCH_RADIUS = 8;
    // How long to stay in cover (ticks) before peeking
    private static final int COVER_HOLD_TIME = 60;
    // Minimum distance from target to seek cover
    private static final double MIN_COVER_DISTANCE = 8.0D;

    public TacticalCoverGoal(SmartPillagerEntity mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.isInCover()) {
            return false; // Already in cover
        }

        if (this.mob.getCoverSearchCooldown() > 0) {
            return false;
        }

        var target = this.mob.getTarget();
        if (target == null) {
            return false;
        }

        double dist = this.mob.distanceTo(target);
        if (dist < MIN_COVER_DISTANCE) {
            return false; // Too close for cover, just fight
        }

        // Find a cover position
        this.coverPos = findCoverPosition(target);
        return this.coverPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.coverPos == null) {
            return false;
        }

        if (this.coverTimer > COVER_HOLD_TIME * 3) {
            return false; // Been too long, re-evaluate
        }

        if (this.noPathTimer > 100) {
            return false; // Can't reach cover
        }

        return this.mob.getTarget() != null;
    }

    @Override
    public void start() {
        this.coverTimer = 0;
        this.noPathTimer = 0;
        moveToCover();
    }

    @Override
    public void tick() {
        this.coverTimer++;

        var target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        // Look at target while in cover
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (this.mob.blockPosition().closerThan(this.coverPos, 2.0D)) {
            // Reached cover
            this.mob.setInCover(true);
            this.mob.setTacticState(SmartPillagerEntity.TACTIC_IN_COVER);

            // Pop out to shoot periodically
            if (this.coverTimer > COVER_HOLD_TIME && this.coverTimer % 40 < 20) {
                // Peeking phase - line of sight to target is checked by attack goal
                this.mob.setSuppressing(true);
            } else {
                this.mob.setSuppressing(false);
            }
        } else {
            // Still moving to cover
            this.noPathTimer++;
            if (this.mob.getNavigation().isDone()) {
                moveToCover();
            }
        }
    }

    @Override
    public void stop() {
        this.mob.setInCover(false);
        this.mob.setSuppressing(false);
        this.coverPos = null;
        this.coverTimer = 0;
        this.noPathTimer = 0;
        this.mob.setCoverSearchCooldown(100); // 5 second cooldown before seeking cover again
    }

    private void moveToCover() {
        if (this.coverPos != null) {
            this.mob.getNavigation().moveTo(
                    this.coverPos.getX() + 0.5,
                    this.coverPos.getY(),
                    this.coverPos.getZ() + 0.5,
                    this.speedModifier
            );
        }
    }

    /**
     * Find a suitable cover position between us and the target.
     * A good cover block is a solid block that blocks line of sight from target to us.
     */
    private BlockPos findCoverPosition(net.minecraft.world.entity.LivingEntity target) {
        BlockPos mobPos = this.mob.blockPosition();
        Vec3 targetEyePos = target.getEyePosition();

        BlockPos bestCover = null;
        double bestScore = Double.MAX_VALUE;

        for (int x = -COVER_SEARCH_RADIUS; x <= COVER_SEARCH_RADIUS; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -COVER_SEARCH_RADIUS; z <= COVER_SEARCH_RADIUS; z++) {
                    BlockPos checkPos = mobPos.offset(x, y, z);

                    if (!isGoodCoverBlock(checkPos)) {
                        continue;
                    }

                    // Check if this block blocks line of sight from target
                    Vec3 coverCenter = Vec3.atCenterOf(checkPos);
                    BlockHitResult hit = this.mob.level().clip(
                            new ClipContext(targetEyePos, coverCenter,
                                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this.mob));

                    if (hit.getBlockPos().equals(checkPos)) {
                        // This block blocks LOS from target - good cover!
                        double distToMob = mobPos.distSqr(checkPos);
                        double distToTarget = target.blockPosition().distSqr(checkPos);

                        // Prefer cover that is:
                        // 1. Close to us (so we can reach it quickly)
                        // 2. Between us and the target
                        double score = distToMob - distToTarget * 0.3;

                        if (score < bestScore && distToMob > 2.0 && distToTarget > 4.0) {
                            bestScore = score;
                            bestCover = checkPos;
                        }
                    }
                }
            }
        }

        return bestCover;
    }

    /**
     * Check if a block position provides good cover (solid, with air above).
     */
    private boolean isGoodCoverBlock(BlockPos pos) {
        BlockState state = this.mob.level().getBlockState(pos);
        if (!state.isSolidRender(this.mob.level(), pos)) {
            return false;
        }

        // Need air above so the pillager can stand behind it
        BlockPos above = pos.above();
        if (!this.mob.level().getBlockState(above).isAir()) {
            return false;
        }

        // Check if the pillager can stand adjacent to this block
        // (at least one side must have air to stand in)
        boolean hasStandingSpot = false;
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            BlockPos sideAbove = side.above();
            if (this.mob.level().getBlockState(side).isAir() &&
                    this.mob.level().getBlockState(sideAbove).isAir()) {
                hasStandingSpot = true;
                break;
            }
        }

        return hasStandingSpot;
    }
}
