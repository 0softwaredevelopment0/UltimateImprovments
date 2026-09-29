# Ultimate Improvments

**A huge collection of gameplay features for Paper 26.2+ (Java 26) — one core + 20 addon plugins**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPLv3-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-26%2B-orange)](https://www.oracle.com/java/)
[![Server](https://img.shields.io/badge/Paper-26.2%2B-green)](https://papermc.io/)
[![Version](https://img.shields.io/badge/Version-1.8.3--alpha.5-brightgreen)](https://github.com/0softwaredevelopment0/UltimateImprovments/releases)
![Development status](https://img.shields.io/badge/status-Stable-green)

**Author:** [rizer001](https://github.com/rizer001)
**Server:** Paper 26.2+
**Database:** SQLite
**Build:** Gradle (JDK 26)

---

### Organization Docs

[![Full Guide](https://img.shields.io/badge/GUIDE-GUIDE.md-blueviolet)](GUIDE.md)

---

> **Ultimate Improvments** is a big collection of everything a server needs: authentication, an energy network, a fusion reactor, radiation, custom items and enchantments, an achievement tree, turrets, protection systems — and 80+ toggleable modules on top. The collection ships as a **family of 21 plugins** (UI-Core + 20 addons) that work as one system, built on the modern Paper plugin loader.

> **Full documentation:** see [GUIDE.md](GUIDE.md) — commands, items, enchantments, achievements, configuration and more.

---

## What can you do with it?

### Security & Administration
- **Auth** — registration/login via Anvil GUI, Argon2id hashing, GitHub 2FA, sessions
- **Punishments** — ban/mute/kick/warn with temporary and permanent durations, IP/HW scopes, fully customizable MiniMessage screens
- **Whitelist / Blacklist** — custom, database-driven, independent of the vanilla one
- **Anti-cheat** — freeze/check players, packet guard, redstone anti-lag, bot protection
- **`/ui invsee` / `/ui endersee`** — view and edit player inventories **even when they're offline** (reads/writes the `.dat` file with automatic backups)
- **Report system**, sudo mode, command-block tracker, creative item validation

### Technology & Machinery
- **Dark Fusion Reactor** — multi-block structure with temperature/pressure/integrity simulation, wear, meltdown
- **Energy network** — cables, batteries, generators, electric furnace, energy workbench
- **Custom crafting** — 20+ craftable items (only craftable in a Crafter, preview in the vanilla workbench)
- **Particle accelerator**, wireless redstone, magnet & lightning structures

### Custom Items
| Item | What it does |
|------|--------------|
| Blazing Sword | Sets targets on fire, applies burn damage over time |
| Glass Sword | 1 durability, deals massive burst damage, breaks on hit |
| Electric Trident | Strikes lightning where it hits |
| Photon Cannon | Long-range projectile weapon |
| Electro Shoker | Close-combat projectile weapon |
| Antimatter Flask | Devastating explosion |
| Multimeter, Metal/Ore/Entity Finders | Scanning tools |
| Hazmat Suit, Lead Ingot, Dosimeter | Radiation protection and measurement |
| Concrete Bucket, Structure Integrity Indicator | Utility |

### Custom Enchantments (18)
Attack AoE, Auto Smelt, Vein Miner, TreeCapitator, Flight, Levitation, Magnet, Igniting, Lava Walker, Container Stealing, Item Stealing, Repairing, Self-Destruct, Degradation, Blunting, Vulnerability, Disappearance, AoE — mostly up to level 255, with gameplay caps on the strongest ones (e.g. AoE 8, Attack AoE 10, Magnet 16, Lava Walker 16). Curses included.

### Achievements (35+)
A full custom achievement tree in the `ui:` namespace: build the reactor, reach the world height limit, break bedrock, deal 1000 damage with a mace, stay online during a server overload — and a few "meme" ones. Includes **timed challenges** (`/ui advancement start woodcutter|teleport`).

### Turrets
End-crystal turrets: configure via Shift+RMB, whitelist/blacklist targets, fires damaging beams with line-of-sight checks.

### Other Highlights
- Custom chat (per-group/per-world formats, pings), tab, scoreboard (gradients), bossbar, MOTD
- Armor & armor-trim effects (configurable potion effects from materials/trims)
- Omniscanner — admin scanner with whitelists, including entity inventories
- Item integrity (durability) system with anvil repair and XP mending
- Radiation system with hazmat protection, dosimeter and an admin overlay (`/ui radview`), homes, notes, spawn, RTP, dimension teleportation
- Power management with countdown bossbar, suicide command, maintenance mode
- Auto-broadcast with conditions, update checker with JAR auto-replace, death logging

---

## Quick Install

1. **Download** the distribution archive (21 `UI-*-all.jar` files) from [Releases](https://github.com/0softwaredevelopment0/UltimateImprovments/releases)
2. **Drop** all of them into the `plugins/` folder
3. **Restart** the server once — the datapack installs and enables itself before worlds load

> Requires **Paper 26.2+** (or its fork like Purpur/Leaf). Not compatible with Spigot/Bukkit.

---

## Architecture

The collection is a **family of 21 plugins**: `UI-Core` is the mandatory heart (database, config routing, the `/ui` command tree, shared systems), and the 20 `UI-*` addons each own a feature domain (enchantments, auth, protection, display, admin, player, guard, world, items, energy, ...). Everything runs on the Paper plugin loader (`paper-plugin.yml`, isolated classloaders, `join-classpath` dependency graph), and cross-addon calls go through a `CoreHooks` indirection layer.

On top of that, every feature is a **module** that can be toggled on/off at runtime via `/ui modules`. If one module fails, the rest keep running. Essential modules (Core, Database, Auth, Crafting, Energy, Reactor, ...) are always on; the rest are optional.

### Addons

Each addon is a regular Paper plugin discovered through the family's **AddonCatalog**; `/ui addons` shows every addon with its status (permission `ui.command.addons`, included in `ui.admin` / `ui.*`):

```
/ui addons
```

---

## Commands

All commands start with `/ui`. The full list is in the in-game help (`/ui help`, paginated) and in [GUIDE.md](GUIDE.md). A few examples:

```
/ui help                  — command list (paginated)
/ui modules               — toggle modules
/ui punish <nick> ban ... — punishments
/ui invsee <nick>         — offline inventory editing
/ui turret                — turret configuration
/ui advancement start     — start a timed challenge
/ui str dfc assemble      — assemble the reactor
/ui power off|reboot      — server power management
```

---

## Permissions

| Permission | Description |
|------------|-------------|
| `ui.admin` / `ui.*` | All permissions |
| `ui.command.<name>` | Access to a specific `/ui <name>` command |
| `ui.command.radview` | Admin radiation overlay (default FALSE) |
| `ui.command.configregen` / `ui.command.configreset` | Config regen/reset (default FALSE, extra-gated) |
| `ui.enchant.itemstealing.steal` | Allows Item Stealing to trigger (default FALSE) |
| `ui.chat.filter.bypass` | Bypass chat filter |
| `ui.packetguard.bypass` | Bypass packet size limit |
| `ui.gmprotect.bypass` | Bypass game mode protection |
| `ui.creative.bypass` | Bypass creative item validation |

---

## Building from Source

```bash
git clone https://github.com/0softwaredevelopment0/UltimateImprovments.git
cd UltimateImprovments
./gradlew distributeJars   # fast: shadow-only, all 21 jars into build/libs/
```

The built JARs will be in `build/libs/UI-<part>-<version>-all.jar`. Requires JDK 26+.

---

## License

**GNU AGPL v3** — see [LICENSE](LICENSE). Free use, modification, and distribution allowed.

---
