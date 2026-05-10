package com.smartpillager.entity;

import com.smartpillager.tacz.TaczIntegration;
import com.smartpillager.tacz.TaczIntegration.GunType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

/**
 * Smart Pillager with full TACZ gun integration.
 * Each pillager is assigned a GunType on spawn and equipped with the
 * corresponding TACZ weapon.  Tactical AI goals use TaczIntegration
 * to fire real TACZ shots with proper ballistics, sound, and particles.
 */
public class SmartPillagerEntity extends Pillager {

    // Synched data
    private static final EntityDataAccessor<Boolean> DATA_IN_COVER =
            SynchedEntityData.defineId(SmartPillagerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SUPPRESSING =
            SynchedEntityData.defineId(SmartPillagerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TACTIC_STATE =
            SynchedEntityData.defineId(SmartPillagerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GUN_TYPE =
            SynchedEntityData.defineId(SmartPillagerEntity.class, EntityDataSerializers.INT);

    // Tactic states
    public static final int TACTIC_IDLE = 0;
    public static final int TACTIC_ADVANCING = 1;
    public static final int TACTIC_IN_COVER = 2;
    public static final int TACTIC_SUPPRESSING = 3;
    public static final int TACTIC_FLANKING = 4;
    public static final int TACTIC_RETREATING = 5;

    // Timers
    private int tacticCooldown = 0;
    private int suppressionTimer = 0;
    private int coverSearchCooldown = 0;

    // Assigned gun type (server-side logic)
    private GunType gunType = GunType.ASSAULT;

    public SmartPillagerEntity(EntityType<? extends Pillager> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Pillager.createAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.ARMOR, 4.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_IN_COVER, false);
        this.entityData.define(DATA_SUPPRESSING, false);
        this.entityData.define(DATA_TACTIC_STATE, TACTIC_IDLE);
        this.entityData.define(DATA_GUN_TYPE, GunType.ASSAULT.ordinal());
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Remove vanilla crossbow AI
        this.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal() instanceof RangedCrossbowAttackGoal);

        // Tactical AI goals
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new com.smartpillager.ai.TacticalCoverGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new com.smartpillager.ai.TacticalSuppressionGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new com.smartpillager.ai.TacticalFlankGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new com.smartpillager.ai.TacticalRetreatGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new com.smartpillager.ai.TacticalGunAttackGoal(this, 1.0D, 20, 30.0F));
        this.goalSelector.addGoal(6, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        // Targeting
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, net.minecraft.world.entity.npc.AbstractVillager.class, false));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            updateTacticalState();
        }
    }

    private void updateTacticalState() {
        if (tacticCooldown > 0) {
            tacticCooldown--;
            return;
        }

        LivingEntity target = this.getTarget();
        if (target == null) {
            setTacticState(TACTIC_IDLE);
            setInCover(false);
            setSuppressing(false);
            return;
        }

        double distanceToTarget = this.distanceTo(target);
        float healthPercent = this.getHealth() / this.getMaxHealth();

        if (healthPercent < 0.25F) {
            setTacticState(TACTIC_RETREATING);
        } else if (isInCover() && distanceToTarget < 20.0D) {
            setTacticState(TACTIC_SUPPRESSING);
        } else if (!isInCover() && coverSearchCooldown <= 0) {
            setTacticState(TACTIC_IN_COVER);
        } else if (distanceToTarget > 30.0D) {
            setTacticState(TACTIC_ADVANCING);
        } else if (distanceToTarget > 15.0D) {
            setTacticState(TACTIC_FLANKING);
        } else {
            setTacticState(TACTIC_SUPPRESSING);
        }

        tacticCooldown = 20 + this.random.nextInt(20);
    }

    // -----------------------------------------------------------------
    // Spawn — assign a random gun type and equip it
    // -----------------------------------------------------------------
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                         MobSpawnType reason, @Nullable SpawnGroupData spawnData,
                                         @Nullable CompoundTag dataTag) {
        spawnData = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);

        // Assign weighted random gun type
        this.gunType = GunType.randomWeighted();
        this.entityData.set(DATA_GUN_TYPE, this.gunType.ordinal());

        // Equip the TACZ gun item
        TaczIntegration.equipGun(this, this.gunType);

        return spawnData;
    }

    // -----------------------------------------------------------------
    // Gun type accessors
    // -----------------------------------------------------------------
    public GunType getGunType() {
        return this.gunType;
    }

    public void setGunType(GunType type) {
        this.gunType = type;
        this.entityData.set(DATA_GUN_TYPE, type.ordinal());
    }

    public int getSyncedGunTypeOrdinal() {
        return this.entityData.get(DATA_GUN_TYPE);
    }

    // -----------------------------------------------------------------
    // Spawn rules
    // -----------------------------------------------------------------
    public static boolean checkSpawnRules(EntityType<SmartPillagerEntity> type,
                                           ServerLevelAccessor level, MobSpawnType spawnType,
                                           net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
        net.minecraft.core.BlockPos below = pos.below();
        return Mob.checkMobSpawnRules(type, level, spawnType, pos, random) &&
                level.getBlockState(below).isValidSpawn(level, below, type);
    }

    // -----------------------------------------------------------------
    // Synced data accessors
    // -----------------------------------------------------------------
    public boolean isInCover() { return this.entityData.get(DATA_IN_COVER); }
    public void setInCover(boolean v) { this.entityData.set(DATA_IN_COVER, v); }
    public boolean isSuppressing() { return this.entityData.get(DATA_SUPPRESSING); }
    public void setSuppressing(boolean v) { this.entityData.set(DATA_SUPPRESSING, v); }
    public int getTacticState() { return this.entityData.get(DATA_TACTIC_STATE); }
    public void setTacticState(int v) { this.entityData.set(DATA_TACTIC_STATE, v); }
    public int getSuppressionTimer() { return suppressionTimer; }
    public void setSuppressionTimer(int v) { this.suppressionTimer = v; }
    public int getCoverSearchCooldown() { return coverSearchCooldown; }
    public void setCoverSearchCooldown(int v) { this.coverSearchCooldown = v; }

    // -----------------------------------------------------------------
    // NBT persistence
    // -----------------------------------------------------------------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("InCover", isInCover());
        tag.putBoolean("Suppressing", isSuppressing());
        tag.putInt("TacticState", getTacticState());
        tag.putInt("GunType", this.gunType.ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setInCover(tag.getBoolean("InCover"));
        setSuppressing(tag.getBoolean("Suppressing"));
        setTacticState(tag.getInt("TacticState"));
        int gunOrdinal = tag.getInt("GunType");
        GunType[] types = GunType.values();
        this.gunType = (gunOrdinal >= 0 && gunOrdinal < types.length) ? types[gunOrdinal] : GunType.ASSAULT;
        this.entityData.set(DATA_GUN_TYPE, this.gunType.ordinal());
    }
}
