package com.smartpillager.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.*;

public class ModConfig {
    public static final ForgeConfigSpec SPEC;

    public static final BooleanValue ENABLE_TACTICAL_AI;
    public static final BooleanValue ENABLE_COVER_SYSTEM;
    public static final BooleanValue ENABLE_SUPPRESSION;
    public static final BooleanValue ENABLE_FLANKING;
    public static final IntValue SPAWN_WEIGHT;
    public static final IntValue SPAWN_MIN_GROUP;
    public static final IntValue SPAWN_MAX_GROUP;
    public static final DoubleValue BASE_HEALTH;
    public static final DoubleValue BASE_DAMAGE;
    public static final DoubleValue SUPPRESSION_RANGE;
    public static final DoubleValue COVER_SEARCH_RANGE;
    public static final BooleanValue DIVISION2_MODE; // Extra hard mode

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Smart Pillager Tactical Configuration").push("general");

        ENABLE_TACTICAL_AI = builder
                .comment("Enable tactical AI for pillagers")
                .define("enableTacticalAI", true);

        ENABLE_COVER_SYSTEM = builder
                .comment("Enable cover-seeking behavior")
                .define("enableCoverSystem", true);

        ENABLE_SUPPRESSION = builder
                .comment("Enable suppression fire mechanics")
                .define("enableSuppression", true);

        ENABLE_FLANKING = builder
                .comment("Enable flanking maneuvers")
                .define("enableFlanking", true);

        DIVISION2_MODE = builder
                .comment("Enable Division 2 Hard Mode - enemies are much more aggressive and coordinated")
                .define("division2Mode", false);

        builder.pop();

        builder.comment("Spawn Settings").push("spawn");

        SPAWN_WEIGHT = builder
                .comment("Spawn weight for smart pillagers")
                .defineInRange("spawnWeight", 50, 1, 100);

        SPAWN_MIN_GROUP = builder
                .comment("Minimum group size")
                .defineInRange("minGroupSize", 2, 1, 10);

        SPAWN_MAX_GROUP = builder
                .comment("Maximum group size")
                .defineInRange("maxGroupSize", 5, 1, 15);

        builder.pop();

        builder.comment("Combat Stats").push("combat");

        BASE_HEALTH = builder
                .comment("Base health of smart pillagers")
                .defineInRange("baseHealth", 40.0, 10.0, 100.0);

        BASE_DAMAGE = builder
                .comment("Base gun damage")
                .defineInRange("baseDamage", 5.0, 1.0, 50.0);

        SUPPRESSION_RANGE = builder
                .comment("Maximum suppression fire range")
                .defineInRange("suppressionRange", 35.0, 10.0, 64.0);

        COVER_SEARCH_RANGE = builder
                .comment("How far pillagers will search for cover")
                .defineInRange("coverSearchRange", 8.0, 3.0, 16.0);

        builder.pop();

        SPEC = builder.build();
    }
}
