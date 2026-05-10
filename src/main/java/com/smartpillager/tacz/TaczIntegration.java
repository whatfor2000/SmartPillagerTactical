package com.smartpillager.tacz;

import com.smartpillager.SmartPillagerMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;

/**
 * TACZ integration via reflection — zero compile-time dependency.
 *
 * All TACZ API calls are made through reflection so the mod compiles and
 * runs without TACZ on the classpath.  When TACZ is present at runtime,
 * pillagers get real gun items and fire actual TACZ shots with proper
 * ballistics, sound, and particles.
 *
 * Gun classes and their TACZ gun IDs:
 *   SCOUT    → tacz:glock_17          (pistol, fast, close range)
 *   ASSAULT  → tacz:ak47              (rifle, medium range, auto)
 *   SNIPER   → tacz:ai_awp            (sniper, long range, bolt-action)
 *   HEAVY    → tacz:m249              (LMG, suppression, high ammo)
 *   ROCKET   → tacz:rpg7              (RPG, rare, devastating)
 */
public final class TaczIntegration {

    private TaczIntegration() {}

    // -----------------------------------------------------------------
    // Gun class enum — each pillager gets one on spawn
    // -----------------------------------------------------------------
    public enum GunType {
        SCOUT("glock_17"),
        ASSAULT("ak47"),
        SNIPER("ai_awp"),
        HEAVY("m249"),
        ROCKET("rpg7");

        public final String taczId;

        GunType(String taczId) {
            this.taczId = taczId;
        }

        private static final GunType[] VALUES = values();
        private static final Random RNG = new Random();

        /** Pick a random gun type with weighted distribution. */
        public static GunType randomWeighted() {
            int roll = RNG.nextInt(100);
            if (roll < 30) return ASSAULT;       // 30%
            if (roll < 55) return SCOUT;         // 25%
            if (roll < 75) return HEAVY;         // 20%
            if (roll < 92) return SNIPER;        // 17%
            return ROCKET;                        // 8%
        }
    }

    // -----------------------------------------------------------------
    // Reflection caches
    // -----------------------------------------------------------------
    private static boolean taczChecked = false;
    private static boolean taczDetected = false;

    // IGun interface methods
    private static Method mGetIGunOrNull;          // IGun.getIGunOrNull(ItemStack)
    private static Method mUseInventoryAmmo;       // IGun.useInventoryAmmo(ItemStack)
    private static Method mHasInventoryAmmo;       // IGun.hasInventoryAmmo(LivingEntity, ItemStack, boolean)
    private static Method mGetDummyAmmo;           // IGun.getDummyAmmoAmount(ItemStack)
    private static Method mSetDummyAmmo;           // IGun.setDummyAmmoAmount(ItemStack, int)
    private static Method mGetMaxDummyAmmo;        // IGun.getMaxDummyAmmoAmount(ItemStack)
    private static Method mGetCurrentAmmoCount;    // IGun.getCurrentAmmoCount(ItemStack)

    // IGunOperator interface methods
    private static Method mFromLivingEntity;       // IGunOperator.fromLivingEntity(LivingEntity)
    private static Method mInitialData;            // IGunOperator.initialData()
    private static Method mShoot;                  // IGunOperator.shoot(DoubleSupplier, DoubleSupplier)
    private static Method mReload;                 // IGunOperator.reload()

    // TimelessAPI
    private static Method mGetCommonGunIndex;      // TimelessAPI.getCommonGunIndex(ResourceLocation)

    // Gun item cache
    private static final Map<String, Item> GUN_ITEM_CACHE = new HashMap<>();

    // -----------------------------------------------------------------
    // Detection & reflection init
    // -----------------------------------------------------------------
    public static boolean isTaczLoaded() {
        if (taczChecked) return taczDetected;
        taczChecked = true;

        try {
            Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
            Class<?> iGunOperator = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            Class<?> timelessAPI = Class.forName("com.tacz.guns.api.TimelessAPI");

            // IGun static methods
            mGetIGunOrNull = iGun.getMethod("getIGunOrNull", ItemStack.class);
            mUseInventoryAmmo = iGun.getMethod("useInventoryAmmo", ItemStack.class);
            mHasInventoryAmmo = iGun.getMethod("hasInventoryAmmo", LivingEntity.class, ItemStack.class, boolean.class);
            mGetDummyAmmo = iGun.getMethod("getDummyAmmoAmount", ItemStack.class);
            mSetDummyAmmo = iGun.getMethod("setDummyAmmoAmount", ItemStack.class, int.class);
            mGetMaxDummyAmmo = iGun.getMethod("getMaxDummyAmmoAmount", ItemStack.class);
            mGetCurrentAmmoCount = iGun.getMethod("getCurrentAmmoCount", ItemStack.class);

            // IGunOperator methods
            mFromLivingEntity = iGunOperator.getMethod("fromLivingEntity", LivingEntity.class);
            mInitialData = iGunOperator.getMethod("initialData");
            mShoot = iGunOperator.getMethod("shoot", java.util.function.DoubleSupplier.class, java.util.function.DoubleSupplier.class);
            mReload = iGunOperator.getMethod("reload");

            // TimelessAPI
            mGetCommonGunIndex = timelessAPI.getMethod("getCommonGunIndex", ResourceLocation.class);

            taczDetected = true;
            try {
                SmartPillagerMod.LOGGER.info("[SmartPillager] TACZ detected — enabling real gun integration");
            } catch (Exception e) {
                // Logger may not be available in test environments
                System.out.println("[SmartPillager] TACZ detected — enabling real gun integration");
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            taczDetected = false;
            try {
                SmartPillagerMod.LOGGER.info("[SmartPillager] TACZ not found — using fallback arrow projectiles");
            } catch (Exception ex) {
                // Logger may not be available in test environments
                System.out.println("[SmartPillager] TACZ not found — using fallback arrow projectiles");
            }
        }
        return taczDetected;
    }

    // -----------------------------------------------------------------
    // Resolve a TACZ gun Item from its string ID
    // -----------------------------------------------------------------
    public static Item getGunItem(String gunId) {
        return GUN_ITEM_CACHE.computeIfAbsent(gunId, id -> {
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("tacz", id);
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item != null) return item;
            SmartPillagerMod.LOGGER.warn("[SmartPillager] TACZ gun 'tacz:{}' not found in registry", id);
            return null;
        });
    }

    // -----------------------------------------------------------------
    // Equip a living entity with a TACZ gun in the main hand.
    // Returns true if successful.
    // -----------------------------------------------------------------
    public static boolean equipGun(LivingEntity entity, GunType type) {
        if (!isTaczLoaded()) return false;
        Item gunItem = getGunItem(type.taczId);
        if (gunItem == null) return false;

        ItemStack gunStack = new ItemStack(gunItem);
        entity.setItemInHand(InteractionHand.MAIN_HAND, gunStack);

        try {
            Object operator = mFromLivingEntity.invoke(null, entity);
            mInitialData.invoke(operator);
            SmartPillagerMod.LOGGER.debug("[SmartPillager] Equipped {} with tacz:{}", entity.getStringUUID(), type.taczId);
            return true;
        } catch (Exception e) {
            SmartPillagerMod.LOGGER.warn("[SmartPillager] Failed to initialise TACZ shooter data: {}", e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------
    // Fire the TACZ gun the entity is holding.
    // Returns true if a shot was attempted.
    // -----------------------------------------------------------------
    public static boolean shootGun(net.minecraft.world.entity.Mob entity) {
        if (!isTaczLoaded()) return false;

        ItemStack gunStack = entity.getMainHandItem();
        try {
            Object iGun = mGetIGunOrNull.invoke(null, gunStack);
            if (iGun == null) return false;

            // Check ammo
            boolean useInvAmmo = (Boolean) mUseInventoryAmmo.invoke(iGun, gunStack);
            if (useInvAmmo) {
                boolean hasAmmo = (Boolean) mHasInventoryAmmo.invoke(iGun, entity, gunStack, true);
                if (!hasAmmo) {
                    Object operator = mFromLivingEntity.invoke(null, entity);
                    mReload.invoke(operator);
                    return false;
                }
            } else {
                int dummy = (Integer) mGetDummyAmmo.invoke(iGun, gunStack);
                if (dummy <= 0) {
                    int maxDummy = (Integer) mGetMaxDummyAmmo.invoke(iGun, gunStack);
                    mSetDummyAmmo.invoke(iGun, gunStack, maxDummy);
                    Object operator = mFromLivingEntity.invoke(null, entity);
                    mReload.invoke(operator);
                    return false;
                }
            }

            // Compute pitch/yaw towards target
            net.minecraft.world.entity.Mob mob = (net.minecraft.world.entity.Mob) entity;
            LivingEntity target = mob.getTarget();
            if (target == null) return false;

            double dx = target.getX() - entity.getX();
            double dy = target.getEyePosition().subtract(entity.getEyePosition()).y;
            double dz = target.getZ() - entity.getZ();
            double horizDist = Math.sqrt(dx * dx + dz * dz);

            float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            float pitch = (float) -(Math.atan2(dy, horizDist) * (180.0 / Math.PI));

            // Add slight inaccuracy based on distance
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            float spread = (float) (dist * 0.01);
            yaw += (entity.getRandom().nextFloat() - 0.5F) * spread;
            pitch += (entity.getRandom().nextFloat() - 0.5F) * spread;

            final float finalPitch = pitch;
            final float finalYaw = yaw;

            // Fire via IGunOperator
            Object operator = mFromLivingEntity.invoke(null, entity);
            mShoot.invoke(operator,
                    (java.util.function.DoubleSupplier) () -> finalPitch,
                    (java.util.function.DoubleSupplier) () -> finalYaw);

            return true;
        } catch (Exception e) {
            SmartPillagerMod.LOGGER.debug("[SmartPillager] TACZ shoot failed: {}", e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------
    // Reload the entity's TACZ gun
    // -----------------------------------------------------------------
    public static void reloadGun(LivingEntity entity) {
        if (!isTaczLoaded()) return;
        try {
            Object operator = mFromLivingEntity.invoke(null, entity);
            mReload.invoke(operator);
        } catch (Exception e) {
            SmartPillagerMod.LOGGER.debug("[SmartPillager] TACZ reload failed: {}", e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Check if the entity's gun needs reloading
    // -----------------------------------------------------------------
    public static boolean needsReload(net.minecraft.world.entity.Mob entity) {
        if (!isTaczLoaded()) return false;
        ItemStack gunStack = entity.getMainHandItem();
        try {
            Object iGun = mGetIGunOrNull.invoke(null, gunStack);
            if (iGun == null) return false;

            boolean useInvAmmo = (Boolean) mUseInventoryAmmo.invoke(iGun, gunStack);
            if (useInvAmmo) {
                return !(Boolean) mHasInventoryAmmo.invoke(iGun, entity, gunStack, false);
            }
            return (Integer) mGetDummyAmmo.invoke(iGun, gunStack) <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    // -----------------------------------------------------------------
    // Get remaining ammo count
    // -----------------------------------------------------------------
    public static int getAmmoCount(net.minecraft.world.entity.Mob entity) {
        if (!isTaczLoaded()) return 0;
        ItemStack gunStack = entity.getMainHandItem();
        try {
            Object iGun = mGetIGunOrNull.invoke(null, gunStack);
            if (iGun == null) return 0;

            boolean useInvAmmo = (Boolean) mUseInventoryAmmo.invoke(iGun, gunStack);
            if (useInvAmmo) {
                return (Integer) mGetCurrentAmmoCount.invoke(iGun, gunStack);
            }
            return (Integer) mGetDummyAmmo.invoke(iGun, gunStack);
        } catch (Exception e) {
            return 0;
        }
    }
}
