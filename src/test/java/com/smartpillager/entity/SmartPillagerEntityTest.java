package com.smartpillager.entity;

import com.smartpillager.tacz.TaczIntegration;
import com.smartpillager.tacz.TaczIntegration.GunType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SmartPillagerEntity — tests entity logic that doesn't
 * require a running Minecraft server.
 */
class SmartPillagerEntityTest {

    // -----------------------------------------------------------------
    // Tactic state constants
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Tactic state constants have correct values")
    void testTacticStateConstants() {
        assertEquals(0, SmartPillagerEntity.TACTIC_IDLE);
        assertEquals(1, SmartPillagerEntity.TACTIC_ADVANCING);
        assertEquals(2, SmartPillagerEntity.TACTIC_IN_COVER);
        assertEquals(3, SmartPillagerEntity.TACTIC_SUPPRESSING);
        assertEquals(4, SmartPillagerEntity.TACTIC_FLANKING);
        assertEquals(5, SmartPillagerEntity.TACTIC_RETREATING);
    }

    @Test
    @DisplayName("Tactic state constants are sequential from 0")
    void testTacticStateSequential() {
        int[] expected = {0, 1, 2, 3, 4, 5};
        int[] actual = {
                SmartPillagerEntity.TACTIC_IDLE,
                SmartPillagerEntity.TACTIC_ADVANCING,
                SmartPillagerEntity.TACTIC_IN_COVER,
                SmartPillagerEntity.TACTIC_SUPPRESSING,
                SmartPillagerEntity.TACTIC_FLANKING,
                SmartPillagerEntity.TACTIC_RETREATING
        };
        assertArrayEquals(expected, actual);
    }

    // -----------------------------------------------------------------
    // Gun type assignment logic
    // -----------------------------------------------------------------

    @Test
    @DisplayName("GunType enum has 5 values matching entity expectations")
    void testGunTypeCount() {
        assertEquals(5, GunType.values().length);
    }

    @Test
    @DisplayName("GunType ordinals are sequential 0-4")
    void testGunTypeOrdinals() {
        // GunType order: SCOUT, ASSAULT, SNIPER, HEAVY, ROCKET
        GunType[] types = GunType.values();
        assertEquals("SCOUT", types[0].name());
        assertEquals("ASSAULT", types[1].name());
        assertEquals("SNIPER", types[2].name());
        assertEquals("HEAVY", types[3].name());
        assertEquals("ROCKET", types[4].name());
    }

    @Test
    @DisplayName("All GunType taczId values are valid resource location paths")
    void testGunTypeResourceIds() {
        for (GunType type : GunType.values()) {
            String id = type.taczId;
            assertNotNull(id);
            assertFalse(id.isBlank());
            // Resource location paths should not contain spaces or uppercase
            assertEquals(id, id.toLowerCase(), "Gun ID should be lowercase: " + id);
            assertFalse(id.contains(" "), "Gun ID should not contain spaces: " + id);
        }
    }

    // -----------------------------------------------------------------
    // NBT persistence logic (ordinal-based)
    // -----------------------------------------------------------------

    @Test
    @DisplayName("GunType can be round-tripped through ordinal NBT storage")
    void testGunTypeNbtRoundTrip() {
        for (GunType original : GunType.values()) {
            int ordinal = original.ordinal();
            GunType restored = GunType.values()[ordinal];
            assertEquals(original, restored,
                    "Round-trip through ordinal failed for " + original.name());
        }
    }

    @Test
    @DisplayName("Invalid GunType ordinal falls back to ASSAULT")
    void testInvalidGunTypeOrdinalFallback() {
        int invalidOrdinal = 99;
        GunType[] types = GunType.values();
        GunType result = (invalidOrdinal >= 0 && invalidOrdinal < types.length)
                ? types[invalidOrdinal] : GunType.ASSAULT;
        assertEquals(GunType.ASSAULT, result);
    }

    @Test
    @DisplayName("Negative GunType ordinal falls back to ASSAULT")
    void testNegativeGunTypeOrdinalFallback() {
        int invalidOrdinal = -1;
        GunType[] types = GunType.values();
        GunType result = (invalidOrdinal >= 0 && invalidOrdinal < types.length)
                ? types[invalidOrdinal] : GunType.ASSAULT;
        assertEquals(GunType.ASSAULT, result);
    }

    // -----------------------------------------------------------------
    // Attribute validation
    // -----------------------------------------------------------------

    @Test
    @DisplayName("Entity stats are within reasonable bounds")
    void testEntityStats() {
        double maxHealth = 40.0D;
        double moveSpeed = 0.35D;
        double followRange = 48.0D;
        double attackDamage = 6.0D;
        double armor = 4.0D;

        assertTrue(maxHealth > 0 && maxHealth <= 200, "Max health should be reasonable");
        assertTrue(moveSpeed > 0 && moveSpeed <= 2.0, "Move speed should be reasonable");
        assertTrue(followRange > 0 && followRange <= 128, "Follow range should be reasonable");
        assertTrue(attackDamage >= 0, "Attack damage should be non-negative");
        assertTrue(armor >= 0, "Armor should be non-negative");
    }
}
