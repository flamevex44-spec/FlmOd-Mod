# FlameMod

Client-side Fabric mod for Minecraft **1.21.11**.  
Detects when the local player disables an opponent's shield and automatically follows up with a configurable mace attack.

---

## Features

| Feature | Default |
|---|---|
| Shield-disable detection & follow-up | **ON** |
| Auto mace-slot switch | **ON** |
| Mace hotbar slot | **5** |
| Attack delay | **4 ticks** |

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.11 |
| Fabric Loader | ≥ 0.15.0 (built with 0.16.14) |
| Fabric API | 0.119.5+1.21.4 or a later +1.21.x build |
| Java | 21 |

---

## Build

### First-time setup (fetch Gradle wrapper JAR)

```sh
chmod +x setup-wrapper.sh
./setup-wrapper.sh
```

This downloads `gradle/wrapper/gradle-wrapper.jar` from the official Gradle GitHub release.  
Required only once; the JAR is not committed to this repo.

### Build

```sh
./gradlew build
```

Output JAR: `build/libs/flamemod-<version>.jar`

---

## Install

1. Install [Fabric Loader 0.16.14+](https://fabricmc.net/use/installer/) for Minecraft 1.21.11.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) (0.119.5+1.21.4 or compatible).
3. Copy `flamemod-<version>.jar` into your `.minecraft/mods/` folder.
4. Launch Minecraft with the Fabric profile.

---

## Configuration

In-game: type `/flameconfig` to open the config screen.

Config file: `.minecraft/config/flamemod.json`

```json
{
  "shieldDisableEnabled": true,
  "autoSwitchEnabled": true,
  "maceSlot": 5,
  "attackDelayTicks": 4
}
```

| Field | Type | Range | Description |
|---|---|---|---|
| `shieldDisableEnabled` | boolean | — | Master toggle |
| `autoSwitchEnabled` | boolean | — | Auto-switch to mace slot on trigger |
| `maceSlot` | int | 1–9 | Hotbar slot containing the mace |
| `attackDelayTicks` | int | 3–6 | Ticks between detection and attack |

---

## How Shield-Disable Detection Works

Minecraft 1.21.x does not expose a dedicated "shield disabled" event to the client. FlameMod uses the following inference chain:

1. The local player attacks a `LivingEntity` that is **actively blocking** (`isBlocking() == true`, active item is a shield).
2. FlameMod begins tracking that entity.
3. Within a 6-tick window, if the entity's `isBlocking()` transitions from `true` to `false` (i.e. the server's state update arrives via S2C packets), FlameMod treats this as a successful shield disable.
4. The scheduled mace attack fires after the configured delay, provided all validity checks pass.

### Known Limitations

- **False positives possible:** if the target voluntarily lowers their shield within the detection window, FlameMod may trigger.
- **Server latency:** the state change packet can arrive 1–4 ticks late on high-ping connections.
- **Not a combat guarantee:** the attack is a normal client-side `attackEntity` call. Reach, cooldown, server-side anti-cheat, and network conditions all apply.

---

## Version Pinning Notes

- **Fabric API `0.119.5+1.21.4`** — the `+1.21.4` suffix covers the 1.21.x compatibility range. If a `+1.21.11`-specific artifact is published on [fabricmc.net](https://maven.fabricmc.net/) or [Modrinth](https://modrinth.com/mod/fabric-api) by the time you build, update `fabric_api_version` in `gradle.properties` to that artifact.
- **Yarn mappings `1.21.4+build.8`** — used as the named-symbol source. Compatible with the 1.21.x series. Update to a 1.21.11-targeted Yarn build if one is published.
- **Gradle 8.11.1** — pinned for reproducible builds.

---

## License

MIT
