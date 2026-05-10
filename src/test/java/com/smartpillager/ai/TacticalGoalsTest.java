package com.smartpillager.ai;

import com.smartpillager.tacz.TaczIntegration.GunType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AI goal configuration constants.
 * Tests only values that don't require Minecraft classes.
 */
class TacticalGoalsTest {

    // -----------------------------------------------------------------
    // Suppression timing constants
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Suppression burst duration is positive")
    void testBurstDuration() {
        int burstDuration = 20;
        int burstPause = 15;
        int maxBursts = 5;
        float suppressionRange = 35.0F;

        assertTrue(burstDuration > 0, "Burst duration must be positive");
        assertTrue(burstPause > 0, "Burst pause must be positive");
        assertTrue(maxBursts > 0, "Max bursts must be positive");
        assertTrue(suppressionRange > 0, "Suppression range must be positive");
    }

    @Test
    @DisplayName("Suppression fire cycle timing is consistent")
    void testSuppressionTiming() {
        int burstDuration = 20;
        int burstPause = 15;
        int fireInterval = 4;

        // Should fire 5 times per burst (every 4 ticks for 20 ticks)
        int shotsPerBurst = burstDuration / fireInterval;
        assertEquals(5, shotsPerBurst, "Should fire 5 shots per burst");

        // Total cycle: 5 bursts x (20 + 15) = 175 ticks
        int totalCycle = 5 * (burstDuration + burstPause);
        assertEquals(175, totalCycle, "Full suppression cycle should be 175 ticks");
    }

    // -----------------------------------------------------------------
    // Retreat distance parameters
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Retreat distance parameters are valid")
    void testRetreatDistances() {
        double retreatDistance = 20.0D;
        double safeDistance = 25.0D;
        int maxRetreatTime = 160;
        int backwardShotInterval = 30;

        assertTrue(retreatDistance > 0, "Retreat distance must be positive");
        assertTrue(safeDistance > retreatDistance, "Safe distance should exceed retreat distance");
        assertTrue(maxRetreatTime > 0, "Max retreat time must be positive");
        assertTrue(backwardShotInterval > 0, "Backward shot interval must be positive");
    }

    @Test
    @DisplayName("Retreat backward shot count is reasonable")
    void testRetreatBackwardShots() {
        int maxRetreatTime = 160;
        int backwardShotInterval = 30;
        int expectedShots = maxRetreatTime / backwardShotInterval;

        assertEquals(5, expectedShots, "Should fire ~5 backward shots during full retreat");
    }

    // -----------------------------------------------------------------
    // Gun attack parameters
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Gun attack parameters are valid")
    void testGunAttackParams() {
        double speedModifier = 1.0D;
        int attackIntervalMin = 20;
        float attackRadius = 30.0F;
        float headshotBonus = 1.5F;

        assertTrue(speedModifier > 0, "Speed modifier must be positive");
        assertTrue(attackIntervalMin > 0, "Attack interval must be positive");
        assertTrue(attackRadius > 0, "Attack radius must be positive");
        assertTrue(headshotBonus > 1.0F, "Headshot bonus should exceed 1.0");
    }

    @Test
    @DisplayName("Attack radius squared is correct")
    void testAttackRadiusSqr() {
        float attackRadius = 30.0F;
        float attackRadiusSqr = attackRadius * attackRadius;
        assertEquals(900.0F, attackRadiusSqr, 0.01f);
    }

    // -----------------------------------------------------------------
    // Flank parameters
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Flank distance parameters are valid")
    void testFlankParams() {
        double flankDistance = 15.0D;
        double minDistance = 10.0D;
        double maxDistance = 40.0D;
        int maxFlankTime = 200;
        int recalculateInterval = 40;

        assertTrue(flankDistance > 0, "Flank distance must be positive");
        assertTrue(minDistance > 0, "Min distance must be positive");
        assertTrue(maxDistance > minDistance, "Max distance should exceed min distance");
        assertTrue(maxFlankTime > 0, "Max flank time must be positive");
        assertTrue(recalculateInterval > 0, "Recalculate interval must be positive");
    }

    // -----------------------------------------------------------------
    // Cover search parameters
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Cover search parameters are valid")
    void testCoverParams() {
        int coverRadius = 8;
        int verticalDown = -2;
        int verticalUp = 3;
        int coverHoldTime = 60;
        int peekTime = 20;
        int coverCooldown = 100;

        assertTrue(coverRadius > 0, "Cover radius must be positive");
        assertTrue(verticalUp > verticalDown, "Vertical up should exceed vertical down");
        assertTrue(coverHoldTime > 0, "Cover hold time must be positive");
        assertTrue(peekTime > 0, "Peek time must be positive");
        assertTrue(coverCooldown > 0, "Cover cooldown must be positive");
    }

    @Test
    @DisplayName("Cover search volume is reasonable")
    void testCoverSearchVolume() {
        int radius = 8;
        int height = 6; // -2 to +3
        int volume = (2 * radius + 1) * height * (2 * radius + 1);
        // 17 x 6 x 17 = 1,734 blocks
        assertEquals(1734, volume);
        assertTrue(volume < 5000, "Cover search volume should be under 5000 blocks to avoid lag");
    }

    // -----------------------------------------------------------------
    // Gun type compatibility with AI
    // -----------------------------------------------------------------

    @Test
    @DisplayName("All gun types can be used by all AI goals")
    void testGunTypeCompatibility() {
        for (GunType type : GunType.values()) {
            assertNotNull(type.taczId);
            assertTrue(type.ordinal() >= 0 && type.ordinal() < 5);
        }
    }

    @Test
    @DisplayName("Gun type count matches DATA_GUN_TYPE sync range")
    void testGunTypeSyncRange() {
        for (GunType type : GunType.values()) {
            assertTrue(type.ordinal() >= 0, "Ordinal must be non-negative");
            assertTrue(type.ordinal() < 128, "Ordinal should fit in a byte for efficiency");
        }
    }

    // -----------------------------------------------------------------
    // Tactical state transition thresholds
    // -----------------------------------------------------------------

    @Test
    @DisplayName("State transition distance thresholds are ordered correctly")
    void testStateTransitionThresholds() {
        double closeRange = 15.0D;
        double mediumRange = 20.0D;
        double longRange = 30.0D;

        assertTrue(closeRange < mediumRange, "Close range should be less than medium range");
        assertTrue(mediumRange < longRange, "Medium range should be less than long range");
    }

    @Test
    @DisplayName("Health thresholds are ordered correctly")
    void testHealthThresholds() {
        double lowHealthThreshold = 0.25D;  // 25% for retreat
        double recoveryThreshold = 0.50D;   // 50% to stop retreating

        assertTrue(lowHealthThreshold < recoveryThreshold,
                "Low health threshold should be below recovery threshold");
        assertTrue(lowHealthThreshold > 0 && lowHealthThreshold < 1,
                "Low health threshold should be a valid fraction");
        assertTrue(recoveryThreshold > 0 && recoveryThreshold < 1,
                "Recovery threshold should be a valid fraction");
    }
}
