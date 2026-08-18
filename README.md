<div align="center">

# ✦ Ultimate Improvments

**A modular, feature-packed Minecraft plugin for Paper 26.2+ (Java 26)**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPLv3-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-26%2B-orange)](https://www.oracle.com/java/)
[![Paper](https://img.shields.io/badge/Paper-26.2%2B-green)](https://papermc.io/)
[![Version](https://img.shields.io/badge/Version-1.9-brightgreen)](https://github.com/rizer001/UltimateImprovments/releases)

**Author:** [rizer001](https://github.com/rizer001)
**Core:** Paper 26.2+ (or Leaf fork)
**Database:** SQLite
**Build:** Gradle (JDK 26)

</div>

---

<p align="center">
  <a href="GUIDE.md">
    <img src="https://img.shields.io/badge/%F0%9F%93%96%20FULL%20GUIDE-GUIDE.md-blueviolet?style=for-the-badge" alt="Full Guide">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-AGPLv3-blue?style=for-the-badge" alt="License">
  </a>
</p>

---

> **Ultimate Improvments** is a monolithic plugin that turns a vanilla server into a full-featured gameplay experience. It's one `.jar` with everything inside: authentication, an energy network, a fusion reactor, radiation, custom items and enchantments, an achievement tree, turrets, protection systems, and 80+ toggleable modules.

> 📖 **Full documentation:** see [GUIDE.md](GUIDE.md) — commands, items, enchantments, achievements, configuration and more.

---

## ✨ What can you do with it?

### 🛡 Security & Administration
- **Auth** — registration/login via Anvil GUI, Argon2id hashing, GitHub 2FA, sessions
- **Punishments** — ban/mute/kick/warn with temporary and permanent durations, IP/HW scopes, fully customizable MiniMessage screens
- **Whitelist / Blacklist** — custom, database-driven, independent of the vanilla one
- **Anti-cheat** — freeze/check players, packet guard, redstone anti-lag, bot protection
- **`/ui invsee` / `/ui endersee`** — view and edit player inventories **even when they're offline** (reads/writes the `.dat` file with automatic backups)
- **Report system**, sudo mode, command-block tracker, creative item validation

### ⚡ Technology & Machinery
- **Dark Fusion Reactor** — multi-block structure with temperature/pressure/integrity simulation, wear, meltdown
- **Energy network** — cables, batteries, generators, electric furnace, energy workbench
- **Custom crafting** — 20+ craftable items (only craftable in a Crafter, preview in the vanilla workbench)
- **Particle accelerator**, wireless redstone, magnet & lightning structures

### 🗡 Custom Items
| Item | What it does |
|------|--------------|
| Blazing Sword | Sets targets on fire, applies burn damage over time |
| Glass Sword | 1 durability, deals massive burst damage, breaks on hit |
| Electric Trident | Strikes lightning where it hits |
| Photon Cannon | Long-range projectile weapon |
| Electro Shoker | Close-combat projectile weapon |
| Antimatter Flask | Devastating explosion |
| Multimeter, Metal/Ore/Entity Finders | Scanning tools |
| Lead Shield, Dosimeter, Portable Radar | Radiation tools |
| Concrete Bucket, Structure Integrity Indicator | Utility |

### ✨ Custom Enchantments (13)
Attack AoE, Auto Smelt, Vein Miner, TreeCapitator, Flight, Magnet, Igniting, Levitation, Self-Destruct, Degradation, Repairing, Item Stealing, AoE — all with levels up to 255.

### 🏆 Achievements (35+)
A full custom achievement tree in the `ui:` namespace: build the reactor, reach the world height limit, break bedrock, deal 1000 damage with a mace, stay online during a server overload — and a few "meme" ones. Includes **timed challenges** (`/ui advancement start woodcutter|teleport`).

### 🔫 Turrets
End-crystal turrets: configure via Shift+RMB, whitelist/blacklist targets, fires damaging beams with line-of-sight checks.

### 📋 Other Highlights
- Custom chat (per-group/per-world formats, pings), tab, scoreboard (gradients), bossbar, MOTD
- Item integrity (durability) system with anvil repair and XP mending
- Radiation system, homes, notes, spawn, RTP, dimension teleportation
- Power management with countdown bossbar, suicide command, maintenance mode
- Auto-broadcast with conditions, update checker with JAR auto-replace, death logging

---

## 📦 Quick Install

1. **Download** the `.jar` from [Releases](https://github.com/rizer001/UltimateImprovments/releases)
2. **Drop** it into the `plugins/` folder
3. **Restart** the server twice (first run installs the datapack, second run activates it)

> ⚠ Requires **Paper 26.2+** (or a Leaf fork). Not compatible with Spigot/Bukkit.

---

## 🔌 Modular Architecture

Every feature is a **module** that can be toggled on/off at runtime via `/ui modules`. If one module fails, the rest keep running. Essential modules (Core, Database, Auth, Crafting, Energy, Reactor, ...) are always on; the rest are optional.

---

## ⌨️ Commands

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

## 🔑 Permissions

| Permission | Description |
|------------|-------------|
| `ui.admin` / `ui.*` | All permissions |
| `ui.command.<name>` | Access to a specific `/ui <name>` command |
| `ui.chat.filter.bypass` | Bypass chat filter |
| `ui.packetguard.bypass` | Bypass packet size limit |
| `ui.gmprotect.bypass` | Bypass game mode protection |
| `ui.creative.bypass` | Bypass creative item validation |

---

## 🏗️ Building from Source

```bash
git clone https://github.com/rizer001/UltimateImprovments.git
cd UltimateImprovments
./gradlew build
```

The built JAR will be in `Jar/UltimateImprovments-<version>.jar`. Requires JDK 26+.

---

## 📄 License

**GNU AGPL v3** — see [LICENSE](LICENSE). Free use, modification, and distribution allowed.

---

*Build date: 2026-08-18 | Latest version: 1.9*
