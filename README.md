# Smart Pillager Tactical Mod

**Version:** 1.1.0
**Minecraft:** 1.20.1
**Forge:** 47.4.18
**Java:** 17

## Overview

Replaces vanilla Pillager AI with tactical, Division 2-inspired combat behaviors.
Pillagers take cover, lay suppression fire, flank, and retreat when injured.
Fully integrated with TACZ gun mod for realistic gunplay — or works standalone
with fallback arrow projectiles.

## Features

- **5 AI Goals:** Cover, Suppression, Flank, Retreat, Gun Attack
- **5 Gun Types:** Scout (Glock 17), Assault (AK-47), Sniper (AWP), Heavy (M249), Rocket (RPG-7)
- **TACZ Integration:** Real gun physics, sound, and particles via reflection (zero compile-time dep)
- **Fallback Mode:** Works without TACZ using arrow projectiles
- **Configurable:** 12+ config options via smartpillager-common.toml
- **Persistent:** Gun type and tactic state saved to NBT

## Requirements

- Minecraft 1.20.1
- Forge 47.4.18
- Java 17+

## Optional

- TACZ (Timeless and Classics Zero) 1.20.1 — for real gun integration

## Installation

1. Place `smartpillager-1.0.0.jar` in your `mods/` folder
2. (Optional) Place TACZ jar in `mods/` for full gun integration
3. Launch Minecraft with Forge 47.4.18

## Building

```
gradlew.bat build
```

Output: `build/libs/smartpillager-1.0.0.jar`

## Testing

```
gradlew.bat test
```

3 test classes, 33 tests total:
- TaczIntegrationTest (16 tests) — GunType, distribution, TACZ detection
- SmartPillagerEntityTest (10 tests) — Tactic states, NBT, attributes
- TacticalGoalsTest (10 tests) — AI timing, distances, thresholds

## Configuration

Config file: `config/smartpillager-common.toml`

Key options:
- `enableTacticalAI` — master toggle
- `enableCoverSystem` — pillagers seek cover
- `enableSuppression` — burst fire suppression
- `enableFlanking` — flanking maneuvers
- `division2Mode` — enhanced Division 2 behaviors (WIP)
- `spawnWeight` / `minGroupSize` / `maxGroupSize` — spawn control
- `baseHealth` / `baseDamage` — combat stats
- `suppressionRange` / `coverSearchRange` — AI ranges

## Commands

```
/summon smartpillager:smart_pillager ~ ~ ~
/data get entity @e[type=smartpillager:smart_pillager,limit=1] TacticState
/data get entity @e[type=smartpillager:smart_pillager,limit=1] GunType
```

## License

MIT License
