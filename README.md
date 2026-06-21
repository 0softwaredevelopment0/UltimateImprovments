<div align="center">

# ✦ MC-Plugin

**A modular Minecraft plugin that adds authentication, advanced mechanics, custom crafting, and server protection.**

[![License](https://img.shields.io/badge/License-AGPLv3-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25%2B-orange)](https://www.oracle.com/java/)
[![Paper](https://img.shields.io/badge/Paper-1.21.4%2B-green)](https://papermc.io/)

</div>

---

## 📦 Quick Install

1. **Download** `.jar` from [Releases](https://github.com/Minecraft337/MC-Plugin/releases)
2. **Drop** into `plugins/` folder
3. **Restart** server twice (first load installs datapack, second activates it)

> ⚠ Requires **Paper 1.21.4+** or fork. Not compatible with Spigot/Bukkit.

---

## 🎯 What It Does

| Category | Features |
|----------|----------|
| 🔐 **Auth** | Mandatory login/register via Anvil GUI, Argon2id hashing, session management, IP check, account limits |
| ⚛ **Reactor** | Multi-block Dark Fusion Reactor — generates energy & Ancient Debris |
| 🔋 **Energy** | Cable network (Waxed Copper blocks) for energy transfer & storage |
| ☢ **Radiation** | Player radiation with levels, effects, dosimeter, lead shield, antirad |
| 🧲 **Magnet** | Multi-block structure that attracts metallic items & armored mobs |
| ⚡ **Lightning** | Multi-block controlled lightning strikes |
| 🔢 **Code Panel** | Interactive code entry with key flags (attempts, time, whitelist, commands) |
| 🔧 **Crafting** | Custom items: Plasma Cannon, Shoker, Multimeter, Health Meter, Entity Locator, Dosimeter, Lead Shield |
| 🔧 **Integrity** | Item durability as 0-100% with repair, combining, mending |
| 🚂 **Minecarts** | Exponential acceleration, collision damage, speed display |
| 🏠 **Homes** | Save/teleport home points (SQLite, configurable limits) |
| 🌍 **Dimensions** | GUI-based world teleportation with return |
| 🔌 **Power** | Server shutdown/restart with BossBar countdown & confirmation |
| 👻 **Vanish** | Full player hiding (persists across restarts) |
| 📝 **Notes** | Personal writable notes per player (54 slots, editable books) |
| 🗣 **Chat Filter** | Profanity filter with wildcard & regex (bypass permission) |
| 🛡 **Protection** | RedstoneGuard, PacketGuard, Void Protection, Entity Limits, Brand Hider |
| 🔄 **Updater** | Auto check & install updates from GitHub Releases |

---

## ⌨ Basic Commands

```
/mp help                  — Command list
/mp reload                — Reload plugin config
/mp modules list          — List all modules
/mp modules enable <name> — Enable a module
/mp modules disable <name>— Disable a module
/mp checkver              — Check for updates
/mp updatejar             — Install update
```

### 🔐 Auth (Admin)
```
/mp auth forcelogin <nick>    /mp auth resetauth <nick>
/mp auth chgpass <nick> <pass>/mp auth delsession <nick>
/mp auth logout
```

### 🌍 World & Teleport
```
/mp chgdim                  — Open world menu
/mp chgdim_teleport <world> — Teleport to world
/mp sethome <name>          — Save home
/mp home <name>             — View home
/mp listhomes               — List homes
```

### ⚙️ Mechanics
```
/mp codepane                      — Code panel
/mp togglespeed                   — Minecart speed display
/mp checkrad [nick]               — Check radiation
/mp setrad <nick> <value>         — Set radiation
/mp vanish <nick>                 — Toggle vanish
/mp notes                         — Open notes
/mp suicide                       — Self-kill
/mp power off|reboot|confirm|undo — Power management
/mp item int list|set|add <value> — Item integrity
```

### 🏗 Structures
```
/mp str dfc assemble       — Assemble reactor
/mp str dfc stats          — Reactor stats
/mp str magnet assemble    — Assemble magnet
/mp str magnet stats       — Magnet stats
/mp str lightning enable|disable|stats
```

---

## 🔑 Essential Permissions

| Permission | Description |
|------------|-------------|
| `mcplugin.admin` or `mcplugin.*` | All permissions |
| `mcplugin.chat.filter.bypass` | Bypass chat filter |
| `mcplugin.packetguard.bypass` | Bypass packet size limit |
| `mcplugin.gmprotect.bypass` | Bypass game mode protection |
| `mcplugin.creative.bypass` | Bypass creative item validation |
| `mcplugin.show.brand` | Show server brand in F3 |

---

## 📄 Configuration

- **`config.yml`** — all settings (auth, reactor, energy, radiation, features, chat filter, homes, etc.)
- **`messages.yml`** — all player-facing messages (MiniMessage format, customizable)

---

## 🗄 Database (SQLite)

Tables: `auth_users`, `auth_sessions`, `cables`, `cable_connections`, `code_panel_keys`, `player_homes`, `player_notes`, `player_radiation`, `updater_state`, `vanished_players`, `magnet_state`, `reactor_state`

---

## 🔄 Updating

1. Delete old datapack in `world/datapacks/`
2. Delete old `config.yml` (regenerated automatically)
3. Replace `.jar`, restart twice

---

## 📖 Detailed Reference

See **[INFO.md](INFO.md)** for the full feature breakdown with all commands, permissions, mechanics, config options, and database structure.

---

## 📄 License

**GNU AGPL v3** — see [LICENSE](LICENSE)

## 👤 Author

**rizer001** — [GitHub](https://github.com/Minecraft337) — Discord: `@error404_user.not.found`
