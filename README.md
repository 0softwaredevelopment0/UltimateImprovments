<div align="center">

# ✦ MC-Plugin

**A powerful modular Minecraft plugin that adds authentication, advanced mechanics, custom crafting, server protection, and much more.**

[![License](https://img.shields.io/badge/License-AGPLv3-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25%2B-orange)](https://www.oracle.com/java/)
[![Paper](https://img.shields.io/badge/Paper-1.21.4%2B-green)](https://papermc.io/)

</div>

---

## 📋 Table of Contents

- [Features Overview](#-features-overview)
- [Installation](#-installation)
- [Configuration](#-configuration)
- [Commands](#-commands)
- [Permissions](#-permissions)
- [Modules](#-modules)
- [Requirements](#-requirements)
- [Updating](#-updating)
- [License](#-license)

---

## 🚀 Features Overview

### 🔐 Authentication System
Mandatory player authentication via Anvil GUI with **Argon2id** password hashing.

| Feature | Description |
|---------|-------------|
| Registration/Login | Anvil GUI with rename field for password entry |
| Password Hashing | Argon2id (32MB memory, 2 iterations) on async thread |
| Session System | Persistent sessions (default: 60 min) with IP check |
| Account Limit | Max accounts per IP (configurable, default: 3) |
| Security | Auto-kick on too many wrong attempts, login timeout |
| Admin Tools | Force login, reset auth, change password, delete sessions |

### ⚛ Dark Fusion Reactor (R.T.S)
Multi-block structure for energy generation and **Ancient Debris** synthesis.

- **Assembly:** `/mp str dfc assemble` — Iron Blocks, Lightning Rods, Copper Blocks, Redstone Blocks + Item Frame
- **Simulation:** Core/case temperature, pressure, integrity (0-100%), wear, recipe progress
- **Modes:** Heating (redstone signal), Cooling, Fuel loading (diamond/gold blocks)
- **Outputs:** Ancient Debris at 100% recipe, energy into cable network (core temp × 0.9 E/tick)
- **States:** Normal → Degradation → Self-destruct → Meltdown
- **Radiation:** Inside reactor chamber (core temp ≥ 1000), pressure releases with particle effects

### 🔋 Energy System (Cable Network)
Waxed Copper-based energy transfer network.

| Block | Function |
|-------|----------|
| `WAXED_LIGHTNING_ROD` | Straight cable (connects along axis) |
| `WAXED_CHISELED_COPPER` | Corner cable (turn) |
| `WAXED_COPPER_GRATE` | Battery (stores energy, no battery→battery) |

- Node types: `CABLE`, `BATTERY`, `GENERATOR`
- SQLite persistence, visual rendering, energy balancing
- Background tasks: CableLoss, CableTick, BatteryDrain, Generator, EnergyBalancer

### ☢ Radiation System
Player radiation with levels, effects, and countermeasures.

| Level | Value | Effects |
|-------|-------|---------|
| ✅ Safe | 0-199 R/h | None |
| ⚠ Mild | 200-399 | Hunger I, Slowness I |
| ⚠ Moderate | 400-799 | Hunger II, Slowness II, Nausea, Weakness |
| 🔴 High | 800-1599 | Hunger III, Slowness III, Nausea II, Weakness II |
| 🔴 Critical | 1600-3199 | Hunger V, Slowness V, Nausea II, Weakness III, Blindness |
| 🆘 Deadly | 3200-6399 | All effects intensified |
| ☠ Lethal | 6400+ | Maximum effect levels |

**Sources:** Ancient Debris, Basalt Deltas, The End, weapon use (Mace/Trident/Elytra), reactor
**Protection:** Lead Shield (hand), Antirad (-100 R/h), Dosimeter (ActionBar display)

### 🧲 Magnet Structure
Multi-block attraction system for metallic items.

- **Assembly:** `/mp str magnet assemble`
- Attracts: metallic items, armored mobs, minecarts
- Auto-collects to structure center with cluster-based physics

### ⚡ Lightning Structure
Controlled lightning strikes via multi-block assembly.

- Toggle on/off via `/mp str lightning`
- Configurable behavior

### 🔢 Code Panel
Interactive code entry system with clickable chat buttons.

| Command | Description |
|---------|-------------|
| `/mp codepane` | Open code entry panel |
| `/mp codepane key add <name> <code> [flags]` | Add key with flags |
| `/mp codepane key list` | List all keys |
| `/mp codepane key remove <name>` | Remove key |
| `/mp codepane key modify <name> <code> [flags]` | Modify key |

**Flags:** `attempts:<N>`, `time:<N>s|m|h|d`, `whitelist:<names>`, `blacklist:<names>`, `command:(<cmd>),(<cmd2>)`

### 🔧 Custom Crafting
Special craftable items with custom recipes.

| Item | Function |
|------|----------|
| **Multimeter** | Inspect block/energy info |
| **Plasma Cannon** | Long-range energy projectile weapon |
| **Shoker** | Close-combat lightning weapon |
| **Antimatter** | Special utility item |
| **Health Meter** | Mob health display |
| **Entity Locator** | Find nearby entities |
| **Dosimeter** | Radiation level display |
| **Lead Shield** | Radiation protection |

### 🔧 Integrity System
Every item has 0-100% integrity that decreases with use and can be repaired.

- **Anvil repair** — restore integrity
- **Item combining** — merge remaining integrity
- **Mending/XP repair** — restore via XP or silk touch
- **XP collection** — restore all items on XP pickup
- **Commands:** `/mp item int list|set|add <value>`

### 🚂 Minecart Speed System
Exponential acceleration on powered rails with collision damage.

- ×N speed per tick on POWERED_RAIL
- Collision damage = speed × 20
- Speed display in ActionBar (`/mp togglespeed`)

### 🏠 Home Points
Per-player home point system with SQLite storage.

| Command | Description |
|---------|-------------|
| `/mp sethome <name>` | Save home point |
| `/mp home <name>` | View home info |
| `/mp delhome <name>` | Delete home |
| `/mp listhomes` | List all homes |
| `/mp ophomels <player>` | [OP] List player's homes |
| `/mp opdelhome <player> <name>` | [OP] Delete player's home |

### 🌍 Dimension Change
World teleportation via Anvil GUI.

- `/mp chgdim` — open world selection
- `/mp chgdim_teleport <world>` — direct teleport
- `/mp chgdim_return` — return to original location
- Cooldown system

### 🔌 Power Management
Server shutdown/restart with BossBar countdown.

| Command | Description |
|---------|-------------|
| `/mp power off` | Request shutdown |
| `/mp power reboot` | Request restart |
| `/mp power confirm` | Confirm request |
| `/mp power undo` | Cancel request |

- **BossBar** with depleting bar
- **ActionBar** with seconds remaining
- **Sound** beeping that accelerates near shutdown

### 👻 Vanish System
Full player hiding with database persistence.

- `/mp vanish <nick>` — vanish/unvanish a player
- Hidden from `/list` and other players
- Persists across server restarts (applies on login)

### 📝 Notes System
Personal per-player notes via GUI.

- `/mp notes` — open notes interface
- 54 slots (numbered 1-54)
- Editable writable books with 5-second save cooldown
- SQLite storage

### 🗣 Chat Filter
Profanity filter with wildcard and regex support.

- Wildcard patterns: `word`, `word*`, `*word`, `*word*`
- Full Java regex support
- Unicode-aware (`\p{L}` for Cyrillic support)
- Bypass permission: `mcplugin.chat.filter.bypass`

### 🛡 Server Protection

| System | Function |
|--------|----------|
| **RedstoneGuard** | Anti-lag redstone limiting |
| **PacketGuard** | Crash packet prevention |
| **Void Protection** | Save players from void |
| **Emergency Entities Kill** | Auto-remove excess entities |
| **Server Overload Warning** | Chat warnings on high MSPT |
| **Brand Hider** | Hide server software from F3 |
| **Plugin Hider** | Conceal plugin from `/plugins` |
| **Mode Protect** | GameMode restriction per world |

### ➕ Additional Mechanics

| Feature | Description |
|---------|-------------|
| **Attributes** | Custom item attribute management |
| **Beacon** | Enhanced beacon effects & range |
| **Block Damage** | Custom block hardness & breaking time |
| **Boosted Cobweb** | Enhanced slowdown |
| **Container Trigger** | Commands on container open |
| **Dragon Egg** | Custom egg behavior |
| **Ender Chest** | Extended interaction |
| **Glass Break** | Realistic breaking effects |
| **Item Kill** | Conditional item removal |
| **Leash** | Leash any entity to mob/fence |
| **Magnet** | Auto-item collection |
| **Saved Hotbar** | Creative item validation |
| **Shield Slowness** | Shield movement penalty |
| **Terracotta Speed** | Speed boost on terracotta |
| **Unbreakable Breaker** | Break bedrock, barriers, etc. |
| **Waypoint** | Teleport point system |

### 🔄 Auto-Updater
Automatic update checking and installation from GitHub Releases.

- SHA commit comparison via GitHub API
- Release gate: only show updates when new release is published
- JAR replacement with automatic backup
- Fallback: saves `.jar.update` in plugins folder

---

## 📦 Installation

1. **Download** the latest `.jar` from [GitHub Releases](https://github.com/Minecraft337/MC-Plugin/releases)
2. **Place** the `.jar` into your server's `plugins/` folder
3. **Restart** the server (or use `/reload` — restart recommended)
4. **Restart again** after first load — the datapack is installed on startup and needs a second restart to activate

> ⚠ **Important:** Always use **Paper** or **Purpur** (or their forks). Spigot/Bukkit are not supported — the plugin requires Paper API features.

---

## ⚙️ Configuration

### config.yml
Main plugin configuration file. Contains all settings organized by section:

- `auth.*` — authentication system settings
- `reactor.*` — fusion reactor parameters
- `energy.*` — cable network settings
- `radiation.*` — radiation system config
- `features.*` — individual feature toggles & configs
- `chat_filter.*` — swear word lists & patterns
- `home.*` — home point limits & settings
- And more...

### messages.yml
All player-facing messages are customizable in MiniMessage format. Supports color gradients, hover events, and click events.

---

## ⌨ Commands

Use `/mp help` for the full command list in-game.

| Category | Command | Description |
|----------|---------|-------------|
| **General** | `/mp help` | Show command list |
| | `/mp reload` | Reload plugin configuration |
| **Auth** | `/mp auth forcelogin <nick>` | Force authorize a player |
| | `/mp auth resetauth <nick>` | Delete player registration |
| | `/mp auth chgpass <nick> <pass>` | Change player's password |
| | `/mp auth delsession <nick>` | Reset player's session |
| | `/mp auth logout` | Log out of your account |
| **Dimensions** | `/mp chgdim` | Open world teleport GUI |
| | `/mp chgdim_teleport <world>` | Teleport to world |
| | `/mp chgdim_return` | Return to origin |
| **Structures** | `/mp structures dfc stats` | Reactor statistics |
| | `/mp structures dfc assemble` | Assemble reactor |
| | `/mp structures magnet assemble` | Assemble magnet |
| | `/mp structures magnet stats` | Magnet statistics |
| | `/mp structures lightning enable` | Enable lightning |
| | `/mp structures lightning disable` | Disable lightning |
| | `/mp structures lightning stats` | Lightning statistics |
| **Code Panel** | `/mp codepane` | Open code entry panel |
| | `/mp codepane key add <name> <code>` | Add code key |
| | `/mp codepane key list` | List code keys |
| | `/mp codepane key remove <name>` | Remove code key |
| | `/mp codepane key modify <name> <code>` | Modify code key |
| **Items** | `/mp item int list` | Item integrity info |
| | `/mp item int set <value>` | Set item integrity |
| | `/mp item int add <value>` | Add item integrity |
| **Homes** | `/mp sethome <name>` | Save home point |
| | `/mp home <name>` | View home info |
| | `/mp delhome <name>` | Delete home |
| | `/mp listhomes` | List your homes |
| | `/mp ophomels <player>` | [OP] List player's homes |
| | `/mp opdelhome <player> <name>` | [OP] Delete player's home |
| **Power** | `/mp power off` | Request shutdown |
| | `/mp power reboot` | Request restart |
| | `/mp power confirm` | Confirm power action |
| | `/mp power undo` | Cancel power action |
| **Other** | `/mp suicide` | Commit suicide |
| | `/mp vanish <nick>` | Toggle player vanish |
| | `/mp notes` | Open notes GUI |
| | `/mp checkrad [nick]` | Check radiation level |
| | `/mp setrad <nick> <value>` | Set player radiation |
| | `/mp togglespeed` | Toggle speed display |
| **Modules** | `/mp modules list` | List all modules |
| | `/mp modules enable <name>` | Enable a module |
| | `/mp modules disable <name>` | Disable a module |
| **Updates** | `/mp checkver` | Check for updates |
| | `/mp updatejar` | Download & install update |

### System Command Overrides

| Command | Redirects to |
|---------|--------------|
| `/stop` | `/mp power off` |
| `/restart` | `/mp power reboot` |
| `/list` | Vanish-aware player list |

---

## 🔑 Permissions

| Permission | Description |
|------------|-------------|
| `mcplugin.admin` or `mcplugin.*` | All permissions |
| `mcplugin.chat.filter.bypass` | Bypass chat filter |
| `mcplugin.command.*` | All `/mp` commands |
| `mcplugin.gmprotect.bypass` | Bypass game mode protection |
| `mcplugin.overload.logs` | Receive overload warnings in chat |
| `mcplugin.packetguard.bypass` | Bypass packet size limit |
| `mcplugin.show.brand` | Show server brand in F3 |
| `mcplugin.creative.bypass` | Bypass creative item validation |

Individual command permissions follow the pattern: `mcplugin.command.<subcommand>`  
(e.g., `mcplugin.command.auth.forcelogin`, `mcplugin.command.codepane.key.add`)

---

## 🧩 Module System

The plugin is fully modular. Each module can be enabled/disabled independently via `/mp modules`.

**Essential modules (always on):**
- `Core` — plugin core (commands, tasks)
- `Database` — SQLite database management
- `Auth` — authentication system
- `Crafting` — custom recipes
- `Cable` — energy network
- `Energy` — energy management
- `Reactor` — fusion reactor

**Optional modules (can be toggled):**
- `UpdateChecker` — automatic update checking
- `VersionCheck` — version compatibility check
- `Datapack` — datapack installation
- `AutoSave` — automatic DB backups (every 5 min)
- `Leash` — enhanced entity leashing
- `RedstoneGuard` — redstone anti-lag
- `PacketGuard` — packet size protection
- `VoidProtection` — void fall prevention
- `ChatFilter` — profanity filter
- `Vanish` — player hiding
- `Notes` — player notes
- `Magnet` — magnet structure
- `MinecartSpeed` — minecart acceleration
- And 20+ feature modules...

---

## 📋 Requirements

- **Java 25+**
- **Paper 1.21.4+** or compatible fork (Purpur, Leaf, etc.)
- **Not compatible** with Spigot/CraftBukkit

---

## 🔄 Updating

1. Delete the automatically generated datapack in `world/datapacks/`
2. Delete the old `config.yml` (the plugin will regenerate it)
3. Replace the `.jar` in `plugins/`
4. Restart the server twice (first load installs datapack, second loads it)

> The updater can also do this automatically via `/mp updatejar`.

---

## 🗄 Database

MC-Plugin uses **SQLite** for all persistent storage.

| Table | Stores |
|-------|--------|
| `auth_users` | Player registrations & passwords |
| `auth_sessions` | Active auth sessions |
| `cables` | Cable network nodes |
| `cable_connections` | Cable connections |
| `code_panel_keys` | Code panel keys & flags |
| `player_homes` | Home points |
| `player_notes` | Player notes |
| `player_radiation` | Radiation levels |
| `updater_state` | Update check state |
| `vanished_players` | Vanish status |
| `magnet_state` | Magnet structure state |
| `reactor_state` | Reactor state |

---

## 📄 License

This project is distributed under the **GNU AGPL v3** license.  
See [LICENSE](LICENSE) for more information.

---

## 👤 Author

**rizer001**

- GitHub: [@Minecraft337](https://github.com/Minecraft337)
- Discord: `@error404_user.not.found`

---

<div align="center">

**MC-Plugin** — Making Minecraft more interesting, one feature at a time. ✨

</div>
