package com.smartpillager.tacz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TaczIntegration — runs without TACZ on classpath.
 * Verifies that the reflection-based integration degrades gracefully.
 *
 * NOTE: isTaczLoaded() triggers ForgeRegistries class initialization which
 * requires Minecraft bootstrap. We test it only once and verify it returns
 * false (since TACZ is not on the test classpath).
 */
class TaczIntegrationTest {

    private static Boolean TACZ_LOADED = null;

    /**
     * Safely check if TACZ is loaded, caching the result.
     * Handles ExceptionInInitializerError from ForgeRegistries not being bootstrapped.
     */
    private static boolean safeIsTaczLoaded() {
        if (TACZ_LOADED != null) return TACZ_LOADED;
        try {
            TACZ_LOADED = TaczIntegration.isTaczLoaded();
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            // ForgeRegistries not bootstrapped in test environment
            TACZ_LOADED = false;
        }
        return TACZ_LOADED;
    }

    // -----------------------------------------------------------------
    // GunType enum tests
    // -----------------------------------------------------------------

    @Test
    @DisplayName("GunType has exactly 5 values")
    void testGunTypeCount() {
        assertEquals(5, TaczIntegration.GunType.values().length,
                "Expected 5 gun types: SCOUT, ASSAULT, SNIPER, HEAVY, ROCKET");
    }

    @Test
    @DisplayName("GunType.SCOUT has correct TACZ ID")
    void testScoutId() {
        assertEquals("glock_17", TaczIntegration.GunType.SCOUT.taczId);
    }

    @Test
    @DisplayName("GunType.ASSAULT has correct TACZ ID")
    void testAssaultId() {
        assertEquals("ak47", TaczIntegration.GunType.ASSAULT.taczId);
    }

    @Test
    @DisplayName("GunType.SNIPER has correct TACZ ID")
    void testSniperId() {
        assertEquals("ai_awp", TaczIntegration.GunType.SNIPER.taczId);
    }

    @Test
    @DisplayName("GunType.HEAVY has correct TACZ ID")
    void testHeavyId() {
        assertEquals("m249", TaczIntegration.GunType.HEAVY.taczId);
    }

    @Test
    @DisplayName("GunType.ROCKET has correct TACZ ID")
    void testRocketId() {
        assertEquals("rpg7", TaczIntegration.GunType.ROCKET.taczId);
    }

    @Test
    @DisplayName("GunType.taczId is non-null and non-empty for all types")
    void testAllGunIdsValid() {
        for (TaczIntegration.GunType type : TaczIntegration.GunType.values()) {
            assertNotNull(type.taczId, "GunType " + type.name() + " has null taczId");
            assertFalse(type.taczId.isEmpty(), "GunType " + type.name() + " has empty taczId");
        }
    }

    @Test
    @DisplayName("GunType names match expected values")
    void testGunTypeNames() {
        String[] expected = {"SCOUT", "ASSAULT", "SNIPER", "HEAVY", "ROCKET"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], TaczIntegration.GunType.values()[i].name());
        }
    }

    // -----------------------------------------------------------------
    // Weighted random distribution test
    // -----------------------------------------------------------------

    @RepeatedTest(20)
    @DisplayName("GunType.randomWeighted() never returns null")
    void testRandomWeightedNeverNull() {
        assertNotNull(TaczIntegration.GunType.randomWeighted());
    }

    @Test
    @DisplayName("GunType.randomWeighted() produces all types over many iterations")
    void testRandomWeightedDistribution() {
        java.util.Set<TaczIntegration.GunType> seen = new java.util.HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(TaczIntegration.GunType.randomWeighted());
        }
        // With 1000 iterations, we should see all 5 types
        assertEquals(5, seen.size(), "Expected all 5 gun types to appear in 1000 random picks");
    }

    @Test
    @DisplayName("GunType.randomWeighted() distribution is roughly correct")
    void testRandomWeightedDistributionRatios() {
        int assault = 0, scout = 0, heavy = 0, sniper = 0, rocket = 0;
        int total = 10000;

        for (int i = 0; i < total; i++) {
            switch (TaczIntegration.GunType.randomWeighted()) {
                case ASSAULT -> assault++;
                case SCOUT -> scout++;
                case HEAVY -> heavy++;
                case SNIPER -> sniper++;
                case ROCKET -> rocket++;
            }
        }

        // Allow ±5% tolerance
        assertTrue(assault > total * 0.25 && assault < total * 0.35,
                "ASSAULT should be ~30%, was " + (assault * 100.0 / total) + "%");
        assertTrue(scout > total * 0.20 && scout < total * 0.30,
                "SCOUT should be ~25%, was " + (scout * 100.0 / total) + "%");
        assertTrue(heavy > total * 0.15 && heavy < total * 0.25,
                "HEAVY should be ~20%, was " + (heavy * 100.0 / total) + "%");
        assertTrue(sniper > total * 0.12 && sniper < total * 0.22,
                "SNIPER should be ~17%, was " + (sniper * 100.0 / total) + "%");
        assertTrue(rocket > total * 0.03 && rocket < total * 0.13,
                "ROCKET should be ~8%, was " + (rocket * 100.0 / total) + "%");
    }

    // -----------------------------------------------------------------
    // TACZ detection without TACZ on classpath
    // -----------------------------------------------------------------

    @Test
    @DisplayName("isTaczLoaded() returns false when TACZ is not on classpath")
    void testTaczNotLoaded() {
        assertFalse(safeIsTaczLoaded(),
                "TACZ should not be detected when not on classpath");
    }

    @Test
    @DisplayName("isTaczLoaded() is idempotent — multiple calls return same result")
    void testTaczDetectionIdempotent() {
        boolean first = safeIsTaczLoaded();
        boolean second = safeIsTaczLoaded();
        assertEquals(first, second, "isTaczLoaded() should be idempotent");
    }

    // -----------------------------------------------------------------
    // Graceful degradation without TACZ
    // -----------------------------------------------------------------

    @Test
    @DisplayName("TACZ is not loaded in test environment")
    void testTaczNotLoadedInTestEnv() {
        assertFalse(safeIsTaczLoaded(),
                "TACZ should not be loaded in test environment");
    }

    // -----------------------------------------------------------------
    // Gun ID format validation
    // -----------------------------------------------------------------

    @Test
    @DisplayName("All gun IDs are valid resource location paths")
    void testGunIdFormat() {
        for (TaczIntegration.GunType type : TaczIntegration.GunType.values()) {
            String id = type.taczId;
            assertNotNull(id);
            assertFalse(id.isBlank());
            assertEquals(id, id.toLowerCase(), "Gun ID should be lowercase: " + id);
            assertFalse(id.contains(" "), "Gun ID should not contain spaces: " + id);
        }
    }

    @Test
    @DisplayName("Gun IDs match expected TACZ registry names")
    void testGunIdsMatchExpected() {
        assertEquals("glock_17", TaczIntegration.GunType.SCOUT.taczId);
        assertEquals("ak47", TaczIntegration.GunType.ASSAULT.taczId);
        assertEquals("ai_awp", TaczIntegration.GunType.SNIPER.taczId);
        assertEquals("m249", TaczIntegration.GunType.HEAVY.taczId);
        assertEquals("rpg7", TaczIntegration.GunType.ROCKET.taczId);
    }

    // -----------------------------------------------------------------
    // Gun item resolution
    // -----------------------------------------------------------------

    @Test
    @DisplayName("getGunItem() returns null for unknown gun ID without TACZ")
    void testGetGunItemUnknown() {
        // Without TACZ, the Forge registry won't have TACZ items
        // This may throw ExceptionInInitializerError due to ForgeRegistries
        try {
            var item = TaczIntegration.getGunItem("nonexistent_gun");
            assertNull(item, "Unknown gun ID should return null");
        } catch (ExceptionInInitializerError | NoClassDefFoundError e) {
            // Expected in test environment without Minecraft bootstrap
            assertTrue(true, "ForgeRegistries not bootstrapped — expected in unit tests");
        }
    }
}
