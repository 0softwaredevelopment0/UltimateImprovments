<div align="center">

# ✦ Ultimate Improvments

**A modular Minecraft plugin for Paper 1.21.4+ (Java 26)**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPLv3-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-26%2B-orange)](https://www.oracle.com/java/)
[![Paper](https://img.shields.io/badge/Paper-26.2%2B-green)](https://papermc.io/)
[![Version](https://img.shields.io/badge/Version-1.9-brightgreen)](https://github.com/rizer001/UltimateImprovments/releases)

**Author:** [rizer001](https://github.com/rizer001)  
**Core:** Leaf (Paper 26.2+) / Paper  
**Database:** SQLite  
**Build:** Gradle (JDK 26)

</div>

---

## 📦 Quick Install

1. **Download** the `.jar` from [Releases](https://github.com/rizer001/UltimateImprovments/releases)
2. **Drop** it into the `plugins/` folder
3. **Restart** the server twice (first run installs the datapack, second run activates it)

> ⚠ Requires **Paper 26.2+** (or a Leaf fork). Not compatible with Spigot/Bukkit.

---

## 🔌 Modular Architecture

The plugin is built on a **modular architecture** — each module can be toggled on/off via `/ui modules`. If one module fails, the rest keep running.

### 📌 Essential Modules (always on)

| Module | Description |
|--------|-------------|
| `Core` | Plugin core: commands, tasks, general listeners |
| `Database` | SQLite database management |
| `Auth` | Player authentication (Anvil GUI, Argon2id) |
| `Crafting` | Custom item recipes |
| `Cable` | Cable network (energy transfer) |
| `Energy` | Energy management: generator, battery, balancer |
| `Reactor` | Dark Fusion Reactor |
| `Power` | Server shutdown/restart system |
| `Radiation` | Player radiation system |
| `Tasks` | Background tasks |

### 🧩 Optional Modules (can be toggled)

| Module | Description |
|--------|-------------|
| `Datapack` | Datapack installation |
| `RedstoneGuard` | Redstone anti-lag |
| `PacketGuard` | Crash packet protection |
| `VoidProtection` | Void fall prevention |
| `ChatFilter` | Profanity filter (wildcard + regex) |
| `UpdateChecker` | Automatic update checking |
| `VersionCheck` | Version compatibility check |
| `AutoSave` | Auto-save to DB (every 5 min) |
| `Vanish` | Player hiding system |
| `Notes` | Player notes system |
| `Magnet` | Magnet structure |
| `MinecartSpeed` | Minecart acceleration |
| `Lightning` | Lightning structure |
| `Integrity` | Item integrity (durability) system |
| `Antimatter` | Antimatter item |
| `Attributes` | Custom item attributes |
| `Beacon` | Enhanced beacon effects |
| `BlockDmg` | Custom block hardness |
| `BoostedCobweb` | Enhanced cobweb |
| `ContainerTrigger` | Container open triggers |
| `DeathBell` | Custom death bell |
| `DragonEgg` | Custom dragon egg behavior |
| `EnderChest` | Extended ender chest |
| `EntityLocator` | Entity finding item |
| `GlassBreak` | Realistic glass breaking |
| `HealthMeter` | Mob health display |
| `Leash` | Enhanced entity leashing |
| `ModeProtect` | GameMode protection per world |
| `ShieldSlowness` | Shield movement penalty |
| `TerracotaSpeed` | Speed boost on terracotta |
| `UnbreakableBreaker` | Break unbreakable blocks |
| `Waypoint` | Teleport point system |
| `CreativeItemValidator` | Creative item validation |
| `WirelessRedstone` | Wireless redstone |
| `ElytraBoost` | Elytra acceleration |
| `Furnace` | Electric furnace |
| `GeneratorBasic` | Basic energy generator |
| `Battery` | Energy battery |
| `BatteryMulti` | Multi-battery |
| `Chat` | Custom chat |
| `Tab` | Custom tab list |
| `Scoreboard` | Custom scoreboard |
| `BossBar` | Custom boss bar |
| `BelowName` | Below-name display |
| `MOTD` | Custom MOTD |
| `Economy` | Economy (Vault) |
| `Punish` | Punishment system |
| `BotProtection` | Bot protection |
| `Light` | Light management |
| `ProxyServer` | Proxy server |
| `Assembler` | Item assembler |
| `Omniscanner` | Block/item/entity scanning |
| `AntiCheat` | Anti-cheat (player freeze) |
| `StructureIntegrity` | Structure integrity |
| `AOEEnchantment` | AoE enchantment (area damage) |
| `Particle` | Particle accelerator |
| `Meteor` | Meteor shower |

---

## 🎯 Key Features

### 🔐 Authentication (Auth)
- Registration/login via Anvil GUI
- **Argon2id** password hashing (32MB memory, 2 iterations)
- **Telegram 2FA** via `@OakworldSRVbot` (9-digit code, confirmation buttons)
- IP check, account limit per IP (3 by default)
- Sessions (60 min), login timeout (60 sec), max attempts (5)
- Password change, force login, registration reset
- Password hiding from console logs

### ⚛ Dark Fusion Reactor (R.T.S)
- **Multi-block structure** — Iron Blocks, Lightning Rods, Copper Blocks, Redstone Blocks, Item Frame
- Simulation: core/case temperature, pressure, shell/case integrity (0-100%)
- **Wear system** — reactor degrades over time
- **Modes:** heating/cooling (via redstone), fuel (diamond/gold blocks)
- **Recipe progress** — crafts Ancient Debris
- **States:** ✅ Normal, ⚠ Degradation, 💀 Self-destruct, 💥 Meltdown
- **Radiation** from reactor (at temp ≥ 1000, during pressure release)

### 🔋 Energy System (Cable Network)
- **Cables** — Waxed Lightning Rod (straight), Waxed Chiseled Copper (corner)
- **Batteries** — Waxed Copper Grate (storage)
- Node types: `CABLE`, `BATTERY`, `GENERATOR`
- Background tasks: energy loss (20s), cable tick (5s), battery drain, balancing
- SQLite persistence
- Connection visualization (CableVisualTask)

### ☢ Radiation
- **Levels:** Safe → Mild → Moderate → High → Critical → Deadly → ☠ Lethal
- **Sources:** Ancient Debris in inventory, Basalt Deltas, The End, weapons (Mace/Trident/Elytra), reactor
- **Protection:** Lead Shield, Antirad (removes 100 R/h)
- **Dosimeter** — shows radiation in ActionBar

### 🧲 Magnet
- Multi-block structure that attracts metallic items
- Radius scales with structure power
- Cluster-based attraction system

### ⚡ Lightning
- Multi-block structure for controlled lightning strikes
- Toggle via `/ui str lightning`

### 🔢 Code Panel
- Interactive chat-based code entry with clickable buttons
- **Key flags:** attempts, time, whitelist, blacklist, commands on success
- SQLite storage (`code_panel_keys`)

### 🔨 Custom Crafting

| Item | Description |
|------|-------------|
| **Multimeter** | Inspect block/energy information |
| **Plasma Cannon** | Long-range weapon |
| **Shoker** | Close-combat weapon |
| **Antimatter** | Special explosive item |
| **Health Meter** | Mob health display |
| **Entity Locator** | Find nearby entities |
| **Dosimeter** | Shows radiation in ActionBar |
| **Lead Shield** | Radiation protection |

The datapack also adds vanilla recipes (books, chains, echo shards, totems, spawners, netherite, etc.).

### 🔧 Integrity System
- Every item: integrity 0-100%
- Anvil repair, item combining, XP mending
- Mending enchantment restores integrity
- Silk Touch restores on harvest
- Color gradient in item lore

### 🔋 Totem Charge System (New in 1.9)
- **Charged totems** — charge via anvil (netherite scrap)
- `totem_charge` in PDC (int)
- Lore display: `Charge: X/X`
- If charge > 0 — totem saves life and consumes 1 charge
- If charge = 0 — behaves as a normal totem (consumed)

### 🧲 Particle Accelerator (New in 1.9)
- Particle acceleration with configurable parameters
- Integration with block physics and structures

### 🧊 Block Friction (New in 1.9)
- Custom block friction via `PlayerMoveEvent`
- Velocity mode — modifies player speed, not walk speed
- Values inherited from vanilla slipperiness

### 🔍 Omniscanner (New in 1.9)
- Administrative scanning of blocks, items, and entities
- Scan types: blocks, items (on ground), entities (mobs, players), everything
- **Async** chunk scanning via `ChunkSnapshot` — no server freeze
- GUI with results, sorted by distance
- PDC item protection in GUI

### 🛠️ Admin Menu (New in 1.9)
- `/ui menu` — GUI for plugin management
- Statistics, info, quick item access
- PDC protection on all items

### 🛡️ Anti-Cheat (New in 1.9)
- `/ui check <player>` — freeze player for inspection
- `/ui uncheck <player>` — unfreeze

### 🧩 LuckPerms Integration (New in 1.9)
- Blocks wildcard (`*`) — requires confirmation (re-type within 15s)
- Logs all wildcard grant attempts

### 📊 Scoreboard System (New in 1.9)
- **Gradients** via Team prefix/suffix (`<gradient>`, `<rainbow>`, `<#FF00FF>`)
- **No 40-char limit** — any line length supported
- **Hidden red numbers** via `NumberFormat.blankFormat()`
- **Placeholders:** `%server_time%`, `%server_date%`, `%player_world%`, `%player_coords%`
- **PAPI integration** — if PlaceholderAPI is installed, all placeholders go through PAPI

### 🛡 Server Protection

| System | Description |
|--------|-------------|
| **RedstoneGuard** | Redstone update rate limiter |
| **PacketGuard** | Crash packet protection (kick on oversized packets) |
| **Void Protection** | Save players from falling into the void |
| **Emergency Entities Kill** | Remove excessive entities |
| **Server Overload Warning** | Warning when server is under high load |
| **Brand Hider** | Hide server brand in F3 |
| **Op Command Blocker** | Block dangerous commands |
| **Whitelist Command Blocker** | Protect whitelist commands |

### 🚂 Minecart Speed
- Exponential acceleration on `POWERED_RAIL`
- Collision damage = speed × 20
- Speed display in ActionBar (`/ui togglespeed`)
- Movement particles

### 🏠 Homes
- Save/teleport to home points
- Configurable home limit (default: 10)
- SQLite (`player_homes`)

### 🌍 Dimension Change
- GUI-based world teleportation
- `/ui chgdim` — world selection menu
- Return to original location

### 👻 Vanish
- Full player invisibility
- Hidden from `/list`
- Persists across restarts (stored in DB)

### 📝 Notes
- Personal writable notes via GUI
- 54 slots, editable books
- SQLite (`player_notes`)

### 🗣 Chat Filter
- Wildcard and regex patterns
- Unicode-aware (Cyrillic via `\p{L}`)
- Highlighted profanity in logs (red)
- Bypass permission: `ui.chat.filter.bypass`

### 🔄 Updater
- Auto-check updates via GitHub API
- SHA commit comparison
- Release gate: new commits without release → no update shown
- JAR download from GitHub Releases
- JAR replacement with backup

### 🔌 Power Management
- `/ui power off|reboot|confirm|undo`
- **BossBar** with depleting bar
- **ActionBar** with seconds remaining
- **Sound** — accelerates towards the end
- Intercepts `/stop` and `/restart`

### ☠ Suicide
- Two-step confirmation + countdown
- BossBar + ActionBar + sound

### 📦 Structure Integrity
- Structure blocks get PDC `integrity` tag
- Connectivity checked on destruction
- `/ui str` command
- **Unbreakable Integrity Tag** — structure blocks can't be broken while structure is intact

---

## ⌨️ Commands

### General
```
/ui help                  — Command list
/ui reload                — Reload plugin
/ui modules list          — List all modules
/ui modules enable <name> — Enable a module
/ui modules disable <name>— Disable a module
```

### Auth (Admin)
```
/ui auth forcelogin <nick>
/ui auth resetauth <nick>
/ui auth chgpass <nick> <pass>
/ui auth delsession <nick>
```

### Worlds & Teleport
```
/ui chgdim                       — World menu
/ui chgdim_teleport <world>      — Teleport to world
/ui sethome <name>               — Save home
/ui home <name>                  — View home
/ui listhomes                    — List homes
/ui delhome <name>               — Delete home
/ui spawn                        — Go to spawn
/ui setspawn                     — Set spawn
```

### Mechanics
```
/ui codepane                          — Code panel
/ui codepane key add/remove/list/list — Key management
/ui togglespeed                       — Speed display toggle
/ui checkrad [nick]                   — Check radiation
/ui setrad <nick> <value>             — Set radiation
/ui vanish <nick>                     — Toggle vanish
/ui notes                             — Open notes
/ui suicide                           — Suicide
/ui power off|reboot|confirm|undo     — Power management
/ui item int list|set|add             — Item integrity
/ui check <player>                    — Freeze (anti-cheat)
/ui uncheck <player>                  — Unfreeze
/ui togglebind                        — Wireless redstone toggle
```

### Structures
```
/ui str dfc assemble       — Assemble reactor
/ui str dfc stats          — Reactor stats
/ui str magnet assemble    — Assemble magnet
/ui str magnet stats       — Magnet stats
/ui str lightning enable|disable|stats — Lightning
```

### Updates
```
/ui checkver              — Check for updates
/ui updatejar             — Download and install update
```

### System (vanilla command overrides)
```
/stop          → /ui power off
/restart       → /ui power reboot
/list          — Custom player list (vanish-aware)
/reactor       — Reactor assembly
```

---

## 🔑 Permissions

| Permission | Description |
|------------|-------------|
| `ui.admin` / `ui.*` | All permissions |
| `ui.chat.filter.bypass` | Bypass chat filter |
| `ui.packetguard.bypass` | Bypass packet size limit |
| `ui.gmprotect.bypass` | Bypass game mode protection |
| `ui.creative.bypass` | Bypass creative item validation |
| `ui.show.brand` | Show server brand in F3 |

---

## 📄 Configuration

- **`config.yml`** — all settings (~6000 lines): auth, reactor, energy, radiation, features, chat filter, homes, protection, etc.
  - **Auto-repair** — on InvalidConfigurationException, the plugin automatically restores config.yml
  - **Built-in guide** — the beginning of config.yml contains full documentation for every key
- **`messages.yml`** — all player-facing messages (MiniMessage format, customizable)

---

## 🗄 Database (SQLite)

| Table | Purpose |
|-------|---------|
| `auth_users` | Authentication users |
| `auth_sessions` | Authentication sessions |
| `cables` | Cable network nodes |
| `cable_connections` | Cable connections |
| `code_panel_keys` | Code panel keys |
| `player_homes` | Home points |
| `player_notes` | Player notes |
| `player_radiation` | Player radiation |
| `updater_state` | Update state |
| `vanished_players` | Vanish status |
| `magnet_state` | Magnet state |
| `reactor_state` | Reactor state |
| `player_settings` | Player settings |

---

## 🏗️ Building from Source

```bash
git clone https://github.com/rizer001/UltimateImprovments.git
cd UltimateImprovments
./gradlew build
```

The built JAR will be in `Jar/UltimateImprovments-<version>.jar`

**Requirements:**
- JDK 26+
- Git

---

## 🔄 Updating

1. Delete the old datapack in `world/datapacks/`
2. Replace the `.jar` with the new one
3. Run `/ui reload`
4. If needed, update `config.yml` — auto-repair will add new sections

> ⚠ If using placeholders in configs: replace `{...}` with `%...%` (the old format is no longer supported).

---

## 🧪 For Developers

### PlaceholderAPI
All plugin placeholders register through PAPI (if installed):
- `%ui_player_name%`, `%ui_player_world%`, `%ui_player_coords%`
- `%ui_server_time%`, `%ui_server_date%`
- `%ui_player_ping%`, `%ui_online%`, `%ui_online_max%`

Also available via internal fallback resolver without PAPI.

### API / Soft-depend
- **PlaceholderAPI** — placeholders
- **LuckPerms** — wildcard blocking
- **Vault** — economy
- **DeluxeMenus** — custom menus

### Project Structure
```
src/main/java/com/ultimateimprovments/
├── chat/           — Chat and pings
├── combat/         — Weapons (Plasma Cannon, Shoker)
├── command/        — Command system (/ui)
├── config/         — Configs, auto-repair, guide
├── core/           — Core: Main, scanners, startup
├── database/       — SQLite, auto-save
├── display/        — Tab, scoreboard, boss bar
├── economy/        — Economy (Vault)
├── enchantment/    — AoE enchantment
├── energy/         — Energy system
├── hook/           — Integrations (PAPI, Vault)
├── listener/       — Event listeners
├── maintenance/    — Maintenance mode
├── mechanics/      — All mechanics
│   ├── crafting/   — Custom crafting
│   ├── environment/— Environment (lightning, light)
│   ├── features/   — All features (integrity, omniscanner, etc.)
│   ├── particle/   — Particle accelerator
│   ├── protection/ — Protection
│   └── security/   — Security (auth, anti-cheat, code panel)
├── module/         — Module manager (~80 modules)
├── punish/         — Punishment system
├── report/         — Report system
├── server/         — Server utilities (RedstoneGuard, etc.)
├── structure/      — Structures (markers, chunks)
├── util/           — Utilities (logging, messaging, blocks)
└── whitelist/      — Whitelist, blacklist
```

---

## 📜 Version History

| Version | Date | Key Changes |
|---------|------|-------------|
| **1.9** | 2026-07-18 | Java 26, Scoreboard gradients, Omniscanner, 2FA, Totem Charge, Particle Accelerator, Block Friction, Anti-Cheat, Structure Integrity, LuckPerms integration, PAPI, ~112 commits |
| **1.8** | — | Energy system, reactor, radiation, code panel, modular architecture |
| **1.7** | — | Custom crafting, protection, economy |

Full changelog: [CHANGELOG.md](CHANGELOG.md)

---

## 📄 License

**GNU AGPL v3** — see [LICENSE](LICENSE)

Free use, modification, and distribution allowed. When used on public servers, you must provide access to the source code of modified versions.

---

## 👤 Author

**rizer001** — [GitHub](https://github.com/rizer001) — Discord: `@error404_user.not.found`

---

*Build date: 2026-07-18 | Latest version: 1.9*
