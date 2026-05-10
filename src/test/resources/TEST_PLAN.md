# Smart Pillager Tactical — Test Plan

## Test Environment Setup

### Without TACZ (Fallback Mode)
1. Place `smartpillager-1.0.0.jar` in `mods/` folder
2. Launch Minecraft 1.20.1 with Forge 47.4.18
3. Verify no errors in `latest.log` related to TACZ classes

### With TACZ (Full Mode)
1. Place `smartpillager-1.0.0.jar` in `mods/`
2. Place `tacz-1.20.1-*.jar` in `mods/` (latest 1.20.1 build)
3. Launch Minecraft 1.20.1 with Forge 47.4.18
4. Check `latest.log` for: `[SmartPillager] TACZ detected — enabling real gun integration`

## Automated Unit Tests

Run with: `./gradlew test`

| Test Class | Tests | What It Covers |
|---|---|---|
| `TaczIntegrationTest` | 14 tests | GunType enum, weighted distribution, TACZ detection, graceful degradation |
| `SmartPillagerEntityTest` | 9 tests | Tactic states, gun type ordinals, NBT round-trip, attribute bounds |
| `TacticalGoalsTest` | 10 tests | AI timing constants, distance params, cover search volume, gun type compatibility |

## Manual In-Game Test Checklist

### Phase 1: Spawn & Gun Assignment
- [ ] `/summon smartpillager:smart_pillager ~ ~ ~` — entity spawns
- [ ] With TACZ: pillager holds a gun item in main hand
- [ ] Without TACZ: pillager has empty hand (uses fallback arrows)
- [ ] Spawn 10+ pillagers — verify mix of gun types in log

### Phase 2: TACZ Gun Firing (requires TACZ)
- [ ] Pillager fires real TACZ gunshots at player
- [ ] Gunshots produce proper TACZ sound effects
- [ ] Gunshots produce proper TACZ muzzle flash particles
- [ ] Pillager pauses to reload when magazine empties
- [ ] Different gun types have different firing patterns

### Phase 3: AI Behavior (works in both modes)
- [ ] Pillager seeks cover behind solid blocks
- [ ] Pillager fires in bursts (suppression)
- [ ] Pillager flanks to player's side
- [ ] Pillager retreats when below 25% HP
- [ ] Pillager strafes while shooting

### Phase 4: Edge Cases
- [ ] No crash when TACZ is removed mid-session
- [ ] Pillagers in superflat world skip cover goal
- [ ] Pillagers persist correctly after save/load
- [ ] 20+ pillagers don't cause TPS drops

## Log Checks

### Expected log entries (with TACZ):
```
[SmartPillager] TACZ detected — enabling real gun integration
[SmartPillager] Equipped <uuid> with tacz:ak47
```

### Expected log entries (without TACZ):
```
[SmartPillager] TACZ not found — using fallback arrow projectiles
```

### Error indicators (should NOT appear):
```
ClassNotFoundException: com.tacz.guns.api.item.IGun
NoClassDefFoundError
NullPointerException in TaczIntegration
```
