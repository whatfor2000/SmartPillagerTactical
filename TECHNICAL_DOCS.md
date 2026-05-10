# Smart Pillager Tactical — Technical Documentation

**Version:** 1.1.0
**Minecraft:** 1.20.1
**Forge:** 47.4.18
**Java:** 17

---

## 1. Architecture Overview

The mod replaces vanilla Pillager AI with a tactical decision-making system inspired by The Division 2 enemy behaviors. It consists of:

| Component | Class | Role |
|-----------|-------|------|
| Main Mod | `SmartPillagerMod` | Registration, entity type, config |
| Entity | `SmartPillagerEntity` | Custom Pillager with synched tactical state + gun type |
| AI Goals (5) | `ai/` package | Cover, Suppression, Flank, Retreat, Gun Attack |
| TACZ Integration | `TaczIntegration` | Reflection-based TACZ gun API wrapper (zero compile-time dep) |
| Config | `ModConfig` | ForgeConfigSpec with 12 tunable parameters |
| Spawn | `SpawnHandler` | Registers spawn placement rules |

### Class Hierarchy

```
net.minecraft.world.entity.monster.Pillager
  └── SmartPillagerEntity
        ├── TacticalCoverGoal        (priority 1)
        ├── TacticalSuppressionGoal  (priority 2)
        ├── TacticalFlankGoal        (priority 3)
        ├── TacticalRetreatGoal      (priority 4)
        ├── TacticalGunAttackGoal    (priority 5)
        ├── MeleeAttackGoal          (priority 6)
        ├── WaterAvoidingRandomStrollGoal (priority 7)
        ├── LookAtPlayerGoal         (priority 8)
        └── RandomLookAroundGoal     (priority 9)
```

Vanilla `RangedCrossbowAttackGoal` is explicitly removed at registration time.

### Package Structure

```
com.smartpillager
├── SmartPillagerMod.java          — Main mod class, entity registration
├── ai/
│   ├── TacticalCoverGoal.java     — Find and use cover
│   ├── TacticalSuppressionGoal.java — Burst fire suppression
│   ├── TacticalFlankGoal.java     — Flank the player
│   ├── TacticalRetreatGoal.java   — Retreat when low HP
│   └── TacticalGunAttackGoal.java — Primary ranged attack (TACZ or fallback)
├── config/
│   └── ModConfig.java             — ForgeConfigSpec
├── entity/
│   └── SmartPillagerEntity.java   — Custom Pillager entity
├── event/
│   └── SpawnHandler.java          — Spawn placement registration
└── tacz/
    └── TaczIntegration.java       — Reflection-based TACZ API wrapper
```

---

## 2. Tactical State Machine

The entity cycles through 6 states, re-evaluated every 1–2 seconds (20–40 ticks):

```
  IDLE ──→ ADVANCING ──→ IN_COVER ──→ SUPPRESSING
              ↑              │              │
              │              ↓              ↓
              └──────── FLANKING ←── RETREATING
```

### State Transitions (in `updateTacticalState()`)

| Condition | New State |
|-----------|-----------|
| No target | `IDLE` |
| Health < 25% | `RETREATING` |
| In cover & target < 20 blocks | `SUPPRESSING` |
| Not in cover & cooldown expired | `IN_COVER` |
| Target > 30 blocks away | `ADVANCING` |
| Target 15–30 blocks away | `FLANKING` |
| Target < 15 blocks away | `SUPPRESSING` |

### Synched Entity Data

Four `EntityDataAccessor` fields are synced client↔server:

- `DATA_IN_COVER` (Boolean) — whether the pillager is behind cover
- `DATA_SUPPRESSING` (Boolean) — whether currently laying suppression fire
- `DATA_TACTIC_STATE` (Int) — current tactic enum (0–5)
- `DATA_GUN_TYPE` (Int) — assigned gun type ordinal (0–4)

All four are persisted to NBT via `addAdditionalSaveData` / `readAdditionalSaveData`.

---

## 3. Gun System

### 3.1 Gun Types

Each pillager is assigned a `GunType` on spawn via weighted random distribution:

| Gun Type | TACZ ID | Weight | Role |
|----------|---------|--------|------|
| ASSAULT | `tacz:ak47` | 30% | Rifle, medium range, auto |
| SCOUT | `tacz:glock_17` | 25% | Pistol, fast, close range |
| HEAVY | `tacz:m249` | 20% | LMG, suppression, high ammo |
| SNIPER | `tacz:ai_awp` | 17% | Sniper, long range, bolt-action |
| ROCKET | `tacz:rpg7` | 8% | RPG, rare, devastating |

### 3.2 TACZ Integration (`TaczIntegration.java`)

All TACZ API calls are made through **reflection** — zero compile-time dependency.

**Detection:** `Class.forName("com.tacz.guns.api.item.IGun")` at first call.

**Key reflective operations:**
- `IGun.getIGunOrNull(ItemStack)` — check if held item is a TACZ gun
- `IGunOperator.fromLivingEntity(LivingEntity)` — get operator for entity
- `IGunOperator.initialData()` — initialise shooter data so TACZ recognises the pillager
- `IGunOperator.shoot(DoubleSupplier, DoubleSupplier)` — fire the gun with pitch/yaw
- `IGunOperator.reload()` — trigger reload
- `IGun.useInventoryAmmo()` / `hasInventoryAmmo()` / `getDummyAmmoAmount()` — ammo management

**Gun item resolution:** `ForgeRegistries.ITEMS.getValue(new ResourceLocation("tacz", gunId))` with caching.

**Fallback:** If TACZ is not installed, all attack goals use arrow projectiles. No crashes.

### 3.3 Entity Spawn & Gun Assignment

In `SmartPillagerEntity.finalizeSpawn()`:
1. `GunType.randomWeighted()` picks a gun class
2. `TaczIntegration.equipGun(this, gunType)` places the TACZ item in main hand and calls `initialData()`
3. Gun type is synced via `DATA_GUN_TYPE` and persisted to NBT

---

## 4. AI Goal Details

### 4.1 TacticalCoverGoal (Priority 1)

**Purpose:** Find and move to a solid block that blocks line-of-sight from the target.

**Algorithm:**
1. Search a 8-block radius around the pillager (vertical range: -2 to +3)
2. For each candidate block, check:
   - `isSolidRender()` — must be opaque
   - Air block above — pillager needs headroom
   - At least one horizontal side has air+airAbove — a valid standing spot
   - `ClipContext` raytrace from target eye → block center hits the block (confirms it blocks LOS)
3. Score candidates: `score = distToMob - distToTarget * 0.3`
   - Prefers cover close to the pillager but between pillager and target
4. Navigate to the best cover block

**Timing:**
- `COVER_HOLD_TIME` = 60 ticks (3 seconds) before peeking
- Peek cycle: 20 ticks exposed / 20 ticks hidden
- 5-second cooldown after leaving cover before seeking again

### 4.2 TacticalSuppressionGoal (Priority 2)

**Purpose:** Lay down burst fire with intentional inaccuracy to pin the player.

**Burst Pattern:**
- Fire for 20 ticks (1 second), then pause for 15 ticks (0.75 seconds)
- Up to 5 bursts before the goal ends
- Fires every 4 ticks during a burst (5 "shots"/second)

**TACZ mode:** Calls `TaczIntegration.shootGun()` each shot — fires real TACZ gun with proper ballistics, sound, and particles.

**Fallback mode:** Arrow entity with:
- Damage: 4.0–6.0 (random)
- Speed: 3.0
- Spread: `inaccuracy = 0.15 + distance * 0.01`

**Strafe:** During pause phases, randomly strafes perpendicular to target.

### 4.3 TacticalFlankGoal (Priority 3)

**Purpose:** Move to the side/rear of the target to break their cover.

**Algorithm:**
1. Get target's look direction vector
2. Rotate ±90° (random left/right) to compute flank direction
3. Target point = target position + (flank direction × 15 blocks)
4. Use `LandRandomPos.getPosTowards` to find valid ground near that point
5. Recalculate every 40 ticks (2 seconds)

**Constraints:** Only activates when distance is 10–40 blocks. Max duration: 200 ticks (10 seconds).

### 4.4 TacticalRetreatGoal (Priority 4)

**Purpose:** Flee when low on health, firing backward to discourage pursuit.

**Algorithm:**
1. Compute retreat direction: normalize(mobPos - targetPos)
2. Target point = mobPos + (retreatDir × 20 blocks)
3. Navigate at 1.2× speed
4. Every 30 ticks, fire a backward shot (TACZ or fallback arrow with 30% inaccuracy, 3.0 damage)
5. Recalculate retreat target every 60 ticks if not in cover

**Exit conditions:**
- Reached safe distance (25 blocks) with cover
- Health recovered above 50% and distance > 17.5 blocks
- Max retreat time exceeded (160 ticks / 8 seconds)

### 4.5 TacticalGunAttackGoal (Priority 5)

**Purpose:** Primary ranged attack — aimed fire with magazine/reload mechanics.

**Features:**
- Attack interval: configurable (default 20 ticks = 1 second)
- Attack radius: 30 blocks
- Headshot system: 15% chance for 1.5× damage (fallback mode only)
- Accuracy scales with distance: `accuracy = max(0.05, 0.15 - dist * 0.002)` (fallback mode)

**TACZ mode:**
- Uses `TaczIntegration.shootGun()` which handles ammo checks, auto-reload, and real TACZ ballistics
- No magazine limit — TACZ handles ammo internally
- Reload wait: 40 ticks (2 seconds) when ammo is empty

**Strafe behavior:**
- Too close (< 50% range): back up
- Too far (> 70% range): move closer
- After 20 ticks of line-of-sight: strafe clockwise/counter-clockwise

---

## 5. Entity Stats

| Attribute | Value | Vanilla Pillager |
|-----------|-------|-----------------|
| Max Health | 40 HP | 24 HP |
| Movement Speed | 0.35 | 0.35 |
| Follow Range | 48 blocks | 32 blocks |
| Attack Damage | 6.0 | 5.0 (crossbow) |
| Armor | 4.0 | 0.0 |

All stats are configurable via `smartpillager-common.toml`.

---

## 6. Configuration (`ModConfig`)

Config file: `config/smartpillager-common.toml`

| Category | Key | Default | Range |
|----------|-----|---------|-------|
| General | `enableTacticalAI` | true | bool |
| General | `enableCoverSystem` | true | bool |
| General | `enableSuppression` | true | bool |
| General | `enableFlanking` | true | bool |
| General | `division2Mode` | false | bool |
| Spawn | `spawnWeight` | 50 | 1–100 |
| Spawn | `minGroupSize` | 2 | 1–10 |
| Spawn | `maxGroupSize` | 5 | 1–15 |
| Combat | `baseHealth` | 40.0 | 10–100 |
| Combat | `baseDamage` | 5.0 | 1–50 |
| Combat | `suppressionRange` | 35.0 | 10–64 |
| Combat | `coverSearchRange` | 8.0 | 3–16 |

---

## 7. Spawn System

- Registered via `SpawnPlacementRegisterEvent` with `Operation.REPLACE`
- Placement: `ON_GROUND` at `MOTION_BLOCKING_NO_LEAVES` heightmap
- Uses `Mob.checkMobSpawnRules` + validates ground block with `isValidSpawn`
- Spawns in groups of 2–5 (configurable) with weight 50
- On spawn, `finalizeSpawn()` assigns a random gun type and equips the TACZ gun

---

## 8. Dependencies

| Dependency | Type | Version |
|------------|------|---------|
| Forge | Required | 47.4.18 |
| Minecraft | Required | 1.20.1 |
| TACZ | Optional | ≥1.0.0 |

TACZ is a **soft dependency** — the mod loads and functions without it, using arrow projectiles as fallback. TACZ is detected at runtime via reflection; no compile-time classpath dependency exists.

---

## 9. Build System

- **Build tool:** ForgeGradle 6.x
- **Java:** 17
- **Mappings:** Parchment (configurable)
- **Repositories:** CurseForge, BlameJared, shedaniel, modmaven, architectury, kosmx, Maven Central, local `libs/`
- **Output:** `build/libs/smartpillager-1.0.0.jar`

---

## 10. Known Limitations

1. **No cross-pillager coordination** — each pillager makes independent decisions (no squad AI)
2. **Cover search is O(n³)** — scans up to 17×6×17 = 2,312 blocks; could cause tick lag with many entities
3. **No pathfinding around obstacles** — uses basic `LandRandomPos` which may fail in complex terrain
4. **Division 2 mode flag exists** but doesn't yet modify any behavior
5. **Fallback projectiles use Arrow entities** — visible with vanilla arrow physics (arc, pickupable)
6. **No custom sound effects in fallback mode** — gunshots use silent arrow launches
7. **No particle effects in fallback mode** — no muzzle flash, tracers, or impact particles
8. **TACZ gun IDs are hardcoded** — if TACZ changes its registry names, guns won't resolve (errors logged gracefully)

---

## 11. Recommended Tests

### 11.1 Basic Functionality

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 1 | **Spawn verification** | `/summon smartpillager:smart_pillager ~ ~ ~` | Entity spawns, has 40 HP, moves with tactical AI |
| 2 | **Natural spawn** | Find a pillager outpost at night | Smart pillagers spawn in groups of 2–5 |
| 3 | **Config reload** | Edit `smartpillager-common.toml`, use `/reload` | New values take effect on next spawn |
| 4 | **Gun type assignment** | `/summon smartpillager:smart_pillager ~ ~ ~`, check main hand | Pillager holds a TACZ gun (if TACZ installed) or nothing (fallback) |

### 11.2 Gun System Tests

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 5 | **TACZ gun equip** | With TACZ installed, summon a smart pillager | Pillager holds a TACZ gun item (ak47, glock_17, ai_awp, m249, or rpg7) |
| 6 | **TACZ shooting** | With TACZ installed, let a pillager shoot at you | Real TACZ gunshots with proper sound, recoil, and ballistics |
| 7 | **TACZ reload** | With TACZ installed, observe sustained fire | Pillager pauses to reload when magazine is empty |
| 8 | **Gun type variety** | Spawn 10+ smart pillagers | Mix of different gun types (mostly ASSAULT/SCOUT, some SNIPER/HEAVY, rare ROCKET) |
| 9 | **Fallback without TACZ** | Run mod without TACZ installed | Pillagers use arrow projectiles, no crashes, no errors |

### 11.3 AI Behavior Tests

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 10 | **Cover seeking** | Stand 20+ blocks from a smart pillager in an open field with nearby trees/walls | Pillager moves behind a solid block, pops out periodically to shoot |
| 11 | **Suppression fire** | Engage a pillager at 15–30 blocks range | Pillager fires in bursts (1s on, 0.75s off), projectiles have visible spread |
| 12 | **Flanking** | Stand still while a pillager is 15–30 blocks away | Pillager moves to your side/rear instead of approaching head-on |
| 13 | **Retreat** | Reduce a pillager's health below 25% (use damage commands) | Pillager runs away from you, fires backward occasionally |
| 14 | **Cover + suppression combo** | Let a pillager reach cover, then stand in the open | Pillager hides behind cover, peeks out every ~3 seconds to fire |

### 11.4 Combat Mechanics

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 15 | **Reload timing** | Engage a pillager and observe sustained fire | After magazine empties, pillager pauses for 2 seconds (reload), then resumes |
| 16 | **Headshot damage (fallback)** | Get shot repeatedly in fallback mode, observe damage values | Occasional 1.5× damage hits |
| 17 | **Strafe while shooting** | Stand at medium range, observe pillager movement | Pillager strafes left/right while firing |
| 18 | **Melee fallback** | Get within 2 blocks of a pillager | Pillager switches to melee attack |

### 11.5 Multi-Entity Tests

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 19 | **Group tactics** | Spawn 4–5 smart pillagers, engage from 20 blocks | Some take cover, some flank, some suppress simultaneously |
| 20 | **Mixed with vanilla** | Engage a group of normal pillagers + smart pillagers | Smart pillagers use tactics, vanilla ones use crossbow |
| 21 | **Target switching** | Have two players; smart pillagers should switch targets when one gets close | Pillagers re-evaluate and may switch to closer target |
| 22 | **Mixed gun types in group** | Spawn 5+ smart pillagers with TACZ installed | Group contains different gun types; SNIPERs engage at long range, SCOUTs push close |

### 11.6 Edge Cases

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 23 | **No cover available** | Fight in a flat superflat world with no blocks | Pillagers skip cover goal, go straight to suppression/flanking |
| 24 | **Long distance** | Stand 50+ blocks away | Pillagers advance toward you (ADVANCING state) |
| 25 | **Very close range** | Stand next to a pillager | Pillager backs up or uses melee |
| 26 | **Persistence** | Save and quit, then reload world | Pillagers retain their tactical state and gun type (NBT persistence) |
| 27 | **TACZ removed mid-game** | Play with TACZ, then remove TACZ jar and restart | Mod loads without errors, pillagers use fallback arrows |

### 11.7 Performance Test

| # | Test | Steps | Expected Result |
|---|------|-------|-----------------|
| 28 | **Tick lag check** | Spawn 20+ smart pillagers, monitor FPS and tick time | No significant TPS drop; cover search doesn't cause noticeable lag |

---

## 12. Useful Debug Commands

```
/summon smartpillager:smart_pillager ~ ~ ~
/data get entity @e[type=smartpillager:smart_pillager,limit=1] TacticState
/data get entity @e[type=smartpillager:smart_pillager,limit=1] InCover
/data get entity @e[type=smartpillager:smart_pillager,limit=1] Suppressing
/data get entity @e[type=smartpillager:smart_pillager,limit=1] GunType
/effect give @e[type=smartpillager:smart_pillager] minecraft:weakness 1000000 255 true
```
